package com.orgmemory.core.shared.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * The properties that matter for a token at rest: it survives a round trip, it is not readable
 * in the row, tampering is caught rather than decrypted, a wrong key fails closed, and an
 * unconfigured deployment refuses to store rather than storing plaintext.
 */
class SecretCipherTests {

    private static final String TOKEN = "xoxb-not-a-real-token-0123456789";

    @Test
    void survivesARoundTrip() {
        SecretCipher cipher = configured("a-long-enough-encryption-key", 1);

        EncryptedSecret stored = cipher.encrypt(SecretValue.of(TOKEN));

        assertEquals(TOKEN, cipher.decrypt(stored).expose());
        assertEquals(1, stored.keyVersion());
    }

    @Test
    void leavesNothingReadableInTheStoredValue() {
        SecretCipher cipher = configured("a-long-enough-encryption-key", 1);

        EncryptedSecret stored = cipher.encrypt(SecretValue.of(TOKEN));

        assertFalse(stored.cipherText().contains("xoxb"), "the row must not carry the token");
        assertFalse(stored.cipherText().contains(TOKEN));
    }

    @Test
    void encryptsTheSameSecretDifferentlyEveryTime() {
        SecretCipher cipher = configured("a-long-enough-encryption-key", 1);

        assertNotEquals(
                cipher.encrypt(SecretValue.of(TOKEN)).cipherText(),
                cipher.encrypt(SecretValue.of(TOKEN)).cipherText(),
                "a fresh initialisation vector per value stops equal secrets looking equal");
    }

    @Test
    void refusesATamperedRowRatherThanDecryptingIt() {
        SecretCipher cipher = configured("a-long-enough-encryption-key", 1);
        EncryptedSecret stored = cipher.encrypt(SecretValue.of(TOKEN));
        byte[] raw = Base64.getDecoder().decode(stored.cipherText());
        raw[raw.length - 1] ^= 0x01;
        EncryptedSecret tampered = new EncryptedSecret(Base64.getEncoder().encodeToString(raw), 1);

        assertThrows(SecretUndecipherableException.class, () -> cipher.decrypt(tampered),
                "authenticated encryption is what turns tampering into a failure");
    }

    @Test
    void failsClosedUnderTheWrongKey() {
        EncryptedSecret stored = configured("the-original-encryption-key", 1).encrypt(SecretValue.of(TOKEN));

        SecretUndecipherableException refused = assertThrows(
                SecretUndecipherableException.class,
                () -> configured("a-different-encryption-key", 2).decrypt(stored));

        assertFalse(refused.getMessage().contains(TOKEN), "a failure must not carry the secret");
    }

    @Test
    void refusesToStoreAnythingWithoutAKey() {
        SecretCipher unconfigured = new SecretCipher(new SecretCipherProperties("", null, null));

        assertFalse(unconfigured.isConfigured());
        assertThrows(
                SecretsUnavailableException.class,
                () -> unconfigured.encrypt(SecretValue.of(TOKEN)),
                "storing plaintext when the key is missing is the failure nobody notices");
    }

    @Test
    void keepsTheSecretOutOfEveryStringItAppearsIn() {
        SecretValue secret = SecretValue.of(TOKEN);

        assertEquals("<redacted>", secret.toString());
        assertFalse(("token=" + secret).contains(TOKEN), "concatenation is the usual accident");
        assertTrue(secret.equals(SecretValue.of(TOKEN)));
        assertFalse(secret.equals(SecretValue.of("xoxb-something-else-entirely-000")));
    }

    @Test
    void rejectsAnAbsentSecret() {
        assertThrows(IllegalArgumentException.class, () -> SecretValue.of(null));
        assertThrows(IllegalArgumentException.class, () -> SecretValue.of("  "));
    }

    private static SecretCipher configured(String key, int version) {
        return new SecretCipher(new SecretCipherProperties(key, null, version));
    }
}
