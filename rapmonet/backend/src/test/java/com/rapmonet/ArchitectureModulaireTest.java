package com.rapmonet;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Vérifie que les modules respectent leurs frontières (pas d'accès aux classes
 * internes d'un autre module, pas de dépendance circulaire) et génère la
 * documentation des modules dans target/spring-modulith-docs.
 */
class ArchitectureModulaireTest {

    private final ApplicationModules modules = ApplicationModules.of(RapmonetApplication.class);

    @Test
    void lesModulesRespectentLeursFrontieres() {
        modules.verify();
    }

    @Test
    void genererDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
