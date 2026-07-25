package com.orgmemory.graphrag.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LightRagEmbeddingPayloadsTests {

    @Test
    void matchesPinnedEntityEmbeddingPayload() {
        assertEquals(
                "orgmemory\nPermission-aware enterprise knowledge.",
                LightRagEmbeddingPayloads.entity(
                        "orgmemory",
                        "Permission-aware enterprise knowledge."));
    }

    @Test
    void matchesPinnedRelationEmbeddingPayload() {
        assertEquals(
                "secure search, retrieval\torgmemory\nopenfga\n"
                        + "OrgMemory uses OpenFGA for relationship authorization.",
                LightRagEmbeddingPayloads.relation(
                        List.of("secure search", "retrieval"),
                        "orgmemory",
                        "openfga",
                        "OrgMemory uses OpenFGA for relationship authorization."));
    }
}
