package com.jjenus.banking.identity.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * User profile entity.
 *
 * <p>The Keycloak {@code sub} claim (UUID) is the primary key.
 * Keycloak owns authentication; this table owns banking-specific profile
 * data: full name, phone number, KYC state.
 *
 * <p>Flyway migration: V004__create_audit_and_identity_tables.sql
 */
@Entity
@Table(
    name = "user_profiles",
    schema = "banking",
    indexes = {
        @Index(name = "idx_user_profiles_email",      columnList = "email",      unique = true),
        @Index(name = "idx_user_profiles_kyc_status", columnList = "kyc_status")
    }
)
public class UserProfileJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;                // Keycloak sub (UUID)

    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "kyc_status", nullable = false, length = 30)
    private String kycStatus;         // KycStatus enum name

    @Column(name = "kyc_submitted_at")
    private Instant kycSubmittedAt;

    @Column(name = "kyc_reviewed_at")
    private Instant kycReviewedAt;

    @Column(name = "kyc_rejection_reason", length = 500)
    private String kycRejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected UserProfileJpaEntity() {}

    public UserProfileJpaEntity(String id, String email, String fullName,
                                 String phoneNumber, String kycStatus,
                                 Instant createdAt) {
        this.id           = id;
        this.email        = email;
        this.fullName     = fullName;
        this.phoneNumber  = phoneNumber;
        this.kycStatus    = kycStatus;
        this.createdAt    = createdAt;
        this.lastUpdatedAt = createdAt;
    }

    public String getId()                  { return id; }
    public String getEmail()               { return email; }
    public String getFullName()            { return fullName; }
    public String getPhoneNumber()         { return phoneNumber; }
    public String getKycStatus()           { return kycStatus; }
    public Instant getKycSubmittedAt()     { return kycSubmittedAt; }
    public Instant getKycReviewedAt()      { return kycReviewedAt; }
    public String getKycRejectionReason()  { return kycRejectionReason; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getLastUpdatedAt()      { return lastUpdatedAt; }

    public void setFullName(String fullName)               { this.fullName = fullName; }
    public void setPhoneNumber(String phoneNumber)         { this.phoneNumber = phoneNumber; }
    public void setEmail(String email)                     { this.email = email; }
    public void setKycStatus(String kycStatus)             { this.kycStatus = kycStatus; }
    public void setKycSubmittedAt(Instant kycSubmittedAt)  { this.kycSubmittedAt = kycSubmittedAt; }
    public void setKycReviewedAt(Instant kycReviewedAt)    { this.kycReviewedAt = kycReviewedAt; }
    public void setKycRejectionReason(String reason)       { this.kycRejectionReason = reason; }
    public void setLastUpdatedAt(Instant lastUpdatedAt)    { this.lastUpdatedAt = lastUpdatedAt; }
}
