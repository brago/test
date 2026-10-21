package com.rapmonet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Démarre l'application complète sur la base PostgreSQL de test
 * (rapmonet_test) et vérifie que l'API et les migrations fonctionnent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void infoSystemeRenvoieVersionEtBase() throws Exception {
        mvc.perform(get("/api/systeme/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("RAPMONET"))
                .andExpect(jsonPath("$.baseDeDonnees.produit").value("PostgreSQL"))
                .andExpect(jsonPath("$.baseDeDonnees.schemaVersion").value("4"));
    }

    @Test
    void santeEstUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
