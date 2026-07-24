package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class KnowledgeSearchTool {

    private final KnowledgeSearchApiClient search;

    KnowledgeSearchTool(KnowledgeSearchApiClient search) {
        this.search = search;
    }

    @McpTool(
            name = "search_knowledge",
            title = "Search organization knowledge",
            description = "Searches only the organization knowledge that the authenticated user may access. "
                    + "Returns permission-verified evidence and citation IDs.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    title = "Search organization knowledge",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    KnowledgeSearchApiClient.SearchResult searchKnowledge(
            @McpToolParam(
                            description = "Natural-language question or search query")
                    String query,
            @McpToolParam(
                            required = false,
                            description = "Maximum evidence items to return")
                    Integer limit,
            McpTransportContext context) {
        String authorization = Objects.toString(
                context.get(
                        McpTransportConfiguration.AUTHORIZATION_CONTEXT_KEY),
                "");
        if (authorization.isBlank()) {
            throw new KnowledgeSearchApiClient.KnowledgeSearchGatewayException(
                    "The MCP request has no authenticated identity");
        }
        return search.search(authorization, query, limit);
    }
}
