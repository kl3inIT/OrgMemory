package com.orgmemory.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RetrievalAdapterBoundaryTests {

    @Test
    void workerDependsOnlyOnIntentionalRetrievalContracts() {
        var dependencies = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.worker")
                .stream()
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getName())
                .filter(name -> name.startsWith("com.orgmemory.core.knowledge.retrieval."))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearchConfiguration",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalProperties"),
                dependencies);
    }
}
