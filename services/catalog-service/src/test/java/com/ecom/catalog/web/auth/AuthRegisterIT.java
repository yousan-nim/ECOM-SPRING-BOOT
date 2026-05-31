package com.ecom.catalog.web.auth;

import com.ecom.catalog.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end register flow against a real Postgres (Testcontainers) with Flyway
 * migrations applied — exercises validation, persistence, and the full security
 * filter chain via the real HTTP endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthRegisterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;

    private static final String BODY = """
            { "email": "%s", "password": "password123", "fullName": "Foo Bar", "phone": "" }
            """;

    @Test
    void register_validBody_returns201WithTokensAndPersistsUser() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("alice@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));

        assertThat(users.existsByEmail("alice@example.com")).isTrue();
    }

    @Test
    void register_mixedCaseEmail_persistsLowercaseAndBlocksDuplicate() throws Exception {
        String mixed = BODY.formatted("Bob@Example.com");
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(mixed))
                .andExpect(status().isCreated());

        assertThat(users.existsByEmail("bob@example.com")).isTrue();

        // Same address, different casing → CITEXT uniqueness rejects it.
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("BOB@example.COM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        String body = """
                { "email": "carol@example.com", "password": "short", "fullName": "Carol", "phone": "" }
                """;
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(users.existsByEmail("carol@example.com")).isFalse();
    }

    @Test
    void register_malformedEmail_returns400() throws Exception {
        String body = """
                { "email": "not-an-email", "password": "password123", "fullName": "Dave", "phone": "" }
                """;
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
