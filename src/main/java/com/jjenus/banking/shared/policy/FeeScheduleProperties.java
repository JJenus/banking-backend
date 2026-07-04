package com.jjenus.banking.shared.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level fee configuration bound from {@code banking.fees.*}.
 *
 * <p>Three independently configurable fee contexts:
 *
 * <ul>
 *   <li><b>intrabank-transfer</b> — sender and receiver are both accounts
 *       on this system (i.e. {@code toAccountId} exists in {@code banking.accounts})</li>
 *   <li><b>outgoing-transfer</b> — receiver is external (Paystack/NIBSS destination;
 *       {@code toAccountId} is not in {@code banking.accounts})</li>
 *   <li><b>withdrawal</b> — cash withdrawal from an account</li>
 * </ul>
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * banking:
 *   fees:
 *     intrabank-transfer:
 *       type: NONE
 *     outgoing-transfer:
 *       type: NIGERIAN_INTERBANK       # 0.1%, min NGN10, max NGN2000
 *     withdrawal:
 *       type: FLAT
 *       flat-amount: 20.00
 *       fee-currency: NGN
 * </pre>
 *
 * <p>Each slot independently supports: {@code NONE}, {@code PERCENTAGE},
 * {@code FLAT}, {@code NIGERIAN_INTERBANK}.
 */
@ConfigurationProperties(prefix = "banking.fees")
public record FeeScheduleProperties(
    FeePolicyProperties intrabankTransfer,
    FeePolicyProperties outgoingTransfer,
    FeePolicyProperties withdrawal
) {
    /** Null-safe accessor — returns a NONE policy if the slot is unconfigured. */
    public FeePolicyProperties intrabankTransferSafe() {
        return intrabankTransfer != null ? intrabankTransfer : noneSlot();
    }

    public FeePolicyProperties outgoingTransferSafe() {
        return outgoingTransfer != null ? outgoingTransfer : noneSlot();
    }

    public FeePolicyProperties withdrawalSafe() {
        return withdrawal != null ? withdrawal : noneSlot();
    }

    private static FeePolicyProperties noneSlot() {
        return new FeePolicyProperties(FeePolicyProperties.PolicyType.NONE,
            null, null, null, null, null);
    }
}
