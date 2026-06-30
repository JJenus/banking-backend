package com.jjenus.banking;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies that Spring Modulith module boundaries are respected.
 *
 * <p>This test MUST pass on every PR. A failure here means one module is
 * directly importing a class from another module's internal package —
 * which breaks the modular monolith contract.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Modules may only reference public API types of other modules
 *       (classes in the module's root package, not sub-packages)</li>
 *   <li>Cross-module communication must go through
 *       {@code ApplicationEventPublisher}, never direct bean injection
 *       across module boundaries</li>
 * </ul>
 *
 * <p>Also generates module documentation diagrams in {@code target/modulith-docs/}.
 */
class ApplicationModulesTest {

    @Test
    void moduleStructureIsValid() {
        ApplicationModules modules = ApplicationModules.of(BankingApplication.class);
        modules.verify();
    }

    @Test
    void generateModuleDocs() {
        ApplicationModules modules = ApplicationModules.of(BankingApplication.class);
        new Documenter(modules)
            .writeDocumentation()
            .writeIndividualModulesAsPlantUml();
    }
}
