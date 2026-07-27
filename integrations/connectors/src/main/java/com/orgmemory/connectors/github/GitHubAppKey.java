package com.orgmemory.connectors.github;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The minimum GitHub App installation credential needed by a background connector.
 *
 * <p>A class rather than a record prevents an accidental generated {@code toString} from
 * printing private-key material into a log. The installation id is kept with the key because a
 * GitHub App may be installed in many organizations, while one OrgMemory connection represents
 * exactly one installation account.
 */
final class GitHubAppKey {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String appId;
    private final long installationId;
    private final PrivateKey privateKey;

    private GitHubAppKey(String appId, long installationId, PrivateKey privateKey) {
        this.appId = appId;
        this.installationId = installationId;
        this.privateKey = privateKey;
    }

    static GitHubAppKey parse(String json) {
        JsonNode document;
        try {
            document = MAPPER.readTree(json);
        } catch (RuntimeException unreadable) {
            throw new GitHubCredentialException(
                    "The GitHub App credential is not readable JSON", "invalid_key");
        }
        if (document == null || !document.isObject()) {
            throw new GitHubCredentialException(
                    "The GitHub App credential must be a JSON object", "invalid_key");
        }
        String appId = document.path("appId").asString("").trim();
        long installationId = document.path("installationId").asLong(0);
        String privateKeyPem = document.path("privateKey").asString("");
        if (appId.isBlank() || installationId <= 0 || privateKeyPem.isBlank()) {
            throw new GitHubCredentialException(
                    "The GitHub App credential needs appId, installationId, and privateKey",
                    "invalid_key");
        }
        return new GitHubAppKey(appId, installationId, readPrivateKey(privateKeyPem));
    }

    String appId() {
        return appId;
    }

    long installationId() {
        return installationId;
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    private static PrivateKey readPrivateKey(String pem) {
        boolean pkcs1 = pem.contains("-----BEGIN RSA PRIVATE KEY-----");
        String base64 = pem.replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            if (pkcs1) {
                der = wrapPkcs1AsPkcs8(der);
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException unusable) {
            // Do not chain a provider parse exception: its message may contain input fragments.
            throw new GitHubCredentialException(
                    "The GitHub App private key could not be read", "invalid_key");
        }
    }

    /**
     * Wraps GitHub's downloaded PKCS#1 RSAPrivateKey in the PKCS#8 PrivateKeyInfo envelope
     * accepted by the JCA RSA key factory. The PKCS#1 bytes remain unchanged.
     */
    private static byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        byte[] rsaAlgorithmIdentifier = {
                0x30, 0x0d,
                0x06, 0x09,
                0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] {0x02, 0x01, 0x00});
        body.writeBytes(rsaAlgorithmIdentifier);
        body.write(0x04);
        writeDerLength(body, pkcs1.length);
        body.writeBytes(pkcs1);

        byte[] privateKeyInfo = body.toByteArray();
        ByteArrayOutputStream sequence = new ByteArrayOutputStream();
        sequence.write(0x30);
        writeDerLength(sequence, privateKeyInfo.length);
        sequence.writeBytes(privateKeyInfo);
        return sequence.toByteArray();
    }

    private static void writeDerLength(ByteArrayOutputStream target, int length) {
        if (length < 0x80) {
            target.write(length);
            return;
        }
        int bytes = 0;
        int remaining = length;
        while (remaining > 0) {
            bytes++;
            remaining >>>= 8;
        }
        target.write(0x80 | bytes);
        for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) {
            target.write((length >>> shift) & 0xff);
        }
    }

    @Override
    public String toString() {
        return "GitHubAppKey[appId=" + appId
                + ", installationId=" + installationId
                + ", privateKey=<redacted>]";
    }
}
