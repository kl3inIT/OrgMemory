package com.orgmemory.connectors.github;

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
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException unusable) {
            // Do not chain a provider parse exception: its message may contain input fragments.
            throw new GitHubCredentialException(
                    "The GitHub App private key could not be read", "invalid_key");
        }
    }

    @Override
    public String toString() {
        return "GitHubAppKey[appId=" + appId
                + ", installationId=" + installationId
                + ", privateKey=<redacted>]";
    }
}
