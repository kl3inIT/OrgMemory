package com.orgmemory.core.shared.secret;

/**
 * Raised when a stored secret does not decrypt under the current key. Authenticated encryption
 * means this covers both a rotated key and a tampered row; the message names neither the secret
 * nor the ciphertext.
 */
public class SecretUndecipherableException extends RuntimeException {

    public SecretUndecipherableException(String message) {
        super(message);
    }
}
