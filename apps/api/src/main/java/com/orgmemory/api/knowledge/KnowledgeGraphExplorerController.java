package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.KnowledgeGraphExplorerService;
import com.orgmemory.core.knowledge.KnowledgeGraphView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-spaces/{knowledgeSpaceId}/graph/explorer")
class KnowledgeGraphExplorerController {

    private final KnowledgeGraphExplorerService explorer;
    private final CurrentActorProvider actors;

    KnowledgeGraphExplorerController(
            KnowledgeGraphExplorerService explorer,
            CurrentActorProvider actors) {
        this.explorer = explorer;
        this.actors = actors;
    }

    @GetMapping
    @Operation(
            operationId = "exploreKnowledgeGraph",
            summary = "Read a bounded permission-filtered graph view")
    KnowledgeGraphView explore(
            @PathVariable UUID knowledgeSpaceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer entityLimit,
            @RequestParam(required = false) Integer maxDepth,
            Authentication authentication,
            HttpServletResponse response) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-ID", requestId);
        return explorer.explore(
                actors.current(authentication),
                knowledgeSpaceId,
                q,
                entityLimit,
                maxDepth,
                requestId);
    }
}
