package com.orgmemory.graphrag.chunking;

import java.util.List;
import java.util.Objects;

public record ChunkProvenance(
        int startChar,
        int endChar,
        Integer startPage,
        Integer endPage,
        List<Integer> blockIndexes,
        String canonicalTextSha256) {

    public ChunkProvenance {
        if (startChar < 0 || endChar <= startChar) {
            throw new IllegalArgumentException("chunk source span must be non-empty");
        }
        if (startPage != null && startPage <= 0
                || endPage != null && endPage <= 0
                || startPage != null && endPage != null && endPage < startPage) {
            throw new IllegalArgumentException("chunk page range is invalid");
        }
        blockIndexes = List.copyOf(Objects.requireNonNull(blockIndexes, "blockIndexes"));
        int previous = -1;
        for (Integer blockIndex : blockIndexes) {
            if (blockIndex == null || blockIndex < 0 || blockIndex <= previous) {
                throw new IllegalArgumentException(
                        "chunk block indexes must be ordered, unique, and non-negative");
            }
            previous = blockIndex;
        }
        if (!Objects.requireNonNull(canonicalTextSha256, "canonicalTextSha256")
                .matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("canonicalTextSha256 must be a lowercase SHA-256");
        }
    }
}
