package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private final McpApiAuthorization authorization =
            mock(McpApiAuthorization.class);
    private final KnowledgeSearchTool tool =
            new KnowledgeSearchTool(search, authorization);

    @Test
    void delegatesToTheSameAuthenticatedApiContract() {
        var expected =
                new KnowledgeSearchApiClient.SearchResult(
                        "request-1",
                        List.of());
        var context = McpTransportContext.create(
                Map.of("request", "test"));
        when(authorization.require(context))
                .thenReturn("Bearer exchanged-api-token");
        when(search.search(
                        "Bearer exchanged-api-token",
                        "leave policy",
                        8))
                .thenReturn(expected);

        var result =
                tool.searchKnowledge("leave policy", 8, context);

        assertEquals(expected, result);
        verify(search).search(
                "Bearer exchanged-api-token",
                "leave policy",
                8);
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
        assertTrue(declaration.description().contains("one-based sourceNumber"));
        assertTrue(declaration.description().contains("Answer directly in the user's language"));
        assertTrue(declaration.description().contains("bracketed marker such as [1]"));
        assertTrue(declaration.description().contains("do not repeat document ownership"));
        assertFalse(declaration.annotations().destructiveHint());
        assertFalse(declaration.annotations().openWorldHint());
        assertEquals(true, declaration.annotations().readOnlyHint());
        assertEquals(true, declaration.annotations().idempotentHint());
    }
}
