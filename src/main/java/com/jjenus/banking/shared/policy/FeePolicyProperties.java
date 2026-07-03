package com.jjenus.banking.shared.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Typed configuration for the active fee policy, bound from
 * {@code banking.fees.*} in {@code application.yml}.
 *
 * <p>Supported policy types:
 * <ul>
 *   <li>{@code NONE}       — always zero (default; useful for development/testing)</li>
 *   <li>{@code PERCENTAGE} — rate * amount, clamped to [min, max]</li>
 *   <li>{@code FLAT}       — fixed amount per transfer</li>
 *   <li>{@code NIGERIAN_INTERBANK} — 0.1%, min ₦10, max ₦2,000 (CBN-style)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "banking.fees")
public record FeePolicyProperties(
    /** Policy type. Defaults to NONE if not set. */
    PolicyType type,

    /** Rate for PERCENTAGE policy (e.g. 0.001 = 0.1%). */
    BigDecimal rate,

    /** Minimum fee amount (currency must match account). */
    BigDecimal minAmount,

    /** Maximum fee amount (currency must match account). */
    BigDecimal maxAmount,

    /** Currency for min/max bounds (ISO 4217). Required for PERCENTAGE and FLAT. */
    String feeCurrency,

    /** Flat fee amount for FLAT policy. */
    BigDecimal flatAmount
) {
    public enum PolicyType {
        NONE, PERCENTAGE, FLAT, NIGERIAN_INTERBANK
    }

    /** Returns the effective type, defaulting to NONE when not configured. */
    public PolicyType effectiveType() {
        return type != null ? type : PolicyType.NONE;
    }
}
