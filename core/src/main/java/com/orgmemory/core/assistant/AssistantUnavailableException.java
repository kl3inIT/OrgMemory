package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class AssistantUnavailableException extends BusinessException {

    public AssistantUnavailableException(String message) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "assistant.unavailable",
                message);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "assistant.unavailable",
                message,
                cause);
    }
}
