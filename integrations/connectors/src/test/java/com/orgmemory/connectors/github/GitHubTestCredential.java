package com.orgmemory.connectors.github;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** One generated, non-production RSA key used only to sign deterministic-shape test requests. */
final class GitHubTestCredential {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final KeyPair KEY_PAIR = generate();

    private GitHubTestCredential() {
    }

    static String json() {
        ObjectNode credential = MAPPER.createObjectNode();
        credential.put("appId", "12345");
        credential.put("installationId", 67890);
        credential.put("privateKey", pem());
        return MAPPER.writeValueAsString(credential);
    }

    static String pem() {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(KEY_PAIR.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new ExceptionInInitializerError(unavailable);
        }
    }
}

