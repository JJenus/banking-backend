package com.jjenus.banking.notifications.service;

import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.identity.IdentityQueryApi;
import com.jjenus.banking.shared.config.BankingProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Email dispatch service.
 *
 * <p>All methods are {@code @Async} — email sending never blocks the calling
 * thread (the event listener), so notification failures cannot cascade into
 * the banking transaction.
 *
 * <p>Emails are rendered from Thymeleaf HTML templates located at
 * {@code src/main/resources/templates/email/}.
 *
 * <p>The {@link JavaMailSender} is configured to point at the self-hosted
 * Postal SMTP server via {@code spring.mail.*} properties. No external
 * mail service is used.
 *
 * <p><b>Recipient resolution:</b> events carry an {@code accountId}, not an
 * email address. This service resolves the recipient through the public
 * cross-module APIs: {@link AccountQueryApi#getOwnerId(String)} maps the
 * account to its Keycloak owner ID, then {@link IdentityQueryApi#getEmailByUserId(String)}
 * maps that owner ID to the registered email. Both calls go through the
 * modules' published interfaces — no repository is accessed directly across
 * a module boundary.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final BankingProperties properties;
    private final AccountQueryApi accountQueryApi;
    private final IdentityQueryApi identityQueryApi;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        BankingProperties properties,
                        AccountQueryApi accountQueryApi,
                        IdentityQueryApi identityQueryApi) {
        this.mailSender      = mailSender;
        this.templateEngine  = templateEngine;
        this.properties      = properties;
        this.accountQueryApi = accountQueryApi;
        this.identityQueryApi = identityQueryApi;
    }

    @Async
    public void sendAccountOpenedEmail(String accountId, String ownerName) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Your account is now open",
            "email/account-opened",
            Map.of("ownerName", ownerName, "accountId", accountId)
        );
    }

    @Async
    public void sendDepositConfirmationEmail(String accountId, String amount, String reference) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Deposit confirmed — " + amount,
            "email/deposit-confirmation",
            Map.of("amount", amount, "reference", reference, "accountId", accountId)
        );
    }

    @Async
    public void sendWithdrawalConfirmationEmail(String accountId, String amount, String reference) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Withdrawal confirmed — " + amount,
            "email/withdrawal-confirmation",
            Map.of("amount", amount, "reference", reference, "accountId", accountId)
        );
    }

    @Async
    public void sendTransferCompletedEmail(String fromAccountId, String toAccountId,
                                           String amount, String transferId) {
        // Notify sender
        sendTemplatedEmailToAccountOwner(
            fromAccountId,
            "Transfer of " + amount + " sent successfully",
            "email/transfer-sent",
            Map.of("amount", amount, "toAccountId", toAccountId, "transferId", transferId)
        );
        // Notify receiver
        sendTemplatedEmailToAccountOwner(
            toAccountId,
            "You have received " + amount,
            "email/transfer-received",
            Map.of("amount", amount, "fromAccountId", fromAccountId, "transferId", transferId)
        );
    }

    @Async
    public void sendTransferReversedEmail(String fromAccountId, String toAccountId,
                                          String amount, String reason) {
        sendTemplatedEmailToAccountOwner(
            fromAccountId,
            "Transfer of " + amount + " has been reversed",
            "email/transfer-reversed",
            Map.of("amount", amount, "reason", reason, "toAccountId", toAccountId)
        );
    }

    @Async
    public void sendTransferFailedEmail(String transferId, String reason) {
        // Transfer failures don't carry a reliable account ID at this layer
        // (the failure can occur before either account is resolved). This is
        // routed to the support mailbox rather than a customer inbox.
        sendTemplatedEmail(
            properties.notifications().supportEmail(),
            "Transfer failed — " + transferId,
            "email/transfer-failed-ops",
            Map.of("transferId", transferId, "reason", reason)
        );
    }

    @Async
    public void sendAccountRestrictedEmail(String accountId, String reason) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Important: Your account has been restricted",
            "email/account-restricted",
            Map.of("accountId", accountId, "reason", reason,
                   "supportEmail", properties.notifications().supportEmail())
        );
    }

    @Async
    public void sendAccountActivatedEmail(String accountId) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Your account is active again",
            "email/account-activated",
            Map.of("accountId", accountId)
        );
    }

    @Async
    public void sendAccountClosedEmail(String accountId, String reason) {
        sendTemplatedEmailToAccountOwner(
            accountId,
            "Your account has been closed",
            "email/account-closed",
            Map.of("accountId", accountId, "reason", reason)
        );
    }

    @Async
    public void sendKycSubmittedEmail(String userId) {
        sendTemplatedEmailToUser(
            userId,
            "We've received your verification documents",
            "email/kyc-submitted",
            Map.of("userId", userId)
        );
    }

    @Async
    public void sendKycApprovedEmail(String userId) {
        sendTemplatedEmailToUser(
            userId,
            "You're verified — full account access unlocked",
            "email/kyc-approved",
            Map.of("userId", userId)
        );
    }

    @Async
    public void sendKycRejectedEmail(String userId, String reason) {
        sendTemplatedEmailToUser(
            userId,
            "Action needed: verification documents could not be approved",
            "email/kyc-rejected",
            Map.of("userId", userId, "reason", reason)
        );
    }

    // ── Core send logic ───────────────────────────────────────────────────

    /**
     * Resolves the recipient by tracing accountId → ownerId (via
     * {@link AccountQueryApi}) → email (via {@link IdentityQueryApi}), then sends.
     *
     * <p>If either lookup fails (account closed and purged, or no profile
     * registered yet), the email is skipped and a warning is logged rather
     * than thrown — a missing notification recipient must never fail or roll
     * back the originating banking transaction, which has already committed
     * by the time this listener runs.
     */
    private void sendTemplatedEmailToAccountOwner(String accountId, String subject,
                                                   String templateName, Map<String, Object> variables) {
        Optional<String> ownerId = accountQueryApi.getOwnerId(accountId);
        if (ownerId.isEmpty()) {
            log.warn("Cannot send notification — no owner found for account {}", accountId);
            return;
        }
        sendTemplatedEmailToUser(ownerId.get(), subject, templateName, variables);
    }

    private void sendTemplatedEmailToUser(String userId, String subject,
                                          String templateName, Map<String, Object> variables) {
        Optional<String> email = identityQueryApi.getEmailByUserId(userId);
        if (email.isEmpty()) {
            log.warn("Cannot send notification — no email on file for user {}", userId);
            return;
        }
        sendTemplatedEmail(email.get(), subject, templateName, variables);
    }

    private void sendTemplatedEmail(String to, String subject, String templateName,
                                    Map<String, Object> variables) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariables(variables);
            ctx.setVariable("fromName", properties.notifications().fromName());
            ctx.setVariable("supportEmail", properties.notifications().supportEmail());

            String htmlBody = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.notifications().fromName()
                + " <" + properties.notifications().supportEmail() + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.debug("Email sent to {} — {}", to, subject);

        } catch (MessagingException e) {
            log.error("Failed to send email to {} — {}: {}", to, subject, e.getMessage(), e);
            // Do not rethrow — email failure must not propagate to the caller
        }
    }
}
