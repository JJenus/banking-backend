package com.jjenus.banking.shared.policy;

import com.jjenus.bank.core.policy.OverdraftPolicy;
import com.jjenus.bank.core.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Currency;

/**
 * Spring configuration that exposes an {@link OverdraftPolicy} bean based on
 * the {@code banking.overdraft.*} properties in {@code application.yml}.
 *
 * <p>Inject {@code OverdraftPolicy overdraftPolicy} wherever overdraft
 * decisions need to be made. Currently wired into
 * {@link com.jjenus.banking.accounts.application.AccountApplicationService}.
 *
 * <p>Example configurations:
 * <pre>
 * # No overdraft (default)
 * banking.overdraft.type: NONE
 *
 * # Fixed overdraft limit (₦5,000 per account)
 * banking.overdraft.type: FIXED_LIMIT
 * banking.overdraft.limit-amount: 5000.00
 * banking.overdraft.limit-currency: NGN
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(OverdraftPolicyProperties.class)
public class OverdraftPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(OverdraftPolicyConfig.class);

    @Bean
    public OverdraftPolicy overdraftPolicy(OverdraftPolicyProperties props) {
        return switch (props.effectiveType()) {

            case NONE -> {
                log.info("Overdraft policy: NONE (withdrawals strictly require sufficient balance)");
                yield OverdraftPolicy.none();
            }

            case FIXED_LIMIT -> {
                if (props.limitAmount() == null) {
                    throw new IllegalStateException(
                        "Missing required overdraft property: banking.overdraft.limit-amount");
                }
                if (props.limitCurrency() == null) {
                    throw new IllegalStateException(
                        "Missing required overdraft property: banking.overdraft.limit-currency");
                }
                Currency currency = Currency.getInstance(props.limitCurrency());
                Money limit = Money.of(props.limitAmount().toPlainString(), currency);
                log.info("Overdraft policy: FIXED_LIMIT {}", limit.format());
                yield OverdraftPolicy.fixedLimit(limit);
            }
        };
    }
}
