package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryContentStore implements ContentStore {

    private final Map<UUID, BatchState> batches = new LinkedHashMap<>();

    @Override
    public synchronized void stageUpsert(
            ProjectionBatch batch,
            Collection<ContentRecord> records) {
        BatchState state = stateFor(batch);
        for (ContentRecord record : Objects.requireNonNull(records, "records")) {
            requireSameOrganization(batch, record);
            state.records().put(record.id(), record);
        }
    }

    @Override
    public synchronized void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        BatchState state = stateFor(batch);
        Objects.requireNonNull(ids, "ids").forEach(state.records()::remove);
    }

    @Override
    public synchronized Optional<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            String id) {
        Objects.requireNonNull(scope, "scope");
        BatchState state = readableState(scope, snapshot);
        ContentRecord record = state.records().get(Objects.requireNonNull(id, "id"));
        return Optional.ofNullable(record)
                .filter(candidate -> scope.includes(
                        candidate.evidence().organizationId(),
                        candidate.evidence().knowledgeAssetId()));
    }

    @Override
    public synchronized List<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids) {
        Objects.requireNonNull(ids, "ids");
        return ids.stream()
                .map(id -> get(scope, snapshot, id))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public synchronized void discard(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        batches.remove(batch.id());
    }

    private BatchState stateFor(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (!batch.requiredProjections().contains(ProjectionKind.CONTENT)) {
            throw new IllegalArgumentException("batch does not require a content projection");
        }
        BatchState existing = batches.get(batch.id());
        if (existing != null) {
            if (!existing.batch().equals(batch)) {
                throw new IllegalArgumentException(
                        "a batch id cannot identify different publication content");
            }
            return existing;
        }
        BatchState created = new BatchState(batch, new LinkedHashMap<>(
                batch.expectedPreviousGeneration() == 0
                        ? Map.of()
                        : recordsAt(batch.namespace(), batch.expectedPreviousGeneration())));
        batches.put(batch.id(), created);
        return created;
    }

    private Map<String, ContentRecord> recordsAt(
            com.orgmemory.graphrag.storage.ProjectionNamespace namespace,
            long generation) {
        return batches.values().stream()
                .filter(state -> state.batch().namespace().equals(namespace))
                .filter(state -> state.batch().generation() == generation)
                .findFirst()
                .map(BatchState::records)
                .orElseThrow(() -> new IllegalStateException(
                        "previous content generation is unavailable"));
    }

    private BatchState readableState(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.projections().contains(ProjectionKind.CONTENT)) {
            throw new IllegalArgumentException("snapshot has no content projection");
        }
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and projection snapshot belong to different organizations");
        }
        BatchState state = batches.get(snapshot.batchId());
        if (state == null
                || state.batch().generation() != snapshot.generation()
                || !state.batch().namespace().equals(snapshot.namespace())) {
            throw new IllegalStateException("content snapshot is unavailable");
        }
        return state;
    }

    private static void requireSameOrganization(
            ProjectionBatch batch,
            ContentRecord record) {
        if (!batch.namespace().organizationId().equals(record.evidence().organizationId())) {
            throw new IllegalArgumentException(
                    "content evidence belongs to another organization");
        }
    }

    private record BatchState(
            ProjectionBatch batch,
            Map<String, ContentRecord> records) {

        private BatchState {
            Objects.requireNonNull(batch, "batch");
            Objects.requireNonNull(records, "records");
        }
    }
}
