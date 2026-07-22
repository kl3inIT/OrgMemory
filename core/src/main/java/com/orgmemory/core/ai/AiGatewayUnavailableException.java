package com.orgmemory.core.ai;

public class AiGatewayUnavailableException extends RuntimeException {

    public AiGatewayUnavailableException(String message) {
        super(message);
    }

    public AiGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
