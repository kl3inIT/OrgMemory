package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class McpTransportConfiguration {

    static final String AUTHORIZATION_CONTEXT_KEY =
            "orgmemory.authorization";

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(
                        Map.of(
                                AUTHORIZATION_CONTEXT_KEY,
                                requireBearer(request.headers().firstHeader(
                                        HttpHeaders.AUTHORIZATION)))))
                .build();
    }

    private static String requireBearer(String authorization) {
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(
                        true,
                        0,
                        "Bearer ",
                        0,
                        7)) {
            throw new IllegalStateException(
                    "An authenticated bearer token is required");
        }
        return authorization;
    }
}
