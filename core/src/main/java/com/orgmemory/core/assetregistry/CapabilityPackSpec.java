package com.orgmemory.core.assetregistry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CapabilityPackSpec(
        Purpose purpose,
        String audience,
        List<String> prerequisites,
        String expectedOutcome,
        List<Item> items,
        List<String> completionCriteria,
        String reviewDate,
        String owner) {

    public CapabilityPackSpec {
        purpose = Objects.requireNonNull(purpose, "purpose");
        audience = required(audience, "audience", 1_000);
        prerequisites = strings(prerequisites, "prerequisites", 30, 1_000);
        expectedOutcome = required(expectedOutcome, "expectedOutcome", 2_000);
        items = items == null ? List.of() : List.copyOf(items);
        completionCriteria = strings(
                completionCriteria, "completionCriteria", 30, 1_000);
        reviewDate = required(reviewDate, "reviewDate", 10);
        java.time.LocalDate.parse(reviewDate);
        owner = required(owner, "owner", 256);
        if (items.isEmpty() || items.size() > 50) {
            throw new IllegalArgumentException(
                    "Capability Pack requires between 1 and 50 items");
        }
        Set<String> keys = new HashSet<>();
        for (Item item : items) {
            if (!keys.add(item.key())) {
                throw new IllegalArgumentException("Capability Pack item keys must be unique");
            }
        }
        if (items.stream().noneMatch(Item::required)) {
            throw new IllegalArgumentException(
                    "Capability Pack requires at least one required item");
        }
    }

    public enum Purpose {
        ROLE_ONBOARDING,
        HANDOVER,
        ROLE_ENABLEMENT
    }

    public enum ItemKind {
        REGISTRY_RELEASE,
        KNOWLEDGE_VERSION
    }

    public record Item(
            String key,
            boolean required,
            ItemKind kind,
            UUID assetId,
            UUID releaseId,
            UUID knowledgeAssetId,
            UUID knowledgeVersionId) {

        public Item {
            key = CapabilityPackSpec.required(key, "item.key", 64);
            kind = Objects.requireNonNull(kind, "item.kind");
            if (kind == ItemKind.REGISTRY_RELEASE) {
                Objects.requireNonNull(assetId, "item.assetId");
                Objects.requireNonNull(releaseId, "item.releaseId");
                if (knowledgeAssetId != null || knowledgeVersionId != null) {
                    throw new IllegalArgumentException(
                            "Registry Pack items cannot carry Knowledge pins");
                }
            } else {
                Objects.requireNonNull(knowledgeAssetId, "item.knowledgeAssetId");
                Objects.requireNonNull(knowledgeVersionId, "item.knowledgeVersionId");
                if (assetId != null || releaseId != null) {
                    throw new IllegalArgumentException(
                            "Knowledge Pack items cannot carry registry pins");
                }
            }
        }
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

    private static String required(String value, String field, int maximumLength) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is blank or exceeds maximum length");
        }
        return normalized;
    }
}
