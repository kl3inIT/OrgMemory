package com.orgmemory.graphrag.processing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable, fully resolved processing profile persisted with a document revision.
 *
 * <p>The requested and actual chunker differ only when a profile explicitly permits a
 * deterministic fallback. The profile hash is therefore safe to use as an idempotency input.
 */
public record ResolvedDocumentProcessingProfile(
        ProcessingComponentRef requestedParser,
        ProcessingComponentRef actualParser,
        ProcessingComponentRef requestedChunker,
        ProcessingComponentRef actualChunker,
        ProcessingComponentRef tokenizer,
        Optional<ProcessingComponentRef> semanticEmbedding,
        Map<String, String> options,
        String canonicalTextSha256,
        String profileSha256) {

    public ResolvedDocumentProcessingProfile {
        Objects.requireNonNull(requestedParser, "requestedParser");
        Objects.requireNonNull(actualParser, "actualParser");
        Objects.requireNonNull(requestedChunker, "requestedChunker");
        Objects.requireNonNull(actualChunker, "actualChunker");
        Objects.requireNonNull(tokenizer, "tokenizer");
        semanticEmbedding = Objects.requireNonNull(semanticEmbedding, "semanticEmbedding");
        options = Map.copyOf(new TreeMap<>(Objects.requireNonNull(options, "options")));
        canonicalTextSha256 = requireSha256(canonicalTextSha256, "canonicalTextSha256");
        profileSha256 = requireSha256(profileSha256, "profileSha256");
        String expected = sha256(canonicalForm(
                requestedParser,
                actualParser,
                requestedChunker,
                actualChunker,
                tokenizer,
                semanticEmbedding,
                options,
                canonicalTextSha256));
        if (!expected.equals(profileSha256)) {
            throw new IllegalArgumentException(
                    "profileSha256 does not match the canonical processing profile");
        }
    }

    public static ResolvedDocumentProcessingProfile resolve(
            ProcessingComponentRef requestedParser,
            ProcessingComponentRef actualParser,
            ProcessingComponentRef requestedChunker,
            ProcessingComponentRef actualChunker,
            ProcessingComponentRef tokenizer,
            Optional<ProcessingComponentRef> semanticEmbedding,
            Map<String, String> options,
            String canonicalTextSha256) {
        var sorted = new TreeMap<>(Objects.requireNonNull(options, "options"));
        String canonical = canonicalForm(
                requestedParser,
                actualParser,
                requestedChunker,
                actualChunker,
                tokenizer,
                semanticEmbedding,
                sorted,
                canonicalTextSha256);
        return new ResolvedDocumentProcessingProfile(
                requestedParser,
                actualParser,
                requestedChunker,
                actualChunker,
                tokenizer,
                semanticEmbedding,
                sorted,
                canonicalTextSha256,
                sha256(canonical));
    }

    public String canonicalForm() {
        return canonicalForm(
                requestedParser,
                actualParser,
                requestedChunker,
                actualChunker,
                tokenizer,
                semanticEmbedding,
                options,
                canonicalTextSha256);
    }

    private static String canonicalForm(
            ProcessingComponentRef requestedParser,
            ProcessingComponentRef actualParser,
            ProcessingComponentRef requestedChunker,
            ProcessingComponentRef actualChunker,
            ProcessingComponentRef tokenizer,
            Optional<ProcessingComponentRef> semanticEmbedding,
            Map<String, String> options,
            String canonicalTextSha256) {
        StringBuilder value = new StringBuilder()
                .append("parser.requested=").append(requestedParser).append('\n')
                .append("parser.actual=").append(actualParser).append('\n')
                .append("chunker.requested=").append(requestedChunker).append('\n')
                .append("chunker.actual=").append(actualChunker).append('\n')
                .append("tokenizer=").append(tokenizer).append('\n')
                .append("semanticEmbedding=")
                .append(semanticEmbedding.map(ProcessingComponentRef::toString).orElse(""))
                .append('\n')
                .append("canonicalTextSha256=").append(canonicalTextSha256).append('\n');
        new TreeMap<>(options).forEach((key, option) -> value
                .append("option.")
                .append(escape(key))
                .append('=')
                .append(escape(option))
                .append('\n'));
        return value.toString();
    }

    private static String escape(String value) {
        return Objects.requireNonNull(value, "profile option")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("=", "\\=");
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireSha256(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return normalized;
    }
}
