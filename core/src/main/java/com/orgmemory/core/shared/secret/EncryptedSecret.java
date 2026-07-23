package com.orgmemory.core.shared.secret;

/**
 * A secret as it is stored: base64 ciphertext and the key version that produced it.
 *
 * <p>The version is recorded rather than inferred. Onyx detects rotation by trial-decrypting
 * every row with the current key and treating failure as "not rotated yet", which cannot tell a
 * stale key from a corrupted row. A version column answers both questions by selection.
 */
public record EncryptedSecret(String cipherText, int keyVersion) {

    public EncryptedSecret {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("Encrypted secret cipher text is required");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("Encrypted secret key version starts at 1");
        }
    }
}
