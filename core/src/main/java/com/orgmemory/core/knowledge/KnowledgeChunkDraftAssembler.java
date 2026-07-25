package com.orgmemory.core.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Converts ordered text chunks and their embeddings into immutable publication drafts.
 */
public final class KnowledgeChunkDraftAssembler {

    private KnowledgeChunkDraftAssembler() {
    }

    public static List<KnowledgeChunkDraft> assemble(
            List<KnowledgeTextChunk> chunks,
            List<float[]> vectors,
            int expectedDimensions) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(vectors, "vectors");
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("embedding count does not match chunk count");
        }
        if (expectedDimensions <= 0) {
            throw new IllegalArgumentException("expected embedding dimensions must be positive");
        }

        List<KnowledgeChunkDraft> drafts = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeTextChunk chunk = Objects.requireNonNull(chunks.get(index), "chunk");
            float[] vector = Objects.requireNonNull(vectors.get(index), "embedding vector");
            if (vector.length != expectedDimensions) {
                throw new IllegalArgumentException(
                        "embedding dimensions do not match the configured profile");
            }
            drafts.add(new KnowledgeChunkDraft(
                    index,
                    chunk.content(),
                    sha256(chunk.content()),
                    chunk.tokenCount(),
                    chunk.startPage(),
                    chunk.endPage(),
                    chunk.heading(),
                    chunk.startChar(),
                    chunk.endChar(),
                    chunk.sourceBlockIndexes(),
                    chunk.canonicalTextSha256(),
                    vector));
        }
        return List.copyOf(drafts);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
