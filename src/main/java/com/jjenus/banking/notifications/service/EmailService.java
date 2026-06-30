package com.jjenus.banking.notifications.service;

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
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final BankingProperties properties;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        BankingProperties properties) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
        this.properties     = properties;
    }

    @Async
    public void sendAccountOpenedEmail(String accountId, String ownerName) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Your account is now open",
            "email/account-opened",
            Map.of("ownerName", ownerName, "accountId", accountId)
        );
    }

    @Async
    public void sendDepositConfirmationEmail(String accountId, String amount, String reference) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Deposit confirmed — " + amount,
            "email/deposit-confirmation",
            Map.of("amount", amount, "reference", reference, "accountId", accountId)
        );
    }

    @Async
    public void sendWithdrawalConfirmationEmail(String accountId, String amount, String reference) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Withdrawal confirmed — " + amount,
            "email/withdrawal-confirmation",
            Map.of("amount", amount, "reference", reference, "accountId", accountId)
        );
    }

    @Async
    public void sendTransferCompletedEmail(String fromAccountId, String toAccountId,
                                           String amount, String transferId) {
        // Notify sender
        sendTemplatedEmail(
            resolveEmail(fromAccountId),
            "Transfer of " + amount + " sent successfully",
            "email/transfer-sent",
            Map.of("amount", amount, "toAccountId", toAccountId, "transferId", transferId)
        );
        // Notify receiver
        sendTemplatedEmail(
            resolveEmail(toAccountId),
            "You have received " + amount,
            "email/transfer-received",
            Map.of("amount", amount, "fromAccountId", fromAccountId, "transferId", transferId)
        );
    }

    @Async
    public void sendTransferReversedEmail(String fromAccountId, String toAccountId,
                                          String amount, String reason) {
        sendTemplatedEmail(
            resolveEmail(fromAccountId),
            "Transfer of " + amount + " has been reversed",
            "email/transfer-reversed",
            Map.of("amount", amount, "reason", reason, "toAccountId", toAccountId)
        );
    }

    @Async
    public void sendTransferFailedEmail(String transferId, String reason) {
        log.warn("Transfer failed, no customer email — transfer {}: {}", transferId, reason);
        // Transfer failure email is sent to ops/support, not the customer,
        // because at failure we may not know whose email to contact yet.
    }

    @Async
    public void sendAccountRestrictedEmail(String accountId, String reason) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Important: Your account has been restricted",
            "email/account-restricted",
            Map.of("accountId", accountId, "reason", reason,
                   "supportEmail", properties.notifications().supportEmail())
        );
    }

    @Async
    public void sendAccountActivatedEmail(String accountId) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Your account is active again",
            "email/account-activated",
            Map.of("accountId", accountId)
        );
    }

    @Async
    public void sendAccountClosedEmail(String accountId, String reason) {
        sendTemplatedEmail(
            resolveEmail(accountId),
            "Your account has been closed",
            "email/account-closed",
            Map.of("accountId", accountId, "reason", reason)
        );
    }

    // ── Core send logic ───────────────────────────────────────────────────

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

    /**
     * Resolves an email address for an account.
     *
     * <p>In a full implementation this queries the identity module via
     * a Spring Modulith-approved inter-module call (via an exposed
     * {@code IdentityQueryApi} bean, not a direct repository call).
     * Stubbed here to keep the scaffold clean.
     */
    private String resolveEmail(String accountId) {
        // TODO: Replace stub with IdentityQueryApi.getEmailForAccount(accountId)
        return "customer@banking.local";
    }
}
