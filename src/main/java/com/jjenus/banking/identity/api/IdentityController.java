package com.jjenus.banking.identity.api;

import com.jjenus.banking.identity.application.IdentityApplicationService;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaEntity;
import com.jjenus.banking.shared.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for the {@code identity} module.
 *
 * <p>Base path: {@code /api/v1/identity}
 *
 * <p>Registration is public (no JWT required) since the Keycloak account is
 * created first by the frontend, and this endpoint links the resulting
 * Keycloak {@code sub} to a banking profile. All other endpoints require
 * authentication.
 */
@RestController
@RequestMapping("/v1/identity")
@Tag(name = "Identity", description = "User profiles and KYC verification")
@SecurityRequirement(name = "bearer-key")
public class IdentityController {

    private final IdentityApplicationService identityService;

    public IdentityController(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    // ── Registration (public) ───────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a banking profile for a Keycloak user")
    public ResponseEntity<ProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileJpaEntity profile = identityService.register(
            request.keycloakSub(), request.email(), request.fullName(), request.phoneNumber()
        );
        return ResponseEntity
            .created(URI.create("/api/v1/identity/profile/" + profile.getId()))
            .body(ProfileResponse.from(profile));
    }

    // ── Self-service profile ────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    @PreAuthorize("isAuthenticated()")
    public ProfileResponse getMyProfile() {
        return ProfileResponse.from(identityService.getMyProfile(CurrentUser.id()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    @PreAuthorize("isAuthenticated()")
    public ProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserProfileJpaEntity updated = identityService.updateProfile(
            CurrentUser.id(), request.fullName(), request.phoneNumber()
        );
        return ProfileResponse.from(updated);
    }

    // ── KYC — customer actions ──────────────────────────────────────────────

    @PostMapping("/me/kyc/submit")
    @Operation(summary = "Submit KYC for review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ProfileResponse submitKyc() {
        return ProfileResponse.from(identityService.submitKyc(CurrentUser.id()));
    }

    // ── KYC — compliance actions ─────────────────────────────────────────────

    @GetMapping("/kyc/pending")
    @Operation(summary = "List profiles with KYC submitted, awaiting review")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public List<ProfileResponse> getPendingKyc() {
        return identityService.getPendingKycReviews()
            .stream().map(ProfileResponse::from).toList();
    }

    @PostMapping("/{userId}/kyc/start-review")
    @Operation(summary = "Begin reviewing a submitted KYC")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ProfileResponse startKycReview(@PathVariable String userId) {
        return ProfileResponse.from(identityService.startKycReview(userId));
    }

    @PostMapping("/{userId}/kyc/approve")
    @Operation(summary = "Approve a user's KYC")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ProfileResponse approveKyc(@PathVariable String userId) {
        return ProfileResponse.from(identityService.approveKyc(userId));
    }

    @PostMapping("/{userId}/kyc/reject")
    @Operation(summary = "Reject a user's KYC with a reason")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public ProfileResponse rejectKyc(@PathVariable String userId,
                                     @Valid @RequestBody RejectKycRequest request) {
        return ProfileResponse.from(identityService.rejectKyc(userId, request.reason()));
    }

    // ── Admin lookup ──────────────────────────────────────────────────────

    @GetMapping("/{userId}")
    @Operation(summary = "Get any user's profile (Admin/Teller/Compliance only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'COMPLIANCE')")
    public ProfileResponse getProfile(@PathVariable String userId) {
        return ProfileResponse.from(identityService.getProfileById(userId));
    }

    // ── Request / Response records ────────────────────────────────────────

    public record RegisterRequest(
        @NotBlank String keycloakSub,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phoneNumber
    ) {}

    public record UpdateProfileRequest(
        @Size(max = 200) String fullName,
        @Size(max = 20) String phoneNumber
    ) {}

    public record RejectKycRequest(
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record ProfileResponse(
        String id,
        String email,
        String fullName,
        String phoneNumber,
        String kycStatus,
        String kycSubmittedAt,
        String kycReviewedAt,
        String kycRejectionReason,
        String createdAt
    ) {
        static ProfileResponse from(UserProfileJpaEntity entity) {
            return new ProfileResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getKycStatus(),
                entity.getKycSubmittedAt() != null ? entity.getKycSubmittedAt().toString() : null,
                entity.getKycReviewedAt() != null ? entity.getKycReviewedAt().toString() : null,
                entity.getKycRejectionReason(),
                entity.getCreatedAt().toString()
            );
        }
    }
}
