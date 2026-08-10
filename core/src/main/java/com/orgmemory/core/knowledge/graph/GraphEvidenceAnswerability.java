package com.orgmemory.core.knowledge.graph;

/** Active GraphRAG projection state for one exact Source revision. */
public record GraphEvidenceAnswerability(State state, String failureCode) {

    public enum State {
        INDEXING,
        READY,
        FAILED
    }

    public static GraphEvidenceAnswerability indexing() {
        return new GraphEvidenceAnswerability(State.INDEXING, null);
    }

    public static GraphEvidenceAnswerability ready() {
        return new GraphEvidenceAnswerability(State.READY, null);
    }

    public static GraphEvidenceAnswerability failed(String failureCode) {
        return new GraphEvidenceAnswerability(State.FAILED, failureCode);
    }
}
