package com.jjenus.banking.shared.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Configuration for a single fee policy slot within the {@link FeeScheduleProperties}.
 *
 * <p>Each slot (intrabank-transfer, outgoing-transfer, withdrawal) carries its own
 * independent set of these properties so each context can use a different policy type
 * and parameters.
 */
public record FeePolicyProperties(
    PolicyType type,
    BigDecimal rate,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String feeCurrency,
    BigDecimal flatAmount
) {
    public enum PolicyType {
        NONE, PERCENTAGE, FLAT, NIGERIAN_INTERBANK
    }

    public PolicyType effectiveType() {
        return type != null ? type : PolicyType.NONE;
    }
}
