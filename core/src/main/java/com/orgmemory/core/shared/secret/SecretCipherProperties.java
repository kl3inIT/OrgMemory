package com.orgmemory.core.shared.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The key material used to encrypt secrets at rest. Supplied through the environment beside
 * the database password rather than committed anywhere.
 *
 * @param key     the encryption key; absent means secrets cannot be stored at all
 * @param salt    hex-encoded salt for key derivation, fixed per deployment
 * @param version stamped on every ciphertext so a rotation can find what it has yet to re-encrypt
 */
@ConfigurationProperties("orgmemory.secrets")
public record SecretCipherProperties(String key, String salt, Integer version) {

    /** A recognisable non-secret default so a misconfigured deployment fails on the key, not this. */
    private static final String DEFAULT_SALT = "6f72676d656d6f7279736563726574";

    public SecretCipherProperties {
        key = key == null ? "" : key.strip();
        salt = salt == null || salt.isBlank() ? DEFAULT_SALT : salt.strip();
        version = version == null || version < 1 ? 1 : version;
    }

    boolean hasKey() {
        return !key.isBlank();
    }
}
