package com.orgmemory.core.knowledge;

/**
 * One thing at a source that a crawl can be pointed at — a Slack channel, a shared drive, a
 * folder. What it is called there is the adapter's business; what matters here is that an
 * administrator can choose it and be told whether choosing it is enough.
 *
 * @param key         the source's own id, which is what the crawl configuration stores
 * @param displayName what to show, in the source's own vocabulary ({@code #engineering})
 * @param reachable   whether the stored credential can already read it
 * @param admissible  whether choosing it is sufficient — the adapter can gain access itself. When
 *                    both this and {@code reachable} are false, only somebody at the source can
 *                    grant access, and {@link #instruction()} says how.
 * @param instruction what a person must do at the source when the adapter cannot, or null
 */
public record ConnectorScope(
        String key,
        String displayName,
        boolean reachable,
        boolean admissible,
        String instruction) {

    public ConnectorScope {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("connector scope key is required");
        }
        key = key.trim();
        displayName = displayName == null || displayName.isBlank() ? key : displayName.trim();
        instruction = instruction == null || instruction.isBlank() ? null : instruction.trim();
    }

    /** Already readable: choosing it costs nothing further. */
    public static ConnectorScope reachable(String key, String displayName) {
        return new ConnectorScope(key, displayName, true, false, null);
    }

    /** Not readable yet, but the adapter can make it so when it is chosen. */
    public static ConnectorScope admissible(String key, String displayName) {
        return new ConnectorScope(key, displayName, false, true, null);
    }

    /** Not readable, and only a person at the source can change that. */
    public static ConnectorScope barred(String key, String displayName, String instruction) {
        return new ConnectorScope(key, displayName, false, false, instruction);
    }
}
