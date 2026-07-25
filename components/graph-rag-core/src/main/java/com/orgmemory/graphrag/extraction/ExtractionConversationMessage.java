package com.orgmemory.graphrag.extraction;

import java.util.Objects;

public record ExtractionConversationMessage(Role role, String content) {

    public ExtractionConversationMessage {
        Objects.requireNonNull(role, "role");
        content = Objects.requireNonNull(content, "content");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }

    public enum Role {
        USER,
        ASSISTANT
    }
}
