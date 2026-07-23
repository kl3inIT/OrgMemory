package com.orgmemory.graphrag.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionResultTests {

    private final ExtractionProfile profile = new ExtractionProfile(
            "openai",
            "gpt-5.5",
            "lightrag-v1.5.4-orgmemory-v1",
            10,
            10);

    @Test
    void acceptsLightRagCompatibleStructuredEntitiesAndRelations() {
        ExtractedEntity organization = new ExtractedEntity(
                "entity-1",
                "OrgMemory",
                "ORGANIZATION",
                "An enterprise knowledge platform.",
                0.98);
        ExtractedEntity product = new ExtractedEntity(
                "entity-2",
                "Secure Search",
                "PRODUCT",
                "Permission-aware retrieval.",
                0.96);

        assertDoesNotThrow(() -> new ExtractionResult(
                profile,
                List.of(organization, product),
                List.of(new ExtractedRelation(
                        "entity-1",
                        "entity-2",
                        "BUILDS",
                        List.of("product", "security"),
                        "OrgMemory builds secure search.",
                        RelationOrientation.DIRECTED,
                        0.94))));
    }

    @Test
    void rejectsRelationsWhoseEndpointsWereNotExtracted() {
        ExtractedEntity organization = new ExtractedEntity(
                "entity-1",
                "OrgMemory",
                "ORGANIZATION",
                "An enterprise knowledge platform.",
                0.98);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtractionResult(
                        profile,
                        List.of(organization),
                        List.of(new ExtractedRelation(
                                "entity-1",
                                "missing",
                                "BUILDS",
                                List.of("product"),
                                "An unresolved relation.",
                                RelationOrientation.DIRECTED,
                                0.7))));
    }
}
