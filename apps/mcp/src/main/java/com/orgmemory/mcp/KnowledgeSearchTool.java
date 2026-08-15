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
                    + "Returns ordered, permission-verified evidence with a one-based sourceNumber and citation ID. "
                    + "Answer directly in the user's language using only that evidence, cite factual claims with "
                    + "sourceNumber as a bracketed marker such as [1], and do not repeat document ownership, "
                    + "classification, access-control wording, or dataset disclaimers unless explicitly asked.",
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
