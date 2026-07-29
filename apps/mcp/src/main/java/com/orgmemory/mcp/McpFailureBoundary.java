package com.orgmemory.mcp;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * Converts a downstream gateway failure into a cause-free MCP failure.
 *
 * <p>The annotation runtime appends the root cause message to the error text it
 * returns to the caller, so a preserved cause would publish internal transport
 * detail such as the API host or request URL. The cause is logged here and only
 * the already-sanitized gateway message crosses the boundary.
 */
final class McpFailureBoundary {

    static final String SERIALIZATION_FAILURE =
            "OrgMemory could not deliver the requested representation";

    private static final Logger log =
            LoggerFactory.getLogger(McpFailureBoundary.class);

    private McpFailureBoundary() {
    }

    static <T> T sanitized(Supplier<T> call) {
        try {
            return call.get();
        } catch (AssetDeliveryApiClient.AssetDeliveryGatewayException
                | KnowledgeSearchApiClient.KnowledgeSearchGatewayException
                        failure) {
            log.warn(
                    "MCP request failed: {}",
                    failure.getMessage(),
                    failure);
            throw new McpRequestFailedException(failure.getMessage());
        } catch (JacksonException failure) {
            log.warn("MCP response serialization failed", failure);
            throw new McpRequestFailedException(SERIALIZATION_FAILURE);
        }
    }

    static final class McpRequestFailedException extends RuntimeException {

        McpRequestFailedException(String message) {
            super(message, null, false, false);
        }
    }
}
