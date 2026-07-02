package com.jjenus.banking.identity;

import com.jjenus.banking.identity.application.IdentityApplicationService;
import com.jjenus.banking.identity.domain.KycStatus;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaEntity;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentityApplicationService")
class IdentityApplicationServiceTest {

    @Mock UserProfileJpaRepository profileRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    IdentityApplicationService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationService(profileRepository, eventPublisher);
    }

    // ── register ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("register creates profile with PENDING KYC status")
    void register_createsPendingProfile() {
        when(profileRepository.existsById("sub-001")).thenReturn(false);
        when(profileRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.register(
            "sub-001", "ada@example.com", "Ada Obi", "+2348012345678");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.PENDING.name());
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        verify(profileRepository).save(any());
    }

    @Test
    @DisplayName("register with duplicate sub throws")
    void register_duplicateSub_throws() {
        when(profileRepository.existsById("sub-001")).thenReturn(true);

        assertThatThrownBy(() ->
            service.register("sub-001", "ada@example.com", "Ada Obi", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("profile already exists");
    }

    @Test
    @DisplayName("register with duplicate email throws")
    void register_duplicateEmail_throws() {
        when(profileRepository.existsById("sub-002")).thenReturn(false);
        when(profileRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
            service.register("sub-002", "ada@example.com", "Ada Obi", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already registered");
    }

    // ── KYC state machine ─────────────────────────────────────────────────

    @Test
    @DisplayName("submitKyc transitions PENDING -> SUBMITTED and publishes event")
    void submitKyc_pendingToSubmitted() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.submitKyc("sub-001");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.SUBMITTED.name());
        assertThat(result.getKycSubmittedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("submitKyc from REJECTED transitions to SUBMITTED (re-submission)")
    void submitKyc_rejectedToSubmitted() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.REJECTED.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.submitKyc("sub-001");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.SUBMITTED.name());
    }

    @Test
    @DisplayName("submitKyc from APPROVED throws — cannot re-submit approved KYC")
    void submitKyc_fromApproved_throws() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.APPROVED.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.submitKyc("sub-001"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("startKycReview transitions SUBMITTED -> UNDER_REVIEW")
    void startKycReview_submittedToUnderReview() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.SUBMITTED.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.startKycReview("sub-001");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.UNDER_REVIEW.name());
        verifyNoInteractions(eventPublisher); // no event on start-review
    }

    @Test
    @DisplayName("approveKyc transitions UNDER_REVIEW -> APPROVED and publishes event")
    void approveKyc_underReviewToApproved() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.UNDER_REVIEW.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.approveKyc("sub-001");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.APPROVED.name());
        assertThat(result.getKycReviewedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("rejectKyc transitions UNDER_REVIEW -> REJECTED with reason and publishes event")
    void rejectKyc_underReviewToRejected() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.UNDER_REVIEW.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileJpaEntity result = service.rejectKyc("sub-001", "Document unclear");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.REJECTED.name());
        assertThat(result.getKycRejectionReason()).isEqualTo("Document unclear");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("rejectKyc with blank reason throws")
    void rejectKyc_blankReason_throws() {
        assertThatThrownBy(() -> service.rejectKyc("sub-001", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("approveKyc from SUBMITTED (not UNDER_REVIEW) throws")
    void approveKyc_notUnderReview_throws() {
        UserProfileJpaEntity entity = pendingProfile("sub-001");
        entity.setKycStatus(KycStatus.SUBMITTED.name());
        when(profileRepository.findById("sub-001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approveKyc("sub-001"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("UNDER_REVIEW");
    }

    // ── IdentityQueryApi ──────────────────────────────────────────────────

    @Test
    @DisplayName("getEmailByUserId returns email for existing user")
    void getEmailByUserId_returnsEmail() {
        when(profileRepository.findEmailById("sub-001")).thenReturn(Optional.of("ada@example.com"));

        Optional<String> email = service.getEmailByUserId("sub-001");

        assertThat(email).hasValue("ada@example.com");
    }

    @Test
    @DisplayName("getEmailByUserId returns empty for unknown user")
    void getEmailByUserId_unknown_empty() {
        when(profileRepository.findEmailById("unknown")).thenReturn(Optional.empty());

        assertThat(service.getEmailByUserId("unknown")).isEmpty();
    }

    @Test
    @DisplayName("getPendingKycReviews returns SUBMITTED profiles")
    void getPendingKycReviews_returnsSubmitted() {
        UserProfileJpaEntity p1 = pendingProfile("sub-001");
        p1.setKycStatus(KycStatus.SUBMITTED.name());
        when(profileRepository.findByKycStatus(KycStatus.SUBMITTED.name()))
            .thenReturn(List.of(p1));

        List<UserProfileJpaEntity> result = service.getPendingKycReviews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKycStatus()).isEqualTo(KycStatus.SUBMITTED.name());
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private UserProfileJpaEntity pendingProfile(String sub) {
        return new UserProfileJpaEntity(
            sub, "user@example.com", "Test User", "+234800000000",
            KycStatus.PENDING.name(), Instant.now()
        );
    }
}
