package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
