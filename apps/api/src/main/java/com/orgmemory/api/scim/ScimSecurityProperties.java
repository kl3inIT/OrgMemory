package com.orgmemory.api.scim;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("orgmemory.security.scim")
public record ScimSecurityProperties(
        String verifierKey,
        int currentKeyVersion,
        String previousVerifierKeys,
        boolean requireTls,
        DataSize maximumRequestSize,
        int requestsPerMinute,
        Duration tokenTtl,
        Duration rotationOverlap) {

    @ConstructorBinding
    public ScimSecurityProperties {
        Assert.hasText(verifierKey, "orgmemory.security.scim.verifier-key is required");
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(verifierKey);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "orgmemory.security.scim.verifier-key must be unpadded base64url", invalid);
        }
        Assert.isTrue(decoded.length >= 32, "SCIM verifier key must contain at least 256 bits");
        Assert.isTrue(currentKeyVersion > 0, "SCIM verifier key version must be positive");
        previousVerifierKeys =
                previousVerifierKeys == null ? "" : previousVerifierKeys.trim();
        decodedVerifierKeys(verifierKey, currentKeyVersion, previousVerifierKeys);
        maximumRequestSize = maximumRequestSize == null
                ? DataSize.ofKilobytes(256)
                : maximumRequestSize;
        Assert.isTrue(maximumRequestSize.toBytes() > 0, "SCIM request size must be positive");
        requestsPerMinute = requestsPerMinute <= 0 ? 120 : requestsPerMinute;
        tokenTtl = tokenTtl == null ? Duration.ofDays(90) : tokenTtl;
        rotationOverlap = rotationOverlap == null ? Duration.ofMinutes(15) : rotationOverlap;
        Assert.isTrue(!tokenTtl.isNegative() && !tokenTtl.isZero(), "SCIM token TTL must be positive");
        Assert.isTrue(
                !rotationOverlap.isNegative() && rotationOverlap.compareTo(Duration.ofHours(24)) <= 0,
                "SCIM rotation overlap must be between zero and 24 hours");
    }

    public Map<Integer, byte[]> decodedVerifierKeys() {
        return decodedVerifierKeys(verifierKey, currentKeyVersion, previousVerifierKeys);
    }

    private static Map<Integer, byte[]> decodedVerifierKeys(
            String currentKey, int currentVersion, String previousKeys) {
        Map<Integer, byte[]> decoded = new LinkedHashMap<>();
        decoded.put(currentVersion, decodeVerifierKey(currentKey));
        if (previousKeys.isBlank()) {
            return Map.copyOf(decoded);
        }
        for (String entry : previousKeys.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "SCIM previous verifier keys must use version=base64url entries");
            }
            int version;
            try {
                version = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                        "SCIM previous verifier key versions must be positive integers",
                        invalid);
            }
            Assert.isTrue(version > 0, "SCIM previous verifier key versions must be positive");
            if (decoded.putIfAbsent(version, decodeVerifierKey(parts[1].trim())) != null) {
                throw new IllegalArgumentException(
                        "SCIM verifier key versions must not be repeated");
            }
        }
        return Map.copyOf(decoded);
    }

    private static byte[] decodeVerifierKey(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            Assert.isTrue(
                    decoded.length >= 32,
                    "Every SCIM verifier key must contain at least 256 bits");
            return decoded;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "SCIM verifier keys must be unpadded base64url with at least 256 bits",
                    invalid);
        }
    }
}
