package com.jjenus.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Banking Backend — modular monolith entry point.
 *
 * <p>Module boundaries are enforced at test time via Spring Modulith.
 * Run {@code ApplicationModulesTest} to verify no module crosses another's
 * internal package boundary.
 *
 * <p>All seven modules are auto-detected by Spring Modulith from sub-packages
 * of this class's package:
 * <ul>
 *   <li>{@code identity}    — user profiles, KYC</li>
 *   <li>{@code accounts}    — account lifecycle, balance</li>
 *   <li>{@code transfers}   — transfer execution, reversal</li>
 *   <li>{@code ledger}      — double-entry journal</li>
 *   <li>{@code notifications} — email dispatch</li>
 *   <li>{@code audit}       — immutable event log</li>
 *   <li>{@code reporting}   — statements, PDF export</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
