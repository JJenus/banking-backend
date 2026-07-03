package com.jjenus.banking.shared.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Typed configuration for the active overdraft policy, bound from
 * {@code banking.overdraft.*} in {@code application.yml}.
 *
 * <p>Supported policy types:
 * <ul>
 *   <li>{@code NONE}        — no overdraft allowed (default)</li>
 *   <li>{@code FIXED_LIMIT} — overdraft allowed up to a fixed amount per account</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "banking.overdraft")
public record OverdraftPolicyProperties(
    PolicyType type,
    BigDecimal limitAmount,
    String limitCurrency
) {
    public enum PolicyType { NONE, FIXED_LIMIT }

    public PolicyType effectiveType() {
        return type != null ? type : PolicyType.NONE;
    }
}
