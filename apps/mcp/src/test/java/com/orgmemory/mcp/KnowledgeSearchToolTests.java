package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

class KnowledgeSearchToolTests {

    private final KnowledgeSearchApiClient search =
            mock(KnowledgeSearchApiClient.class);
    private final KnowledgeSearchTool tool =
            new KnowledgeSearchTool(search);

    @Test
    void delegatesToTheSameAuthenticatedApiContract() {
        var expected =
                new KnowledgeSearchApiClient.SearchResult(
                        "request-1",
                        List.of());
        when(search.search(
                        "Bearer verified-token",
                        "leave policy",
                        8))
                .thenReturn(expected);
        var context = McpTransportContext.create(Map.of(
                McpTransportConfiguration.AUTHORIZATION_CONTEXT_KEY,
                "Bearer verified-token"));

        var result =
                tool.searchKnowledge("leave policy", 8, context);

        assertEquals(expected, result);
        verify(search).search(
                "Bearer verified-token",
                "leave policy",
                8);
    }

    @Test
    void refusesARequestWithoutForwardableIdentity() {
        assertThrows(
                KnowledgeSearchApiClient.KnowledgeSearchGatewayException.class,
                () -> tool.searchKnowledge(
                        "leave policy",
                        8,
                        McpTransportContext.EMPTY));
    }

    @Test
    void publishesExplicitReadOnlyClosedWorldHints() throws Exception {
        McpTool declaration = KnowledgeSearchTool.class
                .getDeclaredMethod(
                        "searchKnowledge",
                        String.class,
                        Integer.class,
                        McpTransportContext.class)
                .getAnnotation(McpTool.class);

        assertEquals("search_knowledge", declaration.name());
        assertFalse(declaration.annotations().destructiveHint());
        assertFalse(declaration.annotations().openWorldHint());
        assertEquals(true, declaration.annotations().readOnlyHint());
        assertEquals(true, declaration.annotations().idempotentHint());
    }
}
