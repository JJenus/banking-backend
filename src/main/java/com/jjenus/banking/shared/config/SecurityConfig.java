package com.jjenus.banking.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Security configuration.
 *
 * <p>The application is a stateless OAuth2 resource server. All authentication
 * is delegated to the self-hosted Keycloak instance. JWTs are validated against
 * Keycloak's public keys fetched from the JWK Set URI configured in
 * {@code application.yml}.
 *
 * <p>Role extraction: Keycloak encodes roles in the JWT claim
 * {@code realm_access.roles}. The {@link JwtAuthenticationConverter} below
 * maps these to Spring Security {@code ROLE_*} authorities.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${banking.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${banking.security.cors.allowed-methods}")
    private String allowedMethods;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()

                // Identity — registration is public, profile requires auth
                .requestMatchers(HttpMethod.POST, "/v1/identity/register").permitAll()

                // Admin endpoints — require ADMIN role
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")

                // Compliance / audit endpoints — ADMIN or COMPLIANCE
                .requestMatchers("/v1/audit/**").hasAnyRole("ADMIN", "COMPLIANCE")

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Extracts Keycloak realm roles from the JWT and maps them to Spring
     * Security authorities.
     *
     * <p>Keycloak JWT structure:
     * <pre>
     * {
     *   "realm_access": {
     *     "roles": ["CUSTOMER", "TELLER", "ADMIN", "COMPLIANCE"]
     *   }
     * }
     * </pre>
     *
     * <p>These become {@code ROLE_CUSTOMER}, {@code ROLE_TELLER}, etc.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess =
                jwt.getClaimAsMap("realm_access");

            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return List.of();
            }

            @SuppressWarnings("unchecked")
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");

            return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of(allowedMethods.split(",")));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Idempotency-Key", "X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
