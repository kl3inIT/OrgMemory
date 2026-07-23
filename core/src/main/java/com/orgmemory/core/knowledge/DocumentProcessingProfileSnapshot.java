package com.orgmemory.core.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Persistable canonical processing profile and its integrity hash. */
public record DocumentProcessingProfileSnapshot(String canonicalForm, String sha256) {

    public DocumentProcessingProfileSnapshot {
        canonicalForm = Objects.requireNonNull(canonicalForm, "canonicalForm");
        if (canonicalForm.isBlank()) {
            throw new IllegalArgumentException("processing profile canonical form must not be blank");
        }
        String calculated = hash(canonicalForm);
        if (!calculated.equals(Objects.requireNonNull(sha256, "sha256"))) {
            throw new IllegalArgumentException("processing profile hash does not match its content");
        }
    }

    public static DocumentProcessingProfileSnapshot from(String canonicalForm) {
        return new DocumentProcessingProfileSnapshot(canonicalForm, hash(canonicalForm));
    }

    static DocumentProcessingProfileSnapshot legacy(
            String parserVersion,
            String chunkerVersion,
            String sourceSha256) {
        return from("parser.actual="
                + Objects.requireNonNull(parserVersion, "parserVersion")
                + "\nchunker.actual="
                + Objects.requireNonNull(chunkerVersion, "chunkerVersion")
                + "\ncanonicalTextSha256="
                + Objects.requireNonNull(sourceSha256, "sourceSha256")
                + "\n");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
