package com.orgmemory.core.assistant;

import java.util.UUID;

public final class AssistantConversationNotFoundException extends RuntimeException {

    public AssistantConversationNotFoundException(UUID conversationId) {
        super("Assistant conversation not found: " + conversationId);
    }
}
