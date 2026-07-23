package com.orgmemory.core.shared.secret;

import java.util.Objects;

/**
 * A value that must not be printed, logged, or serialised by accident.
 *
 * <p>Wrapping the string is what makes exposure deliberate. {@code toString} is redacted, so a
 * log line, a string concatenation, a JSON body, and an exception message all render it as
 * nothing; reading the actual value takes {@link #expose()}, which is named so that a review
 * notices it. Onyx reaches the same end by raising on {@code str()} and serialisation; on the
 * JVM a redacted {@code toString} covers the same paths without making the type awkward to hold.
 *
 * <p>Equality is constant-time, because comparing a supplied secret to a stored one is the
 * obvious use and the obvious implementation leaks its answer through timing.
 */
public final class SecretValue {

    private static final String REDACTED = "<redacted>";

    private final String value;

    private SecretValue(String value) {
        this.value = value;
    }

    /** @throws IllegalArgumentException when the value is absent or blank */
    public static SecretValue of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A secret value is required");
        }
        return new SecretValue(value);
    }

    /** The value itself. Every call site is a decision to let the secret out of the wrapper. */
    public String expose() {
        return value;
    }

    @Override
    public String toString() {
        return REDACTED;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SecretValue secret)) {
            return false;
        }
        byte[] mine = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] theirs = secret.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(mine, theirs);
    }

    @Override
    public int hashCode() {
        // Deliberately coarse: a hash of the secret in a heap dump or a map key is still the
        // secret's fingerprint, and nothing here needs the distribution.
        return Objects.hash(value.length());
    }
}
