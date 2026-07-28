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
 * The committed product and SCIM OpenAPI contracts are generated independently
 * from the live application. The browser client consumes only the product
 * contract, so a machine provisioning route cannot enter that client by accident.
 *
 * <p>Refresh them after changing an endpoint by setting {@code ORGMEMORY_OPENAPI_WRITE=true} and
 * running {@code .\gradlew.bat :apps:api:test --tests "*OpenApiContractTests*"}, then regenerate
 * the browser client with {@code pnpm --filter @orgmemory/web gen:api}.
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
    void theCommittedProductContractDescribesTheLiveProductApi() throws Exception {
        verifyContract("product", "openapi.json", "http://localhost");
    }

    @Test
    void theCommittedScimContractDescribesOnlyTheLiveScimApi() throws Exception {
        verifyContract("scim", "scim-openapi.json", "https://memory.company.com");
    }

    private void verifyContract(String group, String fileName, String serverUrl)
            throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs/{group}", group))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The application context exposes Jackson 3; the contract is plain JSON, so a
        // local Jackson 2 mapper keeps this test independent of the runtime binding.
        ObjectMapper objectMapper = new ObjectMapper();
        Path contract = repositoryRoot().resolve("contracts").resolve(fileName);
        JsonNode expected = Files.exists(contract)
                ? objectMapper.readTree(Files.readString(contract))
                : null;
        // MockMvc reports its own host, which says nothing about the contract.
        var actual =
                (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(generated);
        actual.putArray("servers").addObject().put("url", serverUrl);

        if (Boolean.parseBoolean(System.getenv("ORGMEMORY_OPENAPI_WRITE"))) {
            Files.writeString(contract, objectMapper.writeValueAsString(actual));
            return;
        }

        assertEquals(
                expected,
                actual,
                "contracts/" + fileName
                        + " is stale; rerun this test with ORGMEMORY_OPENAPI_WRITE=true");
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
