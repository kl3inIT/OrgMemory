package com.orgmemory.core.assistant;

import java.util.Objects;

/**
 * One transcript message offered back to the model as prior context.
 *
 * <p>Deliberately not the transcript view type: this carries no identity, no
 * timestamp, and no feedback, because none of that belongs in a model prompt.
 */
public record AssistantContextMessage(AssistantConversationRole role, String content) {

    public AssistantContextMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
