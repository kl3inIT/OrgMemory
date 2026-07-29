package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class KnowledgeSearchTool {

    private final KnowledgeSearchApiClient search;
    private final McpApiAuthorization authorization;

    KnowledgeSearchTool(
            KnowledgeSearchApiClient search,
            McpApiAuthorization authorization) {
        this.search = search;
        this.authorization = authorization;
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
        return McpFailureBoundary.sanitized(() -> search.search(
                authorization.require(context),
                query,
                limit));
    }
}
