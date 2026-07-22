package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.KnowledgeSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-spaces")
class KnowledgeSpaceController {

    private final KnowledgeSpaceService spaces;
    private final CurrentActorProvider actors;

    KnowledgeSpaceController(KnowledgeSpaceService spaces, CurrentActorProvider actors) {
        this.spaces = spaces;
        this.actors = actors;
    }

    @GetMapping("/upload-targets")
    @Operation(
            operationId = "listKnowledgeSpaceUploadTargets",
            summary = "List Knowledge Spaces where the current user may add knowledge")
    List<KnowledgeSpaceResponse> listUploadTargets(Authentication authentication) {
        return spaces.listUploadTargets(actors.current(authentication)).stream()
                .map(KnowledgeSpaceResponse::from)
                .toList();
    }
}
