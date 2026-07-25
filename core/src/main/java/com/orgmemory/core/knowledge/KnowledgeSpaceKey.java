package com.orgmemory.core.knowledge;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * The stable identifier derived from a Knowledge Space name.
 *
 * <p>{@code space_key} is {@code updatable = false}: it is what a URL, an export and an operator's
 * notes refer to, so it outlives renames on purpose. Deriving it rather than asking for it keeps
 * an administrator from having to invent a second name, and keeps a later rename from leaving a
 * key that describes the space's former purpose — the key was never the display name to begin
 * with.
 */
final class KnowledgeSpaceKey {

    /** {@code knowledge_spaces.space_key} is {@code varchar(128)}. */
    private static final int MAXIMUM_LENGTH = 128;

    private KnowledgeSpaceKey() {
    }

    static String from(String name) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(name, "name"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.length() > MAXIMUM_LENGTH) {
            normalized = normalized.substring(0, MAXIMUM_LENGTH).replaceAll("-+$", "");
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "A Knowledge Space name must contain at least one letter or digit");
        }
        return normalized;
    }
}
