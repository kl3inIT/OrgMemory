package com.orgmemory.connectors.github;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
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
        return jsonWithKey(pem());
    }

    static String jsonWithKey(String pem) {
        ObjectNode credential = MAPPER.createObjectNode();
        credential.put("appId", "12345");
        credential.put("installationId", 67890);
        credential.put("privateKey", pem);
        return MAPPER.writeValueAsString(credential);
    }

    static String pem() {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(KEY_PAIR.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }

    static String pkcs1Pem() {
        byte[] encoded = pkcs1Bytes(KEY_PAIR.getPrivate().getEncoded());
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN RSA PRIVATE KEY-----\n"
                + base64
                + "\n-----END RSA PRIVATE KEY-----";
    }

    static PublicKey publicKey() {
        return KEY_PAIR.getPublic();
    }

    /** Extracts the PKCS#1 octet string from the generated PKCS#8 PrivateKeyInfo. */
    private static byte[] pkcs1Bytes(byte[] pkcs8) {
        int[] offset = {0};
        expectTag(pkcs8, offset, 0x30);
        readLength(pkcs8, offset);
        expectTag(pkcs8, offset, 0x02);
        int versionLength = readLength(pkcs8, offset);
        offset[0] += versionLength;
        expectTag(pkcs8, offset, 0x30);
        int algorithmLength = readLength(pkcs8, offset);
        offset[0] += algorithmLength;
        expectTag(pkcs8, offset, 0x04);
        int length = readLength(pkcs8, offset);
        return Arrays.copyOfRange(pkcs8, offset[0], offset[0] + length);
    }

    private static void expectTag(byte[] der, int[] offset, int expected) {
        if (offset[0] >= der.length || Byte.toUnsignedInt(der[offset[0]++]) != expected) {
            throw new IllegalStateException("generated RSA key has an unexpected DER shape");
        }
    }

    private static int readLength(byte[] der, int[] offset) {
        int first = Byte.toUnsignedInt(der[offset[0]++]);
        if ((first & 0x80) == 0) {
            return first;
        }
        int count = first & 0x7f;
        int length = 0;
        for (int index = 0; index < count; index++) {
            length = (length << 8) | Byte.toUnsignedInt(der[offset[0]++]);
        }
        return length;
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
