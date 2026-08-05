package com.orgmemory.core.assistant;

/** Safe, transient progress emitted by the server-owned Assistant tool loop. */
public record AssistantAgentActivity(
        Phase phase,
        State state,
        Integer resultCount) {

    public enum Phase {
        SKILL_DISCOVERY,
        SKILL_ACTIVATION,
        SKILL_RESOURCE
    }

    public enum State {
        ACTIVE,
        COMPLETE,
        FAILED
    }
}
