package com.orgmemory.graphrag.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.model.ExtractedEntity;
import com.orgmemory.graphrag.model.ExtractedRelation;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.RelationOrientation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphContributionAssemblerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ASSET_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REVISION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ACL_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CHUNK_ONE =
            UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID CHUNK_TWO =
            UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final ExtractionProfile PROFILE =
            new ExtractionProfile("openai", "gpt-5.6-sol", "prompt-v1", 20, 20);

    @Test
    void replaysDeterministicallyAndKeepsDescriptionsPerEvidenceChunk() {
        List<ExtractedChunk> chunks = List.of(
                new ExtractedChunk(CHUNK_TWO, result(" orgmemory ", "Second description")),
                new ExtractedChunk(CHUNK_ONE, result("ＯｒｇＭｅｍｏｒｙ", "First description")));

        var first = assemble(chunks);
        var replay = assemble(chunks.reversed());

        assertEquals(first, replay);
        assertEquals(4, first.entities().size());
        assertEquals(2, first.relations().size());
        assertEquals(
                first.entities().get(0).entity().id(),
                first.entities().stream()
                        .filter(contribution -> contribution.provenance().chunkId().equals(CHUNK_TWO))
                        .filter(contribution -> contribution.entity().normalizedName().equals("orgmemory"))
                        .findFirst()
                        .orElseThrow()
                        .entity()
                        .id());
        assertNotEquals(
                first.entities().get(0).provenance().chunkId(),
                first.entities().get(2).provenance().chunkId());
    }

    @Test
    void rejectsMixedExtractionProfilesWithinOneRevision() {
        var other = new ExtractionProfile("openai", "other-model", "prompt-v1", 20, 20);
        var mixed = new ExtractedChunk(
                CHUNK_TWO,
                new ExtractionResult(
                        other,
                        List.of(new ExtractedEntity("a", "A", "TYPE", "A", 1.0)),
                        List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> assemble(List.of(new ExtractedChunk(CHUNK_ONE, result("A", "A")), mixed)));
    }

    private static com.orgmemory.graphrag.port.GraphRevisionContributions assemble(
            List<ExtractedChunk> chunks) {
        return GraphContributionAssembler.assemble(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                ACL_ID,
                3,
                7,
                Instant.parse("2026-07-23T12:00:00Z"),
                chunks);
    }

    private static ExtractionResult result(String productName, String description) {
        return new ExtractionResult(
                PROFILE,
                List.of(
                        new ExtractedEntity("product", productName, "Product", description, 0.9),
                        new ExtractedEntity(
                                "capability", "Secure Search", "Capability", "Permission-aware search", 0.8)),
                List.of(new ExtractedRelation(
                        "product",
                        "capability",
                        "provides",
                        List.of("security", "search"),
                        "The product provides secure search",
                        RelationOrientation.DIRECTED,
                        0.85)));
    }
}
