package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;

/**
 * OpenSearch publication authority using one CAS-protected namespace head.
 *
 * <p>The head embeds the complete winning snapshot. Before the head advances,
 * the previous head is copied to immutable history. Therefore a crash can
 * neither expose a head without its snapshot nor lose the previous generation:
 * the current generation is readable from the head and every older generation
 * is readable from history.
 */
public final class OpenSearchProjectionPublicationStore
        implements ProjectionPublicationStore {

    private static final String PREPARING = "PREPARING";
    private static final String COMMITTING = "COMMITTING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String ABORTED = "ABORTED";

    private final OpenSearchOperations operations;
    private final String controlIndex;

    public OpenSearchProjectionPublicationStore(
            org.opensearch.client.opensearch.OpenSearchClient client,
            OpenSearchGraphRagProperties properties) {
        this(
                operations(client, properties),
                new OpenSearchIndexNames(
                        Objects.requireNonNull(properties, "properties")
                                .getIndexPrefix()));
    }

    OpenSearchProjectionPublicationStore(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes) {
        this.operations = Objects.requireNonNull(operations, "operations");
        controlIndex = Objects.requireNonNull(indexes, "indexes").control();
        operations.ensureIndex(controlIndex, OpenSearchSchemas.control());
    }

    @Override
    public Optional<ProjectionSnapshot> current(ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        OpenSearchOperations.VersionedDocument head =
                operations.get(controlIndex, headId(namespace));
        return head == null
                ? Optional.empty()
                : Optional.of(OpenSearchProjectionCodec.snapshot(head.source()));
    }

    @Override
    public Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            long generation) {
        Objects.requireNonNull(namespace, "namespace");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        Optional<ProjectionSnapshot> current = current(namespace);
        if (current.filter(snapshot -> snapshot.generation() == generation).isPresent()) {
            return current;
        }
        OpenSearchOperations.VersionedDocument history =
                operations.get(controlIndex, historyId(namespace, generation));
        return history == null
                ? Optional.empty()
                : Optional.of(OpenSearchProjectionCodec.snapshot(history.source()));
    }

    @Override
    public void markPrepared(
            ProjectionBatch batch,
            ProjectionKind projection,
            Instant preparedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(preparedAt, "preparedAt");
        if (!batch.requiredProjections().contains(projection)) {
            throw new IllegalArgumentException("projection is not required by this batch");
        }
        RegisteredBatch registered = ensureRegistered(batch);
        if (PUBLISHED.equals(registered.status())) {
            return;
        }
        if (!PREPARING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "only a preparing batch can receive preparation receipts");
        }
        Map<String, Object> receipt = OpenSearchProjectionCodec.batch(batch, PREPARING);
        receipt.put("document_kind", "RECEIPT");
        receipt.put("projection_kind", projection.name());
        receipt.put("prepared_at", preparedAt.toString());
        String receiptId = receiptId(batch.id(), projection);
        if (!operations.create(controlIndex, receiptId, receipt)) {
            OpenSearchOperations.VersionedDocument existing =
                    operations.get(controlIndex, receiptId);
            if (existing == null
                    || !batch.manifestFingerprint()
                            .equals(existing.source().get("manifest_fingerprint"))
                    || !projection.name()
                            .equals(existing.source().get("projection_kind"))) {
                throw new PublicationConflictException(
                        "a preparation receipt identifies different content");
            }
        }
    }

    @Override
    public ProjectionSnapshot publish(
            ProjectionBatch batch,
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Optional<ProjectionSnapshot> replay = findReplay(batch);
        if (replay.isPresent()) {
            return replay.orElseThrow();
        }

        RegisteredBatch registered = ensureRegistered(batch);
        if (ABORTED.equals(registered.status())) {
            throw new PublicationConflictException("an aborted batch cannot be published");
        }
        requirePrepared(batch);
        claimForPublication(batch, registered);

        try {
            OpenSearchOperations.VersionedDocument currentHead =
                    operations.get(controlIndex, headId(batch.namespace()));
            long currentGeneration = currentHead == null
                    ? 0
                    : OpenSearchProjectionCodec.snapshot(currentHead.source())
                            .generation();
            if (currentGeneration != batch.expectedPreviousGeneration()) {
                restorePreparing(batch);
                throw new PublicationConflictException(
                        "expected generation "
                                + batch.expectedPreviousGeneration()
                                + " but current generation is "
                                + currentGeneration);
            }
            if (currentHead != null) {
                persistHistory(currentHead);
            }

            Map<String, Object> headDocument =
                    OpenSearchProjectionCodec.publication(batch, publishedAt);
            headDocument.put("document_kind", "HEAD");
            boolean won = currentHead == null
                    ? operations.create(
                            controlIndex,
                            headId(batch.namespace()),
                            headDocument)
                    : operations.compareAndSet(
                            controlIndex,
                            headId(batch.namespace()),
                            currentHead,
                            headDocument);
            if (!won) {
                Optional<ProjectionSnapshot> afterConflict = current(batch.namespace());
                if (afterConflict.filter(snapshot ->
                                snapshot.batchId().equals(batch.id())
                                        && samePublication(batch, snapshot))
                        .isPresent()) {
                    markPublished(batch);
                    return afterConflict.orElseThrow();
                }
                restorePreparing(batch);
                throw new PublicationConflictException(
                        "another publication advanced the namespace head");
            }

            markPublished(batch);
            return new ProjectionSnapshot(
                    batch.id(),
                    batch.namespace(),
                    batch.generation(),
                    batch.manifestFingerprint(),
                    batch.requiredProjections(),
                    publishedAt);
        } catch (RuntimeException failure) {
            Optional<ProjectionSnapshot> observed = current(batch.namespace());
            if (observed.filter(snapshot -> snapshot.batchId().equals(batch.id()))
                    .isPresent()) {
                markPublished(batch);
                return observed.orElseThrow();
            }
            throw failure;
        }
    }

    @Override
    public void abort(
            ProjectionBatch batch,
            String reason,
            Instant abortedAt) {
        Objects.requireNonNull(batch, "batch");
        String normalizedReason = requireText(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        RegisteredBatch registered = ensureRegistered(batch);
        if (PUBLISHED.equals(registered.status())
                || current(batch.namespace())
                        .filter(snapshot -> snapshot.batchId().equals(batch.id()))
                        .isPresent()) {
            throw new PublicationConflictException(
                    "a published batch cannot be aborted");
        }
        if (ABORTED.equals(registered.status())) {
            return;
        }
        if (COMMITTING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "a committing batch cannot be aborted");
        }
        if (!PREPARING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "batch is not available for abort");
        }
        Map<String, Object> aborted = new LinkedHashMap<>(registered.document().source());
        aborted.put("status", ABORTED);
        aborted.put("aborted_at", abortedAt.toString());
        aborted.put("abort_reason", normalizedReason);
        if (!operations.compareAndSet(
                controlIndex,
                batchId(batch.id()),
                registered.document(),
                aborted)) {
            RegisteredBatch latest = requireRegistered(batch.id());
            if (!ABORTED.equals(latest.status())) {
                throw new PublicationConflictException(
                        "batch state changed while aborting");
            }
        }
    }

    void requireReadable(
            ProjectionSnapshot snapshot,
            ProjectionKind kind) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(kind, "kind");
        ProjectionSnapshot persisted = published(
                        snapshot.namespace(),
                        snapshot.generation())
                .filter(candidate -> candidate.batchId().equals(snapshot.batchId()))
                .filter(candidate -> candidate
                        .manifestFingerprint()
                        .equals(snapshot.manifestFingerprint()))
                .orElseThrow(() -> new PublicationConflictException(
                        "snapshot does not identify a published batch"));
        if (!persisted.projections().equals(snapshot.projections())
                || !persisted.projections().contains(kind)) {
            throw new PublicationConflictException(
                    "snapshot does not exactly contain projection " + kind);
        }
    }

    Optional<UUID> previousBatchId(ProjectionBatch batch) {
        if (batch.expectedPreviousGeneration() == 0) {
            return Optional.empty();
        }
        return published(batch.namespace(), batch.expectedPreviousGeneration())
                .map(ProjectionSnapshot::batchId);
    }

    private RegisteredBatch ensureRegistered(ProjectionBatch batch) {
        String id = batchId(batch.id());
        Map<String, Object> document =
                OpenSearchProjectionCodec.batch(batch, PREPARING);
        if (operations.create(controlIndex, id, document)) {
            return requireRegistered(batch.id());
        }
        RegisteredBatch registered = requireRegistered(batch.id());
        requireSameBatch(batch, registered.batch());
        return registered;
    }

    private RegisteredBatch requireRegistered(UUID batchId) {
        OpenSearchOperations.VersionedDocument document =
                operations.get(controlIndex, batchId(batchId));
        if (document == null) {
            throw new PublicationConflictException("batch is not registered");
        }
        return new RegisteredBatch(
                OpenSearchProjectionCodec.batch(document.source()),
                document.source().get("status").toString(),
                document);
    }

    private RegisteredBatch claimForPublication(
            ProjectionBatch batch,
            RegisteredBatch registered) {
        if (COMMITTING.equals(registered.status())) {
            return registered;
        }
        if (!PREPARING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "batch is not available for publication");
        }
        Map<String, Object> committing =
                new LinkedHashMap<>(registered.document().source());
        committing.put("status", COMMITTING);
        committing.put("commit_started_at", Instant.now().toString());
        if (!operations.compareAndSet(
                controlIndex,
                batchId(batch.id()),
                registered.document(),
                committing)) {
            RegisteredBatch latest = requireRegistered(batch.id());
            if (!COMMITTING.equals(latest.status())) {
                throw new PublicationConflictException(
                        "batch state changed before publication");
            }
            return latest;
        }
        return requireRegistered(batch.id());
    }

    private void markPublished(ProjectionBatch batch) {
        for (int attempt = 0; attempt < 3; attempt++) {
            RegisteredBatch registered = requireRegistered(batch.id());
            if (PUBLISHED.equals(registered.status())) {
                return;
            }
            if (!COMMITTING.equals(registered.status())) {
                throw new PublicationConflictException(
                        "only the committing batch can finalize publication");
            }
            Map<String, Object> published =
                    new LinkedHashMap<>(registered.document().source());
            published.put("status", PUBLISHED);
            if (operations.compareAndSet(
                    controlIndex,
                    batchId(batch.id()),
                    registered.document(),
                    published)) {
                return;
            }
        }
        throw new OpenSearchProjectionException(
                "published namespace head but could not finalize batch status");
    }

    private void restorePreparing(ProjectionBatch batch) {
        RegisteredBatch latest = requireRegistered(batch.id());
        if (!COMMITTING.equals(latest.status())) {
            return;
        }
        Map<String, Object> preparing =
                new LinkedHashMap<>(latest.document().source());
        preparing.put("status", PREPARING);
        preparing.remove("commit_started_at");
        operations.compareAndSet(
                controlIndex,
                batchId(batch.id()),
                latest.document(),
                preparing);
    }

    private void requirePrepared(ProjectionBatch batch) {
        for (ProjectionKind projection : batch.requiredProjections()) {
            OpenSearchOperations.VersionedDocument receipt = operations.get(
                    controlIndex,
                    receiptId(batch.id(), projection));
            if (receipt == null
                    || !batch.manifestFingerprint()
                            .equals(receipt.source().get("manifest_fingerprint"))) {
                throw new PublicationNotReadyException(
                        "every required projection must have a durable preparation receipt");
            }
        }
    }

    private void persistHistory(
            OpenSearchOperations.VersionedDocument currentHead) {
        ProjectionSnapshot snapshot =
                OpenSearchProjectionCodec.snapshot(currentHead.source());
        Map<String, Object> history = new LinkedHashMap<>(currentHead.source());
        history.put("document_kind", "HISTORY");
        String id = historyId(snapshot.namespace(), snapshot.generation());
        if (!operations.create(controlIndex, id, history)) {
            OpenSearchOperations.VersionedDocument existing =
                    operations.get(controlIndex, id);
            if (existing == null
                    || !snapshot.equals(
                            OpenSearchProjectionCodec.snapshot(existing.source()))) {
                throw new PublicationConflictException(
                        "publication history identifies different content");
            }
        }
    }

    private Optional<ProjectionSnapshot> findReplay(ProjectionBatch batch) {
        Optional<ProjectionSnapshot> batchGeneration =
                published(batch.namespace(), batch.generation());
        if (batchGeneration.isPresent()) {
            ProjectionSnapshot snapshot = batchGeneration.orElseThrow();
            if (snapshot.batchId().equals(batch.id()) && samePublication(batch, snapshot)) {
                return batchGeneration;
            }
            if (snapshot.batchId().equals(batch.id())) {
                requireSamePublication(batch, snapshot);
            }
        }
        OpenSearchOperations.VersionedDocument currentHead =
                operations.get(controlIndex, headId(batch.namespace()));
        if (currentHead != null
                && batch.idempotencyKey()
                        .equals(currentHead.source().get("idempotency_key"))) {
            ProjectionSnapshot snapshot =
                    OpenSearchProjectionCodec.snapshot(currentHead.source());
            requireSamePublication(batch, snapshot);
            return Optional.of(snapshot);
        }
        Optional<ProjectionSnapshot> historical = historyByIdempotency(batch);
        historical.ifPresent(snapshot -> requireSamePublication(batch, snapshot));
        return historical;
    }

    private Optional<ProjectionSnapshot> historyByIdempotency(ProjectionBatch batch) {
        try {
            var response = operations.client().search(
                    request -> request
                            .index(controlIndex)
                            .size(1)
                            .query(query -> query.bool(bool -> bool
                                    .filter(term(
                                            "document_kind",
                                            "HISTORY"))
                                    .filter(term(
                                            OpenSearchProjectionCodec.ORGANIZATION_ID,
                                            batch.namespace()
                                                    .organizationId()
                                                    .toString()))
                                    .filter(term(
                                            OpenSearchProjectionCodec.WORKSPACE,
                                            batch.namespace().workspace()))
                                    .filter(term(
                                            OpenSearchProjectionCodec.COLLECTION,
                                            batch.namespace().collection()))
                                    .filter(term(
                                            "idempotency_key",
                                            batch.idempotencyKey()))))
                            .sort(sort -> sort.field(field -> field
                                    .field(OpenSearchProjectionCodec.GENERATION)
                                    .order(SortOrder.Asc))),
                    Map.class);
            if (response.hits().hits().isEmpty()
                    || response.hits().hits().getFirst().source() == null) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source =
                    (Map<String, Object>) response.hits().hits().getFirst().source();
            return Optional.of(OpenSearchProjectionCodec.snapshot(source));
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to find an idempotent publication",
                    exception);
        }
    }

    private static org.opensearch.client.opensearch._types.query_dsl.Query term(
            String field,
            String value) {
        return org.opensearch.client.opensearch._types.query_dsl.Query.of(query ->
                query.term(term -> term
                        .field(field)
                        .value(FieldValue.of(value))));
    }

    private static boolean samePublication(
            ProjectionBatch batch,
            ProjectionSnapshot snapshot) {
        return batch.namespace().equals(snapshot.namespace())
                && batch.generation() == snapshot.generation()
                && batch.manifestFingerprint().equals(snapshot.manifestFingerprint())
                && batch.requiredProjections().equals(snapshot.projections());
    }

    private static void requireSamePublication(
            ProjectionBatch batch,
            ProjectionSnapshot snapshot) {
        if (!samePublication(batch, snapshot)) {
            throw new PublicationConflictException(
                    "an idempotency key cannot identify different publication content");
        }
    }

    private static void requireSameBatch(
            ProjectionBatch expected,
            ProjectionBatch actual) {
        if (!expected.equals(actual)) {
            throw new PublicationConflictException(
                    "a batch id cannot identify different publication content");
        }
    }

    private static String batchId(UUID batchId) {
        return "batch:" + batchId;
    }

    private static String receiptId(
            UUID batchId,
            ProjectionKind projection) {
        return "receipt:" + batchId + ":" + projection.name();
    }

    private static String headId(ProjectionNamespace namespace) {
        return "head:" + namespaceKey(namespace);
    }

    private static String historyId(
            ProjectionNamespace namespace,
            long generation) {
        return "history:" + namespaceKey(namespace) + ":" + generation;
    }

    private static String namespaceKey(ProjectionNamespace namespace) {
        String canonical = namespace.organizationId()
                + "\u0000"
                + namespace.workspace()
                + "\u0000"
                + namespace.collection();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static OpenSearchOperations operations(
            org.opensearch.client.opensearch.OpenSearchClient client,
            OpenSearchGraphRagProperties properties) {
        return new OpenSearchOperations(
                Objects.requireNonNull(client, "client"),
                Objects.requireNonNull(properties, "properties")
                        .getBulkMaximumOperations());
    }

    private record RegisteredBatch(
            ProjectionBatch batch,
            String status,
            OpenSearchOperations.VersionedDocument document) {
    }
}
