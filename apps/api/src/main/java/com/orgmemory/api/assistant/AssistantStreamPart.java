package com.orgmemory.api.assistant;

sealed interface AssistantStreamPart {

    record StartStep() implements AssistantStreamPart {
    }

    record FinishStep() implements AssistantStreamPart {
    }

    record Activity(
            Phase phase,
            State state,
            Integer evidenceCount) implements AssistantStreamPart {

        enum Phase {
            RETRIEVAL,
            GENERATION
        }

        enum State {
            ACTIVE,
            COMPLETE
        }
    }

    record TextStart(String id) implements AssistantStreamPart {
    }

    record TextDelta(String id, String delta) implements AssistantStreamPart {
    }

    record TextEnd(String id) implements AssistantStreamPart {
    }

    record SourceUrl(
            String sourceId,
            String url,
            String title,
            int citationNumber) implements AssistantStreamPart {
    }
}
