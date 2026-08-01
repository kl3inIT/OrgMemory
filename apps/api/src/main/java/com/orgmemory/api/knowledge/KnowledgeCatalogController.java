package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogEntry;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogQuery;
import com.orgmemory.core.permission.KnowledgeClassification;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/catalog")
class KnowledgeCatalogController {

    private final KnowledgeCatalogQuery catalog;
    private final CurrentActorProvider actors;

    KnowledgeCatalogController(
            KnowledgeCatalogQuery catalog,
            CurrentActorProvider actors) {
        this.catalog = catalog;
        this.actors = actors;
    }

    @GetMapping
    @Operation(
            operationId = "listKnowledgeCatalog",
            summary = "List current permission-verified Knowledge versions for composition")
    List<KnowledgeCatalogItem> list(Authentication authentication) {
        return catalog.list(actors.current(authentication)).stream()
                .map(KnowledgeCatalogItem::from)
                .toList();
    }
}

record KnowledgeCatalogItem(
        UUID knowledgeAssetId,
        UUID knowledgeVersionId,
        long versionNumber,
        UUID knowledgeSpaceId,
        String title,
        String language,
        KnowledgeClassification classification,
        String contentDigest) {

    static KnowledgeCatalogItem from(KnowledgeCatalogEntry entry) {
        return new KnowledgeCatalogItem(
                entry.knowledgeAssetId(),
                entry.knowledgeVersionId(),
                entry.versionNumber(),
                entry.knowledgeSpaceId(),
                entry.title(),
                entry.language(),
                entry.classification(),
                entry.contentDigest());
    }
}
