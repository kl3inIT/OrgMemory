package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeChunkDraftAssemblerTests {

    @Test
    void assemblesOrderedDraftsWithProvenanceAndHashes() {
        var chunks = List.of(
                new KnowledgeTextChunk("First chunk", 1, 2),
                new KnowledgeTextChunk("Second chunk", 3, 4));
        var vectors = List.of(new float[] {1, 2, 3}, new float[] {4, 5, 6});

        List<KnowledgeChunkDraft> drafts =
                KnowledgeChunkDraftAssembler.assemble(chunks, vectors, 3);

        assertEquals(2, drafts.size());
        assertEquals(0, drafts.getFirst().index());
        assertEquals("First chunk", drafts.getFirst().content());
        assertEquals(
                "d4c3ad047de841268dfa59da3a61873c491bb56fdf52d65e6aa45ece3c70d972",
                drafts.getFirst().contentSha256());
        assertEquals(1, drafts.getFirst().startPage());
        assertEquals(2, drafts.getFirst().endPage());
        assertArrayEquals(new float[] {1, 2, 3}, drafts.getFirst().embedding());
        assertEquals(1, drafts.get(1).index());
    }

    @Test
    void rejectsMismatchedEmbeddingCount() {
        var chunks = List.of(new KnowledgeTextChunk("Only chunk", null, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeChunkDraftAssembler.assemble(chunks, List.of(), 3));
    }

    @Test
    void rejectsEmbeddingWithWrongDimensions() {
        var chunks = List.of(new KnowledgeTextChunk("Only chunk", null, null));

        assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeChunkDraftAssembler.assemble(
                        chunks, List.of(new float[] {1, 2}), 3));
    }
}
