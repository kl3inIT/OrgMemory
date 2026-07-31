package com.orgmemory.mcp;

final class McpGatewayException extends RuntimeException {

    McpGatewayException(String message) {
        super(message);
    }

    McpGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
