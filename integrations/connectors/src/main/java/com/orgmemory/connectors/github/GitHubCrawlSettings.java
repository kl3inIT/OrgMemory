package com.orgmemory.connectors.github;

import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GitHub-owned settings kept opaque by the ledger.
 *
 * @param repositoryIds optional stable numeric repository ids; empty means every admissible
 *                      private organization repository selected for the installation
 * @param maxItemsPerRepository bound on issue/PR descriptions read in one content crawl
 * @param valid whether an explicit nonblank configuration was structurally usable
 */
record GitHubCrawlSettings(
        Set<String> repositoryIds,
        int maxItemsPerRepository,
        boolean valid) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_ITEMS_PER_REPOSITORY = 500;

    GitHubCrawlSettings {
        repositoryIds = repositoryIds == null ? Set.of() : Set.copyOf(repositoryIds);
        maxItemsPerRepository = maxItemsPerRepository <= 0
                ? DEFAULT_MAX_ITEMS_PER_REPOSITORY
                : maxItemsPerRepository;
    }

    static GitHubCrawlSettings from(String sourceConfig) {
        if (sourceConfig == null || sourceConfig.isBlank()) {
            return defaults();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(sourceConfig);
        } catch (RuntimeException unreadable) {
            return invalid();
        }
        if (root == null || !root.isObject()) {
            return invalid();
        }
        Set<String> repositoryIds = new LinkedHashSet<>();
        JsonNode configured = root.get("repositoryIds");
        if (configured != null) {
            if (!configured.isArray()) {
                return invalid();
            }
            for (JsonNode id : configured) {
                String value = id.asString("").trim();
                if (value.matches("[1-9][0-9]*")) {
                    repositoryIds.add(value);
                }
            }
            if (!configured.isEmpty() && repositoryIds.isEmpty()) {
                return invalid();
            }
        }
        return new GitHubCrawlSettings(
                repositoryIds,
                root.path("maxItemsPerRepository").asInt(0),
                true);
    }

    boolean selectsEverything() {
        return repositoryIds.isEmpty();
    }

    private static GitHubCrawlSettings defaults() {
        return new GitHubCrawlSettings(Set.of(), DEFAULT_MAX_ITEMS_PER_REPOSITORY, true);
    }

    private static GitHubCrawlSettings invalid() {
        return new GitHubCrawlSettings(Set.of(), DEFAULT_MAX_ITEMS_PER_REPOSITORY, false);
    }
}
