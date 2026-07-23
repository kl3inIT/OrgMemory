package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.List;
import java.util.Objects;

/** Canonical normalized text and typed block IR shared by parsers and chunkers. */
public record CanonicalDocument(String content, String contentSha256, List<DocumentBlock> blocks) {

    public CanonicalDocument {
        content = Objects.requireNonNull(content, "content");
        if (content.isBlank()) {
            throw new IllegalArgumentException("canonical document content must not be blank");
        }
        String computed = ResolvedDocumentProcessingProfile.sha256(content);
        if (!computed.equals(Objects.requireNonNull(contentSha256, "contentSha256"))) {
            throw new IllegalArgumentException("canonical document hash does not match its content");
        }
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        int previousEnd = -1;
        for (int index = 0; index < blocks.size(); index++) {
            DocumentBlock block = Objects.requireNonNull(blocks.get(index), "block");
            if (block.index() != index) {
                throw new IllegalArgumentException("block indexes must be contiguous and ordered");
            }
            if (block.endChar() > content.length() || block.startChar() < previousEnd) {
                throw new IllegalArgumentException(
                        "block spans must be ordered, non-overlapping, and inside canonical text");
            }
            previousEnd = block.endChar();
        }
    }

    public static CanonicalDocument text(String content) {
        String normalized = Objects.requireNonNull(content, "content")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("canonical document content must not be blank");
        }
        return new CanonicalDocument(
                normalized,
                ResolvedDocumentProcessingProfile.sha256(normalized),
                List.of(new DocumentBlock(
                        0,
                        DocumentBlockKind.PARAGRAPH,
                        0,
                        normalized.length(),
                        null,
                        null,
                        null,
                        java.util.Map.of())));
    }

    public String text(DocumentBlock block) {
        Objects.requireNonNull(block, "block");
        return content.substring(block.startChar(), block.endChar());
    }

    public boolean hasStructuredBlocks() {
        return blocks.size() > 1
                || blocks.stream().anyMatch(block -> block.kind() != DocumentBlockKind.PARAGRAPH)
                || blocks.stream().anyMatch(block -> "true".equals(block.attributes().get("structured")));
    }
}
