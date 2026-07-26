package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;
import java.util.UUID;

public final class AssistantConversationNotFoundException extends BusinessException {

    public AssistantConversationNotFoundException(UUID conversationId) {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "assistant.conversation-not-found",
                "Assistant conversation not found: " + conversationId);
    }
}
