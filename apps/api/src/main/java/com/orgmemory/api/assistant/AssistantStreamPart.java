package com.orgmemory.api.assistant;

sealed interface AssistantStreamPart {

    record StartStep() implements AssistantStreamPart {
    }

    record FinishStep() implements AssistantStreamPart {
    }

    record TextStart(String id) implements AssistantStreamPart {
    }

    record TextDelta(String id, String delta) implements AssistantStreamPart {
    }

    record TextEnd(String id) implements AssistantStreamPart {
    }

    record SourceUrl(String sourceId, String url, String title) implements AssistantStreamPart {
    }

    record SourceDocument(String sourceId, String mediaType, String title, String filename)
            implements AssistantStreamPart {
    }
}
