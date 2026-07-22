package com.orgmemory.core.knowledge;

import java.util.Objects;

/**
 * The staging-contract payload versions declared by a crawl batch. Each payload kind
 * (content, identity, permissions) is versioned independently so a connector can evolve
 * one shape without breaking the others. An unknown version fails closed: the batch is
 * rejected rather than interpreted with a guessed shape.
 */
public record ConnectorContractVersions(String content, String identity, String permission) {

    static final String CONTENT_V1 = "content/v1";
    static final String IDENTITY_V1 = "identity/v1";
    static final String PERMISSION_V1 = "permissions/v1";

    public ConnectorContractVersions {
        content = require(content, "content");
        identity = require(identity, "identity");
        permission = require(permission, "permission");
    }

    /** The versions this build understands. Fixtures and the live adapter must match. */
    public static ConnectorContractVersions supported() {
        return new ConnectorContractVersions(CONTENT_V1, IDENTITY_V1, PERMISSION_V1);
    }

    void requireSupported() {
        requireKnown(content, CONTENT_V1, "content");
        requireKnown(identity, IDENTITY_V1, "identity");
        requireKnown(permission, PERMISSION_V1, "permissions");
    }

    private static void requireKnown(String actual, String expected, String kind) {
        if (!expected.equals(actual)) {
            throw new UnsupportedConnectorPayloadException(
                    "Unsupported connector " + kind + " payload version: " + actual);
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connector payload version " + field + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConnectorContractVersions versions
                && content.equals(versions.content)
                && identity.equals(versions.identity)
                && permission.equals(versions.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, identity, permission);
    }
}
