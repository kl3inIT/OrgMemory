package com.orgmemory.api.scim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ScimTokenCodecTests {

    @Test
    void acceptsABoundedPreviousVerifierKeyDuringDeploymentRotation() {
        String oldKey = encodedKey((byte) 1);
        var original = new ScimTokenCodec(properties(oldKey, 1, ""));
        var issued = original.issue();

        String newKey = encodedKey((byte) 2);
        var rotated = new ScimTokenCodec(properties(newKey, 2, "1=" + oldKey));

        assertTrue(rotated.matches(
                original.parse(issued.rawToken()).secret(),
                issued.verifierDigest(),
                issued.keyVersion()));
        assertFalse(new ScimTokenCodec(properties(newKey, 2, "")).matches(
                original.parse(issued.rawToken()).secret(),
                issued.verifierDigest(),
                issued.keyVersion()));
    }

    private static ScimSecurityProperties properties(
            String currentKey, int currentVersion, String previousKeys) {
        return new ScimSecurityProperties(
                currentKey,
                currentVersion,
                previousKeys,
                false,
                DataSize.ofKilobytes(256),
                120,
                Duration.ofDays(90),
                Duration.ofMinutes(15));
    }

    private static String encodedKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }
}
