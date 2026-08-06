package com.orgmemory.core.assistant;

import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class AssistantUnavailableException extends BusinessException {

    /**
     * Why this turn was unavailable, as a bounded machine code, or null when the raising site
     * had nothing more specific to say than "it failed".
     *
     * <p>This exists because the turn observation publishes a failure code, and without one
     * carried on the exception every pre-stream failure collapses into a single
     * {@code assistant_turn_failed} series — which is exactly the state that made a 21% failure
     * rate on ZM impossible to attribute. The field is a code and not a message on purpose: it
     * becomes a meter tag, so it is validated against the same bounded pattern the turn event
     * enforces rather than trusted to whoever writes the next {@code throw}.
     */
    private final String failureCode;

    public AssistantUnavailableException(String message) {
        this(message, null, null);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public AssistantUnavailableException(String message, Throwable cause, String failureCode) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "assistant.unavailable",
                message,
                cause);
        this.failureCode = validated(failureCode);
    }

    /** Null when the raising site did not name a cause; never an exception message. */
    public String failureCode() {
        return failureCode;
    }

    private static String validated(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return null;
        }
        String stripped = failureCode.strip();
        if (!AssistantTurnEvent.FAILURE_CODE.matcher(stripped).matches()) {
            throw new IllegalArgumentException("failureCode must be a bounded machine code");
        }
        return stripped;
    }
}
