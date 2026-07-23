package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The committed OpenAPI contract is what the browser client is generated from, so it
 * has to keep describing the controllers that actually exist. This test regenerates it
 * from the live application and fails when the committed copy has drifted.
 *
 * <p>Refresh it after changing any endpoint by setting {@code ORGMEMORY_OPENAPI_WRITE=true} and
 * running {@code .\gradlew.bat :apps:api:test --tests "*OpenApiContractTests*"}, then regenerate
 * the browser client with {@code pnpm -C web gen:api}.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiContractTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theCommittedContractDescribesTheLiveApi() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The application context exposes Jackson 3; the contract is plain JSON, so a
        // local Jackson 2 mapper keeps this test independent of the runtime binding.
        ObjectMapper objectMapper = new ObjectMapper();
        Path contract = repositoryRoot().resolve("contracts/openapi.json");
        JsonNode expected = objectMapper.readTree(Files.readString(contract));
        // MockMvc reports its own host, which says nothing about the contract.
        JsonNode actual = ((com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(generated))
                .set("servers", expected.get("servers"));

        if (Boolean.parseBoolean(System.getenv("ORGMEMORY_OPENAPI_WRITE"))) {
            Files.writeString(contract, objectMapper.writeValueAsString(actual));
            return;
        }

        assertEquals(
                expected,
                actual,
                "contracts/openapi.json is stale; rerun this test with ORGMEMORY_OPENAPI_WRITE=true");
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the repository root from the test working directory");
        }
        return candidate;
    }
}
