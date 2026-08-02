package com.orgmemory.core.ai;

import java.util.Locale;

/** OpenAI-specific reasoning effort. Absence on {@link AiRoute} means omit the option. */
public enum OpenAiReasoningEffort {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
