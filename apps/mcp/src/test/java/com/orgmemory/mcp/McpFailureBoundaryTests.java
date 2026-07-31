package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamWriteException;

class McpFailureBoundaryTests {

    private static final String INTERNAL_HOST = "orgmemory-api.internal";

    @Test
    void keepsTheSanitizedGatewayMessageAndDropsTheCause() {
        var failure = assertThrows(
                McpFailureBoundary.McpRequestFailedException.class,
                () -> McpFailureBoundary.sanitized(() -> {
                    throw new McpGatewayException(
                            "OrgMemory Asset delivery is temporarily unavailable",
                            new UnknownHostException(INTERNAL_HOST));
                }));

        assertEquals(
                "OrgMemory Asset delivery is temporarily unavailable",
                failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void replacesASerializationFailureWithOneFixedMessage() {
        var failure = assertThrows(
                McpFailureBoundary.McpRequestFailedException.class,
                () -> McpFailureBoundary.sanitized(() -> {
                    throw new StreamWriteException(
                            null,
                            "No serializer found for class com.orgmemory.mcp.AssetDeliveryApiClient$AssetRelease");
                }));

        assertEquals(
                McpFailureBoundary.SERIALIZATION_FAILURE,
                failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("com.orgmemory"));
    }

    @Test
    void letsAnUnexpectedFailurePropagateUnchanged() {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> McpFailureBoundary.sanitized(() -> {
                    throw new IllegalStateException("programming error");
                }));

        assertEquals("programming error", failure.getMessage());
    }
}
