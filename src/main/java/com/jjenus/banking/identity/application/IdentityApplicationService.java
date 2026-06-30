package com.jjenus.banking.identity.application;

import com.jjenus.banking.identity.IdentityQueryApi;
import com.jjenus.banking.identity.domain.IdentityEvent;
import com.jjenus.banking.identity.domain.KycStatus;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaEntity;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaRepository;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for all identity operations.
 *
 * <p>Also implements {@link IdentityQueryApi} — the public interface
 * that other modules use to query user data without crossing boundaries.
 */
@Service
@Transactional
public class IdentityApplicationService implements IdentityQueryApi {

    private final UserProfileJpaRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IdentityApplicationService(UserProfileJpaRepository profileRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Registration ─────────────────────────────────────────────────────

    /**
     * Registers a new user profile after Keycloak account creation.
     *
     * <p>Called from {@code IdentityController.register()}.
     * The Keycloak account is created first by the frontend (via Keycloak's
     * registration flow or admin API). This method creates the corresponding
     * banking profile row.
     *
     * @param keycloakSub Keycloak {@code sub} claim (UUID)
     * @param email       user's email (must match Keycloak account)
     * @param fullName    display name
     * @param phoneNumber optional phone number
     * @return the newly created profile
     */
    public UserProfileJpaEntity register(String keycloakSub, String email,
                                          String fullName, String phoneNumber) {
        if (profileRepository.existsById(keycloakSub)) {
            throw new IllegalStateException("A profile already exists for this user");
        }
        if (profileRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email address is already registered");
        }

        UserProfileJpaEntity entity = new UserProfileJpaEntity(
            keycloakSub, email, fullName, phoneNumber,
            KycStatus.PENDING.name(), Instant.now()
        );
        return profileRepository.save(entity);
    }

    // ── Profile management ────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileJpaEntity getMyProfile(String userId) {
        return profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));
    }

    /**
     * Updates name and phone number for the authenticated user.
     */
    public UserProfileJpaEntity updateProfile(String userId, String fullName, String phoneNumber) {
        UserProfileJpaEntity entity = profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));

        if (fullName != null && !fullName.isBlank()) {
            entity.setFullName(fullName.trim());
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            entity.setPhoneNumber(phoneNumber.trim());
        }
        entity.setLastUpdatedAt(Instant.now());
        return profileRepository.save(entity);
    }

    // ── KYC state machine ─────────────────────────────────────────────────

    /**
     * Customer submits KYC — transitions PENDING/REJECTED → SUBMITTED.
     * Publishes {@link IdentityEvent.KycSubmitted} on success.
     */
    public UserProfileJpaEntity submitKyc(String userId) {
        UserProfileJpaEntity entity = profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));

        KycStatus current = KycStatus.valueOf(entity.getKycStatus());
        if (!current.canSubmit()) {
            throw new IllegalStateException(
                "KYC cannot be submitted from status: " + current);
        }

        entity.setKycStatus(KycStatus.SUBMITTED.name());
        entity.setKycSubmittedAt(Instant.now());
        entity.setKycRejectionReason(null);
        entity.setLastUpdatedAt(Instant.now());
        UserProfileJpaEntity saved = profileRepository.save(entity);

        eventPublisher.publishEvent(IdentityEvent.kycSubmitted(userId));
        return saved;
    }

    /**
     * Compliance officer begins review — transitions SUBMITTED → UNDER_REVIEW.
     */
    public UserProfileJpaEntity startKycReview(String userId) {
        UserProfileJpaEntity entity = profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));

        KycStatus current = KycStatus.valueOf(entity.getKycStatus());
        if (!current.canReview()) {
            throw new IllegalStateException(
                "KYC cannot be reviewed from status: " + current);
        }

        entity.setKycStatus(KycStatus.UNDER_REVIEW.name());
        entity.setLastUpdatedAt(Instant.now());
        return profileRepository.save(entity);
    }

    /**
     * Compliance officer approves KYC — transitions UNDER_REVIEW → APPROVED.
     * Publishes {@link IdentityEvent.KycApproved} on success.
     */
    public UserProfileJpaEntity approveKyc(String userId) {
        UserProfileJpaEntity entity = profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));

        KycStatus current = KycStatus.valueOf(entity.getKycStatus());
        if (current != KycStatus.UNDER_REVIEW) {
            throw new IllegalStateException("KYC must be UNDER_REVIEW to approve");
        }

        entity.setKycStatus(KycStatus.APPROVED.name());
        entity.setKycReviewedAt(Instant.now());
        entity.setKycRejectionReason(null);
        entity.setLastUpdatedAt(Instant.now());
        UserProfileJpaEntity saved = profileRepository.save(entity);

        eventPublisher.publishEvent(IdentityEvent.kycApproved(userId));
        return saved;
    }

    /**
     * Compliance officer rejects KYC — transitions UNDER_REVIEW → REJECTED.
     * Publishes {@link IdentityEvent.KycRejected} on success.
     */
    public UserProfileJpaEntity rejectKyc(String userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        UserProfileJpaEntity entity = profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));

        KycStatus current = KycStatus.valueOf(entity.getKycStatus());
        if (current != KycStatus.UNDER_REVIEW) {
            throw new IllegalStateException("KYC must be UNDER_REVIEW to reject");
        }

        entity.setKycStatus(KycStatus.REJECTED.name());
        entity.setKycReviewedAt(Instant.now());
        entity.setKycRejectionReason(reason);
        entity.setLastUpdatedAt(Instant.now());
        UserProfileJpaEntity saved = profileRepository.save(entity);

        eventPublisher.publishEvent(IdentityEvent.kycRejected(userId, reason));
        return saved;
    }

    // ── Admin queries ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserProfileJpaEntity> getPendingKycReviews() {
        return profileRepository.findByKycStatus(KycStatus.SUBMITTED.name());
    }

    @Transactional(readOnly = true)
    public List<UserProfileJpaEntity> getUnderReviewKyc() {
        return profileRepository.findByKycStatus(KycStatus.UNDER_REVIEW.name());
    }

    @Transactional(readOnly = true)
    public UserProfileJpaEntity getProfileById(String userId) {
        return profileRepository.findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of("UserProfile", userId));
    }

    // ── IdentityQueryApi implementation (used by other modules) ──────────

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getEmailByUserId(String userId) {
        return profileRepository.findEmailById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getFullNameByUserId(String userId) {
        return profileRepository.findById(userId)
            .map(UserProfileJpaEntity::getFullName);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean profileExists(String userId) {
        return profileRepository.existsById(userId);
    }
}
