package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.security.oauth2.client.registration.orgmemory-api.client-secret=test-only")
@AutoConfigureMockMvc
class OrgMemoryMcpContextTests {

    private final List<McpStatelessServerFeatures.SyncToolSpecification>
            tools;
    private final List<McpStatelessServerFeatures.SyncResourceTemplateSpecification>
            resources;
    private final List<McpStatelessServerFeatures.SyncPromptSpecification>
            prompts;

    OrgMemoryMcpContextTests(
            @Qualifier("toolSpecs")
                    List<McpStatelessServerFeatures.SyncToolSpecification>
                            tools,
            @Qualifier("resourceTemplateSpecs")
                    List<McpStatelessServerFeatures.SyncResourceTemplateSpecification>
                            resources,
            @Qualifier("promptSpecs")
                    List<McpStatelessServerFeatures.SyncPromptSpecification>
                            prompts) {
        this.tools = tools;
        this.resources = resources;
        this.prompts = prompts;
    }

    @Autowired
    MockMvc mvc;

    @Test
    void publishesOnlyThePermissionAwareReadOnlyContracts() {
        assertEquals(9, tools.size());
        assertEquals(
                List.of(
                        "get_asset",
                        "get_asset_release",
                        "get_capability_pack",
                        "get_skill_manifest",
                        "render_prompt",
                        "resolve_asset_relations",
                        "resolve_skill",
                        "search_assets",
                        "search_knowledge"),
                tools.stream()
                        .map(spec -> spec.tool().name())
                        .sorted()
                        .toList());
        tools.forEach(spec -> {
            assertEquals(true, spec.tool().annotations().readOnlyHint());
            assertEquals(false, spec.tool().annotations().destructiveHint());
            assertEquals(true, spec.tool().annotations().idempotentHint());
            assertEquals(false, spec.tool().annotations().openWorldHint());
            assertEquals(
                    "object",
                    spec.tool().outputSchema().get("type"),
                    () -> spec.tool().name()
                            + " must publish an MCP 2025-11-25 object outputSchema");
        });
        assertEquals(2, resources.size());
        assertEquals(1, prompts.size());
        assertEquals("released_prompt", prompts.getFirst().prompt().name());
    }

    @Test
    void healthProbesRemainPublicWhileTheMcpEndpointRequiresAuthentication()
            throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "WWW-Authenticate",
                        "Bearer resource_metadata=\"http://localhost:8090/.well-known/oauth-protected-resource/mcp\""));
        mvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource")
                        .value("http://localhost:8090/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]")
                        .value("http://localhost:8180/realms/orgmemory"))
                .andExpect(jsonPath("$.scopes_supported[0]")
                        .value("assets:read"))
                .andExpect(jsonPath("$.scopes_supported.length()")
                        .value(1));
    }
}
