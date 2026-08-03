package com.orgmemory.core.assetregistry.workinstructioncontract;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record WorkInstructionSpec(
        String purpose,
        String audience,
        List<String> prerequisites,
        String completionOutcome,
        String responsibleRole,
        List<Step> steps) {

    public WorkInstructionSpec {
        purpose = required(purpose, "purpose", 2_000);
        audience = required(audience, "audience", 1_000);
        prerequisites = strings(prerequisites, "prerequisites", 30, 1_000);
        completionOutcome = required(completionOutcome, "completionOutcome", 2_000);
        responsibleRole = required(responsibleRole, "responsibleRole", 256);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.isEmpty() || steps.size() > 100) {
            throw new IllegalArgumentException(
                    "Work Instruction requires between 1 and 100 steps");
        }
        Set<String> keys = new HashSet<>();
        for (Step step : steps) {
            if (!keys.add(step.key())) {
                throw new IllegalArgumentException("Work Instruction step keys must be unique");
            }
        }
    }

    public record Step(
            String key,
            String title,
            String instruction,
            String expectedResult,
            String check,
            String escalation,
            List<String> prohibitedActions,
            List<UUID> relatedAssetIds,
            List<UUID> relatedKnowledgeVersionIds) {

        public Step {
            key = required(key, "step.key", 64);
            title = required(title, "step.title", 256);
            instruction = required(instruction, "step.instruction", 8_000);
            expectedResult = required(expectedResult, "step.expectedResult", 2_000);
            check = required(check, "step.check", 2_000);
            escalation = optional(escalation, 2_000);
            prohibitedActions = strings(
                    prohibitedActions, "step.prohibitedActions", 20, 1_000);
            relatedAssetIds = ids(relatedAssetIds);
            relatedKnowledgeVersionIds = ids(relatedKnowledgeVersionIds);
        }
    }

    private static List<UUID> ids(List<UUID> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 20 || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Related references are invalid");
        }
        return List.copyOf(values);
    }

    private static List<String> strings(
            List<String> values, String field, int maximumItems, int maximumLength) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maximumItems) {
            throw new IllegalArgumentException(field + " has too many items");
        }
        return values.stream()
                .map(value -> required(value, field, maximumLength))
                .toList();
    }

    private static String optional(String value, int maximumLength) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("Value exceeds maximum length");
        }
        return normalized;
    }

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds maximum length");
        }
        return normalized;
    }
}
