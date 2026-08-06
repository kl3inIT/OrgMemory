package com.orgmemory.core.assistant;

/** Safe, transient progress emitted by the server-owned Assistant tool loop. */
public record AssistantAgentActivity(
        Phase phase,
        State state,
        Integer resultCount,
        Integer skillOrdinal,
        String skillTitle) {

    private static final int MAX_SKILL_TITLE_LENGTH = 80;

    public AssistantAgentActivity {
        if (phase == null || state == null) {
            throw new IllegalArgumentException("Assistant activity phase and state are required");
        }
        if (resultCount != null && resultCount < 0) {
            throw new IllegalArgumentException("Assistant activity result count cannot be negative");
        }
        if (skillOrdinal != null && skillOrdinal <= 0) {
            throw new IllegalArgumentException("Skill ordinal must be positive");
        }
        if (phase == Phase.SKILL_DISCOVERY && (skillOrdinal != null || skillTitle != null)) {
            throw new IllegalArgumentException("Skill discovery activity cannot identify a Skill");
        }
        if (skillTitle != null) {
            if (phase != Phase.SKILL_ACTIVATION || state != State.COMPLETE || skillOrdinal == null) {
                throw new IllegalArgumentException(
                        "A Skill title is allowed only for a completed activation");
            }
            skillTitle = sanitizeTitle(skillTitle);
        }
    }

    public AssistantAgentActivity(Phase phase, State state, Integer resultCount) {
        this(phase, state, resultCount, null, null);
    }

    private static String sanitizeTitle(String value) {
        String sanitized = value.codePoints()
                .map(codePoint -> Character.isWhitespace(codePoint) ? ' ' : codePoint)
                .filter(codePoint -> !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim()
                .replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Skill title cannot be blank");
        }
        if (sanitized.codePointCount(0, sanitized.length()) <= MAX_SKILL_TITLE_LENGTH) {
            return sanitized;
        }
        int end = sanitized.offsetByCodePoints(0, MAX_SKILL_TITLE_LENGTH - 1);
        return sanitized.substring(0, end).stripTrailing() + "…";
    }

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
