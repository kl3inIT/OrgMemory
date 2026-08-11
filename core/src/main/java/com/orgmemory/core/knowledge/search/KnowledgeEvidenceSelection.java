package com.orgmemory.core.knowledge.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Optional second retrieval ceiling for one Assistant turn.
 *
 * <p>Actor authorization remains the first ceiling. A restricted selection can
 * only narrow it and carries immutable Source/revision/Asset identity so a
 * later revision or a graph neighbor cannot silently replace the uploaded
 * evidence.
 */
public record KnowledgeEvidenceSelection(boolean restricted, List<Item> items) {

    public static final int MAXIMUM_ITEMS = 3;

    public KnowledgeEvidenceSelection {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (!restricted && !items.isEmpty()) {
            throw new IllegalArgumentException("an unrestricted selection has no items");
        }
        if (restricted && (items.isEmpty() || items.size() > MAXIMUM_ITEMS)) {
            throw new IllegalArgumentException("a restricted selection requires between one and three items");
        }
        if (new LinkedHashSet<>(items.stream().map(Item::bindingId).toList()).size() != items.size()) {
            throw new IllegalArgumentException("binding ids must be unique");
        }
        if (new LinkedHashSet<>(items.stream().map(Item::sourceObjectId).toList()).size() != items.size()) {
            throw new IllegalArgumentException("selected Source objects must be unique");
        }
    }

    public static KnowledgeEvidenceSelection unrestricted() {
        return new KnowledgeEvidenceSelection(false, List.of());
    }

    public static KnowledgeEvidenceSelection restricted(List<Item> items) {
        return new KnowledgeEvidenceSelection(true, items);
    }

    public Set<UUID> assetIds() {
        return Set.copyOf(items.stream().map(Item::knowledgeAssetId).toList());
    }

    public boolean contains(
            UUID knowledgeAssetId,
            UUID sourceObjectId,
            UUID sourceRevisionId) {
        return !restricted || items.stream().anyMatch(item ->
                item.knowledgeAssetId().equals(knowledgeAssetId)
                        && item.sourceObjectId().equals(sourceObjectId)
                        && item.sourceRevisionId().equals(sourceRevisionId));
    }

    public Set<UUID> missingSourceObjectIds(List<RetrievedKnowledgeEvidence> evidence) {
        if (!restricted) {
            return Set.of();
        }
        Set<UUID> found = evidence.stream()
                .filter(item -> contains(
                        item.knowledgeAssetId(),
                        item.sourceObjectId(),
                        item.sourceRevisionId()))
                .map(RetrievedKnowledgeEvidence::sourceObjectId)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<UUID> missing = new LinkedHashSet<>();
        for (Item item : items) {
            if (!found.contains(item.sourceObjectId())) {
                missing.add(item.sourceObjectId());
            }
        }
        return Set.copyOf(missing);
    }

    public record Item(
            UUID bindingId,
            UUID sourceObjectId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId) {

        public Item {
            Objects.requireNonNull(bindingId, "bindingId");
            Objects.requireNonNull(sourceObjectId, "sourceObjectId");
            Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
            Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        }
    }
}
