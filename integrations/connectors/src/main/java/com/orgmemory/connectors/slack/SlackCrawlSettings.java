package com.orgmemory.connectors.slack;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The half of a connection's settings only Slack understands, read out of the opaque document
 * the ledger stored for it.
 *
 * <p>Parsing here rather than in {@code core} is the point of the document being opaque. The
 * ledger has no basis for an opinion about what a channel is; this adapter defined the shape
 * and is the only thing that should read it.
 *
 * <p>A missing or unreadable document is not a failure. A connection somebody enabled without
 * filling in the optional half should crawl every channel with the default bound, not refuse
 * to run — the settings that matter enough to fail on are the ones the ledger kept as columns.
 *
 * @param channels             channel names to crawl; empty means every channel the bot can see
 * @param maxThreadsPerChannel a bound on one crawl so a large workspace cannot run unbounded
 */
record SlackCrawlSettings(List<String> channels, int maxThreadsPerChannel) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_THREADS_PER_CHANNEL = 500;

    SlackCrawlSettings {
        channels = channels == null ? List.of() : List.copyOf(channels);
        maxThreadsPerChannel = maxThreadsPerChannel <= 0 ? DEFAULT_MAX_THREADS_PER_CHANNEL : maxThreadsPerChannel;
    }

    static SlackCrawlSettings from(String sourceConfig) {
        if (sourceConfig == null || sourceConfig.isBlank()) {
            return defaults();
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(sourceConfig);
        } catch (RuntimeException unreadable) {
            return defaults();
        }
        if (!root.isObject()) {
            return defaults();
        }
        List<String> channels = new ArrayList<>();
        JsonNode configured = root.path("channels");
        if (configured.isArray()) {
            for (JsonNode channel : configured) {
                String name = channel.asString("").strip();
                if (!name.isEmpty()) {
                    channels.add(name);
                }
            }
        }
        return new SlackCrawlSettings(channels, root.path("maxThreadsPerChannel").asInt(0));
    }

    private static SlackCrawlSettings defaults() {
        return new SlackCrawlSettings(List.of(), DEFAULT_MAX_THREADS_PER_CHANNEL);
    }
}
