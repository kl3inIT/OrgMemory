package com.orgmemory.connectors.github;

import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Administrator-owned policy for using a GitHub connection during Skill import. */
record GitHubSkillImportSettings(
        boolean allowPrivateSkillImports,
        Set<String> repositoryIds,
        boolean valid) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    GitHubSkillImportSettings {
        repositoryIds = repositoryIds == null ? Set.of() : Set.copyOf(repositoryIds);
    }

    static GitHubSkillImportSettings from(String sourceConfig) {
        JsonNode root;
        try {
            root = MAPPER.readTree(sourceConfig == null ? "{}" : sourceConfig);
        } catch (RuntimeException unreadable) {
            return invalid();
        }
        if (root == null || !root.isObject()) {
            return invalid();
        }
        JsonNode enabled = root.get("allowPrivateSkillImports");
        if (enabled != null && !enabled.isBoolean()) {
            return invalid();
        }
        Set<String> repositoryIds = new LinkedHashSet<>();
        JsonNode repositories = root.get("repositoryIds");
        if (repositories != null) {
            if (!repositories.isArray()) {
                return invalid();
            }
            for (JsonNode id : repositories) {
                String value = id.asString("").strip();
                if (!value.matches("[1-9][0-9]*")) {
                    return invalid();
                }
                repositoryIds.add(value);
            }
        }
        if (enabled != null && enabled.asBoolean() && repositoryIds.isEmpty()) {
            return invalid();
        }
        return new GitHubSkillImportSettings(
                enabled != null && enabled.asBoolean(), repositoryIds, true);
    }

    boolean allowsRepository(String repositoryId) {
        return repositoryId != null
                && !repositoryId.isBlank()
                && repositoryIds.contains(repositoryId);
    }

    private static GitHubSkillImportSettings invalid() {
        return new GitHubSkillImportSettings(false, Set.of(), false);
    }
}
