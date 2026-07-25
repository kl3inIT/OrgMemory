package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.KnowledgeCatalogItem;
import com.orgmemory.core.knowledge.KnowledgeCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/catalog")
class KnowledgeCatalogController {

    private final KnowledgeCatalogService catalog;
    private final CurrentActorProvider actors;

    KnowledgeCatalogController(
            KnowledgeCatalogService catalog,
            CurrentActorProvider actors) {
        this.catalog = catalog;
        this.actors = actors;
    }

    @GetMapping
    @Operation(
            operationId = "listKnowledgeCatalog",
            summary = "List current permission-verified Knowledge versions for composition")
    List<KnowledgeCatalogItem> list(Authentication authentication) {
        return catalog.list(actors.current(authentication));
    }
}
