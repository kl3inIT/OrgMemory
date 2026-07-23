package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryContentStore implements ContentStore {

    private final ProjectionPublicationStore publications;
    private final Map<UUID, BatchState> batches = new LinkedHashMap<>();

    public InMemoryContentStore(ProjectionPublicationStore publications) {
        this.publications = Objects.requireNonNull(publications, "publications");
    }

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
        Map<String, ContentRecord> predecessor = batch.expectedPreviousGeneration() == 0
                ? Map.of()
                : recordsAt(publishedPredecessor(batch));
        BatchState created = new BatchState(batch, new LinkedHashMap<>(
                predecessor));
        batches.put(batch.id(), created);
        return created;
    }

    private ProjectionSnapshot publishedPredecessor(ProjectionBatch batch) {
        return publications
                .published(batch.namespace(), batch.expectedPreviousGeneration())
                .filter(snapshot -> snapshot.projections().contains(ProjectionKind.CONTENT))
                .orElseThrow(() -> new IllegalStateException(
                        "previous published content generation is unavailable"));
    }

    private Map<String, ContentRecord> recordsAt(ProjectionSnapshot snapshot) {
        BatchState state = batches.get(snapshot.batchId());
        if (state == null || !matches(state.batch(), snapshot)) {
            throw new IllegalStateException("published content generation is unavailable");
        }
        return state.records();
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
        ProjectionSnapshot recorded = publications
                .published(snapshot.namespace(), snapshot.generation())
                .filter(snapshot::equals)
                .orElseThrow(() -> new IllegalStateException(
                        "content snapshot is not published"));
        BatchState state = batches.get(snapshot.batchId());
        if (state == null || !matches(state.batch(), recorded)) {
            throw new IllegalStateException("content snapshot is unavailable");
        }
        return state;
    }

    private static boolean matches(
            ProjectionBatch batch,
            ProjectionSnapshot snapshot) {
        return batch.id().equals(snapshot.batchId())
                && batch.namespace().equals(snapshot.namespace())
                && batch.generation() == snapshot.generation()
                && batch.manifestFingerprint().equals(snapshot.manifestFingerprint())
                && batch.requiredProjections().equals(snapshot.projections());
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
