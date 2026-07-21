package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.KnowledgeRetrievalService;
import com.orgmemory.core.organization.CurrentActor;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-assets")
class KnowledgeAssetController {

    private final KnowledgeRetrievalService knowledge;
    private final CurrentActorProvider actors;

    KnowledgeAssetController(KnowledgeRetrievalService knowledge, CurrentActorProvider actors) {
        this.knowledge = knowledge;
        this.actors = actors;
    }

    @GetMapping
    List<KnowledgeAssetSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            HttpServletResponse response,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        String requestId = requestId(response);
        return knowledge.search(actor, q, limit, requestId).stream()
                .map(KnowledgeAssetSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{assetId}")
    KnowledgeAssetDetailResponse get(
            @PathVariable UUID assetId,
            HttpServletResponse response,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        String requestId = requestId(response);
        return KnowledgeAssetDetailResponse.from(knowledge.get(actor, assetId, requestId));
    }

    private static String requestId(HttpServletResponse response) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        return requestId;
    }
}
