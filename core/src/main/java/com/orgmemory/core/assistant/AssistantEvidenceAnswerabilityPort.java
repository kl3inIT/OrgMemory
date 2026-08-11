package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;

/** Active Assistant engine readiness, separate from Source processing readiness. */
public interface AssistantEvidenceAnswerabilityPort {

    Answerability evaluate(GovernedEvidenceRef source);

    record Answerability(State state, String failureCode) {

        public enum State {
            INDEXING,
            READY,
            FAILED
        }

        public static Answerability indexing() {
            return new Answerability(State.INDEXING, null);
        }

        public static Answerability ready() {
            return new Answerability(State.READY, null);
        }

        public static Answerability failed(String failureCode) {
            return new Answerability(State.FAILED, failureCode);
        }
    }
}
