package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(OrgMemoryMcpContextTests.TestSecurityConfiguration.class)
class OrgMemoryMcpContextTests {

    private final List<McpStatelessServerFeatures.SyncToolSpecification>
            tools;

    OrgMemoryMcpContextTests(
            @Qualifier("toolSpecs")
                    List<McpStatelessServerFeatures.SyncToolSpecification>
                            tools) {
        this.tools = tools;
    }

    @Autowired
    MockMvc mvc;

    @Test
    void publishesOnlyThePermissionAwareReadOnlySearchTool() {
        assertEquals(1, tools.size());
        assertEquals(
                "search_knowledge",
                tools.getFirst().tool().name());
        assertEquals(
                true,
                tools.getFirst().tool().annotations().readOnlyHint());
    }

    @Test
    void healthProbesRemainPublicWhileTheMcpEndpointRequiresAuthentication()
            throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
