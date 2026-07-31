package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.web.client.ResourceAccessException;

class McpToolErrorSurfaceTests {

    private static final String INTERNAL_HOST = "orgmemory-api.internal";

    private final KnowledgeSearchApiClient search =
            mock(KnowledgeSearchApiClient.class);
    private final McpApiAuthorization authorization =
            mock(McpApiAuthorization.class);
    private final McpTransportContext context =
            McpTransportContext.create(Map.of("request", "test"));

    @Test
    void downstreamFailureBecomesASanitizedToolErrorWithoutTransportDetail() {
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(search.search(any(), any(), any()))
                .thenThrow(new McpGatewayException(
                        "OrgMemory knowledge search is temporarily unavailable",
                        new ResourceAccessException(
                                "I/O error on GET request for \"http://"
                                        + INTERNAL_HOST
                                        + ":8080/api/knowledge/search\"",
                                new UnknownHostException(INTERNAL_HOST))));

        var result = callSearchKnowledge();

        assertEquals(Boolean.TRUE, result.isError());
        String text = textOf(result);
        assertTrue(
                text.contains(
                        "OrgMemory knowledge search is temporarily unavailable"),
                text);
        assertFalse(text.contains(INTERNAL_HOST), text);
        assertFalse(text.contains("/api/knowledge/search"), text);
    }

    private io.modelcontextprotocol.spec.McpSchema.CallToolResult
            callSearchKnowledge() {
        var specification = SyncMcpAnnotationProviders
                .statelessToolSpecifications(List.of(
                        new KnowledgeSearchTool(search, authorization)))
                .stream()
                .filter(spec -> spec.tool().name().equals("search_knowledge"))
                .findFirst()
                .orElseThrow();
        return specification.callHandler().apply(
                context,
                new CallToolRequest(
                        "search_knowledge",
                        Map.of("query", "leave policy"),
                        null));
    }

    private static String textOf(
            io.modelcontextprotocol.spec.McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
