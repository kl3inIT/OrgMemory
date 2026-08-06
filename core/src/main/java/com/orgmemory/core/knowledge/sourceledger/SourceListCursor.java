package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

record SourceListCursor(Instant updatedAt, UUID sourceId) {

    SourceListCursor {
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(sourceId, "sourceId");
    }

    static String encode(Instant updatedAt, UUID sourceId) {
        String value = updatedAt + "|" + sourceId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static SourceListCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException("cursor fields are missing");
            }
            return new SourceListCursor(
                    Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1)));
        } catch (IllegalArgumentException invalid) {
            throw new InvalidSourceListCursorException(invalid);
        }
    }

    private static final class InvalidSourceListCursorException extends BusinessException {

        private InvalidSourceListCursorException(Throwable cause) {
            super(
                    BusinessErrorCategory.VALIDATION,
                    "source.invalid-cursor",
                    "The document page cursor is invalid",
                    cause);
        }
    }
}
