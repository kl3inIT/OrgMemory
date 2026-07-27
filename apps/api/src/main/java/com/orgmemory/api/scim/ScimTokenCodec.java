package com.orgmemory.api.scim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class ScimTokenCodec {

    private static final String PREFIX = "omscim_";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, byte[]> verifierKeys;
    private final int keyVersion;

    ScimTokenCodec(ScimSecurityProperties properties) {
        verifierKeys = properties.decodedVerifierKeys();
        keyVersion = properties.currentKeyVersion();
    }

    IssuedToken issue() {
        byte[] publicBytes = new byte[12];
        byte[] secretBytes = new byte[32];
        random.nextBytes(publicBytes);
        random.nextBytes(secretBytes);
        String publicId = ENCODER.encodeToString(publicBytes);
        String secret = ENCODER.encodeToString(secretBytes);
        return new IssuedToken(
                PREFIX + publicId + "." + secret,
                publicId,
                digest(secret, verifierKeys.get(keyVersion)),
                keyVersion);
    }

    ParsedToken parse(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Malformed SCIM credential");
        }
        int separator = rawToken.indexOf('.', PREFIX.length());
        if (separator < 0
                || separator != PREFIX.length() + 16
                || rawToken.length() != separator + 1 + 43) {
            throw new IllegalArgumentException("Malformed SCIM credential");
        }
        String publicId = rawToken.substring(PREFIX.length(), separator);
        String secret = rawToken.substring(separator + 1);
        if (!publicId.matches("[A-Za-z0-9_-]{16}")
                || !secret.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Malformed SCIM credential");
        }
        return new ParsedToken(publicId, secret);
    }

    boolean matches(String secret, String expectedDigest, int verifierKeyVersion) {
        byte[] verifierKey = verifierKeys.get(verifierKeyVersion);
        if (verifierKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                digest(secret, verifierKey).getBytes(StandardCharsets.US_ASCII),
                expectedDigest.getBytes(StandardCharsets.US_ASCII));
    }

    private static String digest(String secret, byte[] verifierKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(verifierKey, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        }
    }

    record IssuedToken(String rawToken, String publicId, String verifierDigest, int keyVersion) {
    }

    record ParsedToken(String publicId, String secret) {
    }
}
