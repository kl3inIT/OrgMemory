package com.orgmemory.graphrag.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical, length-delimited cache identity.
 *
 * <p>Length prefixes prevent field-boundary collisions and sorted field names
 * make the result independent of map iteration order. Callers must never add
 * credentials or other secrets to the input.
 */
public final class CanonicalCacheKeyHasher {

    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");

    private CanonicalCacheKeyHasher() {
    }

    public static String sha256(String domain, Map<String, String> fields) {
        String normalizedDomain = requireText(domain, "domain");
        Objects.requireNonNull(fields, "fields");
        MessageDigest digest = sha256Digest();
        append(digest, "domain");
        append(digest, normalizedDomain);
        fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> {
                    append(digest, requireText(entry.getKey(), "field name"));
                    append(digest, Objects.requireNonNull(entry.getValue(), entry.getKey()));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String requireSha256(String value, String field) {
        String normalized = requireText(value, field);
        if (!LOWERCASE_SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase SHA-256 hex digest");
        }
        return normalized;
    }

    private static void append(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) ';');
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
