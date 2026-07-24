package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Framework-neutral answer generation boundary. */
public interface QueryAnswerModel {

    ProcessingComponentRef component();

    Response answer(Request request);

    record Request(
            String query,
            String systemPrompt,
            List<Message> conversationHistory,
            boolean streaming) {

        public Request {
            query = requireText(query, "query");
            systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
            conversationHistory =
                    List.copyOf(Objects.requireNonNull(conversationHistory, "conversationHistory"));
        }
    }

    record Message(Role role, String content) {
        public Message {
            Objects.requireNonNull(role, "role");
            content = requireText(content, "content");
        }
    }

    enum Role {
        USER,
        ASSISTANT
    }

    sealed interface Response permits Complete, Streaming {
    }

    record Complete(String content) implements Response {
        public Complete {
            content = requireText(content, "content");
        }
    }

    record Streaming(Iterator<String> chunks) implements Response {
        public Streaming {
            Objects.requireNonNull(chunks, "chunks");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
