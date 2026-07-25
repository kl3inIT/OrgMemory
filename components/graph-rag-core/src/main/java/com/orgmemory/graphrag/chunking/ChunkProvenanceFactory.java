package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ChunkProvenanceFactory {

    private ChunkProvenanceFactory() {
    }

    static ChunkProvenance create(CanonicalDocument document, int startChar, int endChar) {
        List<DocumentBlock> covered = document.blocks().stream()
                .filter(block -> block.endChar() > startChar && block.startChar() < endChar)
                .toList();
        List<Integer> indexes = covered.stream().map(DocumentBlock::index).toList();
        Integer startPage = covered.stream()
                .map(DocumentBlock::startPage)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Integer endPage = covered.stream()
                .map(DocumentBlock::endPage)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ChunkProvenance(
                startChar,
                endChar,
                startPage,
                endPage,
                new ArrayList<>(indexes),
                document.contentSha256());
    }

    static SourceSpan trim(String source, int start, int end) {
        int left = Math.max(0, start);
        int right = Math.min(source.length(), end);
        while (left < right && Character.isWhitespace(source.charAt(left))) {
            left++;
        }
        while (right > left && Character.isWhitespace(source.charAt(right - 1))) {
            right--;
        }
        return left >= right ? null : new SourceSpan(left, right);
    }
}
