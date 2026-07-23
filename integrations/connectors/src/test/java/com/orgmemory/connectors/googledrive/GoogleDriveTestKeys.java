package com.orgmemory.connectors.googledrive;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * A service account key generated for the test, so the signing path is exercised for real
 * without a credential from anywhere real existing in the repository.
 *
 * <p>Generated once per JVM: RSA key generation is the slowest thing in these suites by an order
 * of magnitude, and every test wants the same key.
 */
final class GoogleDriveTestKeys {

    private static final String KEY_JSON = generate();

    private GoogleDriveTestKeys() {
    }

    static String serviceAccountKeyJson() {
        return KEY_JSON;
    }

    private static String generate() {
        KeyPair pair = keyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        return """
                {
                  "type": "service_account",
                  "project_id": "orgmemory-test",
                  "client_email": "crawler@orgmemory-test.iam.gserviceaccount.com",
                  "token_uri": "https://oauth2.googleapis.com/token",
                  "private_key": "%s"
                }
                """.formatted(pem);
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("RSA is required by the platform", impossible);
        }
    }
}
