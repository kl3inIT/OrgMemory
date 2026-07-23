package com.orgmemory.connectors.googledrive;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A Google service account key, read out of the JSON file Google hands over.
 *
 * <p>Only the four fields an access token needs are kept. The rest of the document — project
 * id, key id, the console URLs — is not read, so a key that gains fields later still parses.
 *
 * <p>{@code toString} is overridden. A record would print the private key into the first log
 * line that touched this object, and the whole point of the surrounding design is that a
 * credential never reaches a log.
 */
record GoogleServiceAccountKey(String clientEmail, PrivateKey privateKey, String tokenUri) {

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @throws GoogleDriveCredentialException when the document is not a service account key, or
     *         is one whose private key cannot be read — both of which an administrator can fix,
     *         and neither of which should read as "Google refused us"
     */
    static GoogleServiceAccountKey parse(String json) {
        JsonNode document;
        try {
            document = MAPPER.readTree(json);
        } catch (RuntimeException unreadable) {
            throw new GoogleDriveCredentialException("The credential is not readable JSON", "invalid_key");
        }
        if (!document.isObject() || !"service_account".equals(document.path("type").asString(""))) {
            throw new GoogleDriveCredentialException(
                    "The credential is not a Google service account key", "invalid_key");
        }
        String clientEmail = document.path("client_email").asString("");
        String privateKeyPem = document.path("private_key").asString("");
        if (clientEmail.isBlank() || privateKeyPem.isBlank()) {
            throw new GoogleDriveCredentialException(
                    "The service account key is missing client_email or private_key", "invalid_key");
        }
        String tokenUri = document.path("token_uri").asString("");
        return new GoogleServiceAccountKey(
                clientEmail,
                readPrivateKey(privateKeyPem),
                tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri);
    }

    /** PEM PKCS#8, which is what Google writes into the key file. */
    private static PrivateKey readPrivateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException unusable) {
            // Deliberately not chained: the cause's message can carry fragments of the key
            // material it failed to parse, and this exception is on its way to a screen.
            throw new GoogleDriveCredentialException(
                    "The service account key's private key could not be read", "invalid_key");
        }
    }

    @Override
    public String toString() {
        return "GoogleServiceAccountKey[clientEmail=" + clientEmail + ", privateKey=<redacted>]";
    }
}
