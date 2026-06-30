package com.jjenus.banking.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed configuration properties bound from {@code application.yml}
 * under the {@code banking.*} prefix.
 *
 * <p>Inject via constructor: {@code private final BankingProperties props;}
 */
@ConfigurationProperties(prefix = "banking")
public record BankingProperties(
    Keycloak keycloak,
    Idempotency idempotency,
    Notifications notifications,
    Security security
) {

    public record Keycloak(
        String realm,
        String adminClientId,
        String adminClientSecret,
        String serverUrl
    ) {}

    public record Idempotency(
        int ttlHours
    ) {}

    public record Notifications(
        String fromName,
        String supportEmail
    ) {}

    public record Security(
        Cors cors
    ) {
        public record Cors(
            List<String> allowedOrigins,
            String allowedMethods,
            String allowedHeaders,
            String exposedHeaders,
            long maxAge
        ) {}
    }
}
