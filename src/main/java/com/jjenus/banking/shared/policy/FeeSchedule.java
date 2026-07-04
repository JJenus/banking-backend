package com.jjenus.banking.shared.policy;

import com.jjenus.bank.core.policy.FeePolicy;

/**
 * Holds the three {@link FeePolicy} instances for the three fee contexts.
 *
 * <p>Injected as a single Spring bean. Consumers pick the relevant policy:
 * <pre>
 *   FeePolicy policy = feeSchedule.forTransfer(isIntrabank);
 *   // or
 *   FeePolicy policy = feeSchedule.withdrawal();
 * </pre>
 *
 * @param intrabankTransfer fee for transfers where both accounts are on this system
 * @param outgoingTransfer  fee for transfers to external accounts
 * @param withdrawal        fee charged on cash withdrawals
 */
public record FeeSchedule(
    FeePolicy intrabankTransfer,
    FeePolicy outgoingTransfer,
    FeePolicy withdrawal
) {
    /**
     * Selects the transfer fee policy based on whether the transfer is intrabank.
     *
     * @param intrabank {@code true} if both sender and receiver accounts exist
     *                  in {@code banking.accounts}; {@code false} for external transfers
     */
    public FeePolicy forTransfer(boolean intrabank) {
        return intrabank ? intrabankTransfer : outgoingTransfer;
    }

    /**
     * Returns a human-readable summary of all configured fees.
     * Used by {@code GET /v1/transfers/fee-info}.
     */
    public FeeSummary summary() {
        return new FeeSummary(
            intrabankTransfer.description(),
            outgoingTransfer.description(),
            withdrawal.description()
        );
    }

    public record FeeSummary(
        String intrabankTransfer,
        String outgoingTransfer,
        String withdrawal
    ) {}
}
