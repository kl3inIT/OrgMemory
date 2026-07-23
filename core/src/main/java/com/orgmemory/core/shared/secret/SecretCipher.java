package com.orgmemory.core.shared.secret;

import java.util.Base64;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets for storage, using AES-256-GCM with a random initialisation vector per
 * value.
 *
 * <p>The algorithm is authenticated on purpose. Unauthenticated CBC — which is what Onyx uses
 * here — keeps the value secret but leaves the ciphertext malleable, so anyone who can write to
 * the row can alter what decrypts out of it without the read path noticing. GCM makes that
 * tampering a decryption failure.
 *
 * <p>There is no path that stores a secret unencrypted. Onyx falls back to plaintext when no key
 * is configured, which is the failure mode where an operator believes their tokens are encrypted
 * and they are not; here an unconfigured key is a refusal to store.
 */
@Component
public class SecretCipher {

    private final SecretCipherProperties properties;
    private final BytesEncryptor encryptor;

    SecretCipher(SecretCipherProperties properties) {
        this.properties = properties;
        this.encryptor = properties.hasKey()
                ? Encryptors.stronger(properties.key(), properties.salt())
                : null;
    }

    /** Whether this deployment can store secrets at all. */
    public boolean isConfigured() {
        return encryptor != null;
    }

    /** @throws SecretsUnavailableException when no encryption key is configured */
    public EncryptedSecret encrypt(SecretValue secret) {
        requireConfigured();
        byte[] cipherText = encryptor.encrypt(secret.expose().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new EncryptedSecret(Base64.getEncoder().encodeToString(cipherText), properties.version());
    }

    /**
     * @throws SecretsUnavailableException when no key is configured
     * @throws SecretUndecipherableException when the stored value does not decrypt under the
     *     current key, which is what a rotated key or a tampered row looks like
     */
    public SecretValue decrypt(EncryptedSecret stored) {
        requireConfigured();
        try {
            byte[] plain = encryptor.decrypt(Base64.getDecoder().decode(stored.cipherText()));
            return SecretValue.of(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (RuntimeException failure) {
            // The cause can carry ciphertext fragments; the message deliberately does not.
            throw new SecretUndecipherableException(
                    "A stored secret could not be decrypted with the current key (version "
                            + stored.keyVersion() + ")");
        }
    }

    private void requireConfigured() {
        if (encryptor == null) {
            throw new SecretsUnavailableException(
                    "No encryption key is configured, so secrets cannot be stored or read");
        }
    }
}
