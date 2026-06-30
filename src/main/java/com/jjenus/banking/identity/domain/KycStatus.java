package com.jjenus.banking.identity.domain;

/**
 * KYC (Know Your Customer) verification state machine.
 *
 * <pre>
 * PENDING → SUBMITTED → UNDER_REVIEW → APPROVED
 *                                    ↘ REJECTED → SUBMITTED (re-submission allowed)
 * </pre>
 */
public enum KycStatus {

    /** User registered but has not submitted KYC documents yet. */
    PENDING,

    /** User has submitted documents; awaiting review queue. */
    SUBMITTED,

    /** A compliance officer has picked up the review. */
    UNDER_REVIEW,

    /** KYC approved — full account functionality unlocked. */
    APPROVED,

    /** KYC rejected — user may re-submit with corrected documents. */
    REJECTED;

    public boolean canSubmit() {
        return this == PENDING || this == REJECTED;
    }

    public boolean canReview() {
        return this == SUBMITTED;
    }

    public boolean isVerified() {
        return this == APPROVED;
    }
}
