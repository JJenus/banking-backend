package com.jjenus.banking.shared.policy;

import com.jjenus.bank.core.policy.FeePolicy;
import com.jjenus.bank.core.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Currency;

/**
 * Builds a {@link FeeSchedule} bean from the {@code banking.fees.*}
 * configuration, containing three independent {@link FeePolicy} instances:
 * one for intrabank transfers, one for outgoing transfers, and one for
 * withdrawals.
 */
@Configuration
@EnableConfigurationProperties(FeeScheduleProperties.class)
public class FeePolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(FeePolicyConfig.class);

    @Bean
    public FeeSchedule feeSchedule(FeeScheduleProperties props) {
        FeePolicy intrabank  = build("intrabank-transfer",  props.intrabankTransferSafe());
        FeePolicy outgoing   = build("outgoing-transfer",   props.outgoingTransferSafe());
        FeePolicy withdrawal = build("withdrawal",          props.withdrawalSafe());
        return new FeeSchedule(intrabank, outgoing, withdrawal);
    }

    private FeePolicy build(String label, FeePolicyProperties slot) {
        FeePolicy policy = switch (slot.effectiveType()) {

            case NONE -> {
                log.info("Fee[{}]: NONE", label);
                yield FeePolicy.none();
            }

            case NIGERIAN_INTERBANK -> {
                log.info("Fee[{}]: NIGERIAN_INTERBANK (0.1%, min NGN 10, max NGN 2,000)", label);
                yield FeePolicy.nigerianInterbank();
            }

            case PERCENTAGE -> {
                require(slot.rate(),       label, "rate");
                require(slot.minAmount(),  label, "min-amount");
                require(slot.maxAmount(),  label, "max-amount");
                require(slot.feeCurrency(), label, "fee-currency");

                Currency currency = Currency.getInstance(slot.feeCurrency());
                Money min = Money.of(slot.minAmount().toPlainString(), currency);
                Money max = Money.of(slot.maxAmount().toPlainString(), currency);
                log.info("Fee[{}]: PERCENTAGE {}% (min {}, max {})", label,
                    slot.rate().multiply(java.math.BigDecimal.valueOf(100))
                               .stripTrailingZeros().toPlainString(),
                    min.format(), max.format());
                yield FeePolicy.percentage(slot.rate(), min, max);
            }

            case FLAT -> {
                require(slot.flatAmount(),  label, "flat-amount");
                require(slot.feeCurrency(), label, "fee-currency");

                Currency currency = Currency.getInstance(slot.feeCurrency());
                Money flat = Money.of(slot.flatAmount().toPlainString(), currency);
                log.info("Fee[{}]: FLAT {}", label, flat.format());
                yield FeePolicy.flat(flat);
            }
        };
        return policy;
    }

    private static void require(Object value, String label, String field) {
        if (value == null) {
            throw new IllegalStateException(
                "Missing required fee property for [" + label + "]: banking.fees."
                + label + "." + field);
        }
    }
}
