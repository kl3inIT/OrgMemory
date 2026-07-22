package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/search")
class KnowledgeSearchController {

    private final SecureKnowledgeRetrievalService retrieval;
    private final CurrentActorProvider actors;

    KnowledgeSearchController(SecureKnowledgeRetrievalService retrieval, CurrentActorProvider actors) {
        this.retrieval = retrieval;
        this.actors = actors;
    }

    @GetMapping
    @Operation(operationId = "searchKnowledge", summary = "Search permission-verified knowledge evidence")
    KnowledgeSearchResponse search(
            @RequestParam String q,
            @RequestParam(required = false) Integer limit,
            HttpServletResponse response,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        return KnowledgeSearchResponse.from(
                retrieval.search(actors.current(authentication), q, limit, requestId));
    }
}
