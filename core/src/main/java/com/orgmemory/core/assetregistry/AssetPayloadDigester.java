package com.orgmemory.core.assetregistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
class AssetPayloadDigester {

    private final ObjectMapper json = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    CanonicalAssetPayload canonicalize(
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload) {
        Object payloadValue;
        try {
            payloadValue = json.readValue(Objects.requireNonNull(payload, "payload"), Object.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Asset payload must be valid JSON", exception);
        }
        if (!(payloadValue instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Asset payload must be a JSON object");
        }

        String canonicalPayload = json.writeValueAsString(payloadValue);
        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("classification", Objects.requireNonNull(classification, "classification").trim());
        envelope.put("payload", payloadValue);
        envelope.put("schemaVersion", Objects.requireNonNull(schemaVersion, "schemaVersion").trim());
        envelope.put("summary", Objects.requireNonNull(summary, "summary").trim());
        envelope.put("title", Objects.requireNonNull(title, "title").trim());
        return new CanonicalAssetPayload(
                canonicalPayload,
                sha256(json.writeValueAsString(envelope)));
    }

    private static String sha256(String canonicalValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    record CanonicalAssetPayload(String payload, String digest) {
    }
}
