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
 * Spring configuration that exposes a {@link FeePolicy} bean based on
 * the {@code banking.fees.*} properties in {@code application.yml}.
 *
 * <p>Inject {@code FeePolicy feePolicy} wherever fees need to be calculated.
 * Currently wired into {@link com.jjenus.banking.transfers.application.TransferApplicationService}.
 *
 * <p>Changing the fee policy requires only a config change and a restart —
 * no code change needed.
 *
 * <p>Example configurations:
 * <pre>
 * # No fee (default / development)
 * banking.fees.type: NONE
 *
 * # Nigerian interbank style (0.1%, min ₦10, max ₦2,000)
 * banking.fees.type: NIGERIAN_INTERBANK
 *
 * # Custom percentage (0.5%, min ₦50, max ₦5,000)
 * banking.fees.type: PERCENTAGE
 * banking.fees.rate: 0.005
 * banking.fees.min-amount: 50.00
 * banking.fees.max-amount: 5000.00
 * banking.fees.fee-currency: NGN
 *
 * # Flat fee (₦100 per transfer)
 * banking.fees.type: FLAT
 * banking.fees.flat-amount: 100.00
 * banking.fees.fee-currency: NGN
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(FeePolicyProperties.class)
public class FeePolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(FeePolicyConfig.class);

    @Bean
    public FeePolicy feePolicy(FeePolicyProperties props) {
        FeePolicy policy = switch (props.effectiveType()) {

            case NONE -> {
                log.info("Fee policy: NONE (all transfers are free)");
                yield FeePolicy.none();
            }

            case NIGERIAN_INTERBANK -> {
                log.info("Fee policy: NIGERIAN_INTERBANK (0.1%, min NGN 10, max NGN 2,000)");
                yield FeePolicy.nigerianInterbank();
            }

            case PERCENTAGE -> {
                requireField(props.rate(),       "banking.fees.rate");
                requireField(props.minAmount(),  "banking.fees.min-amount");
                requireField(props.maxAmount(),  "banking.fees.max-amount");
                requireField(props.feeCurrency(),"banking.fees.fee-currency");

                Currency currency = Currency.getInstance(props.feeCurrency());
                Money min = Money.of(props.minAmount().toPlainString(), currency);
                Money max = Money.of(props.maxAmount().toPlainString(), currency);

                log.info("Fee policy: PERCENTAGE {}% (min {}, max {})",
                    props.rate().multiply(java.math.BigDecimal.valueOf(100))
                         .stripTrailingZeros().toPlainString(),
                    min.format(), max.format());

                yield FeePolicy.percentage(props.rate(), min, max);
            }

            case FLAT -> {
                requireField(props.flatAmount(), "banking.fees.flat-amount");
                requireField(props.feeCurrency(),"banking.fees.fee-currency");

                Currency currency = Currency.getInstance(props.feeCurrency());
                Money flat = Money.of(props.flatAmount().toPlainString(), currency);

                log.info("Fee policy: FLAT {}", flat.format());
                yield FeePolicy.flat(flat);
            }
        };

        return policy;
    }

    private static void requireField(Object value, String propertyKey) {
        if (value == null) {
            throw new IllegalStateException(
                "Missing required fee policy property: " + propertyKey +
                ". Check banking.fees.* in application.yml.");
        }
    }
}
