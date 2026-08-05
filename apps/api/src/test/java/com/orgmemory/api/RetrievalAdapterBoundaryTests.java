package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RetrievalAdapterBoundaryTests {

    @Test
    void apiDependsOnlyOnIntentionalRetrievalContracts() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.api")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> name.startsWith("com.orgmemory.core.knowledge.retrieval."))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch",
                        "com.orgmemory.core.knowledge.retrieval.CitationContent",
                        "com.orgmemory.core.knowledge.retrieval.CitationContentService",
                        "com.orgmemory.core.knowledge.retrieval.CitationEvidenceExcerpt",
                        "com.orgmemory.core.knowledge.retrieval.CitationEvidenceReference",
                        "com.orgmemory.core.knowledge.retrieval.CitationEvidenceService",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService$KeywordPlanSnapshot",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService$RetrievalObservation",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService$RetrievedDocument",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagRetrievalPolicy",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagRetrievalPolicy$RerankPolicy",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeAssetAccessInspector",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeAssetAccessInspector$AssetInspection",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalProperties",
                        "com.orgmemory.core.knowledge.retrieval.QueryEmbedding",
                        "com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort",
                        "com.orgmemory.core.knowledge.retrieval.SourceContent",
                        "com.orgmemory.core.knowledge.retrieval.SourceContentService"),
                dependencies);
    }
}
