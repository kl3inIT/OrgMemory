package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class McpTransportConfiguration {

    static final String AUTHENTICATION_CONTEXT_KEY =
            "orgmemory.authentication";

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(
                        Map.of(
                                AUTHENTICATION_CONTEXT_KEY,
                                requireAuthentication())))
                .build();
    }

    private static org.springframework.security.core.Authentication
            requireAuthentication() {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "An authenticated MCP identity is required");
        }
        return authentication;
    }
}
