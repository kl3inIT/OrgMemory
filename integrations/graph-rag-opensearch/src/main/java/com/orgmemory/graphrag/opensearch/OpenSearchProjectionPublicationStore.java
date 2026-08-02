package com.orgmemory.graphrag.opensearch;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionAbortOutcome;
import com.orgmemory.graphrag.storage.ProjectionCommitPermit;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    OpenSearchProjectionPublicationStore(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes) {
        this.operations = Objects.requireNonNull(operations, "operations");
        controlIndex = Objects.requireNonNull(indexes, "indexes").control();
        operations.ensureIndex(controlIndex, OpenSearchSchemas.control());
    }

    @Override
    public ProjectionBatch begin(ProjectionBatch candidate) {
        Objects.requireNonNull(candidate, "candidate");
        OpenSearchOperations.VersionedDocument exact =
                operations.get(controlIndex, batchId(candidate.id()));
        if (exact != null) {
            RegisteredBatch registered = requireRegistered(candidate.id());
            requireSameBatch(candidate, registered.batch());
            return registered.batch();
        }
        Optional<RegisteredBatch> active = activeAttemptByIdempotency(candidate);
        if (active.isEmpty()) {
            return ensureRegistered(candidate).batch();
        }
        RegisteredBatch previousAttempt = active.orElseThrow();
        requireSameOperation(candidate, previousAttempt.batch());
        if (hasPermit(previousAttempt)) {
            return previousAttempt.batch();
        }
        if (candidate.claimEpoch() <= 0
                || candidate.claimEpoch() <= previousAttempt.batch().claimEpoch()) {
            throw new PublicationConflictException(
                    "a live or unfenced publication attempt already owns the operation");
        }
        Map<String, Object> aborted =
                new LinkedHashMap<>(previousAttempt.document().source());
        aborted.put("status", ABORTED);
        aborted.put("aborted_at", candidate.createdAt().toString());
        aborted.put("abort_reason", "SUPERSEDED_CLAIM_EPOCH");
        if (!operations.compareAndSet(
                controlIndex,
                batchId(previousAttempt.batch().id()),
                previousAttempt.document(),
                aborted)) {
            throw new PublicationConflictException(
                    "publication attempt changed while rebasing to a newer claim epoch");
        }
        return ensureRegistered(candidate).batch();
    }

    @Override
    public boolean hasBoundCommitPermit(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        RegisteredBatch registered = requireRegistered(batch.id());
        requireSameBatch(batch, registered.batch());
        return hasPermit(registered);
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
    public Optional<ProjectionSnapshot> published(
            ProjectionNamespace namespace,
            String idempotencyKey) {
        Objects.requireNonNull(namespace, "namespace");
        String key = requireText(idempotencyKey, "idempotencyKey");
        OpenSearchOperations.VersionedDocument currentHead =
                operations.get(controlIndex, headId(namespace));
        if (currentHead != null
                && key.equals(currentHead.source().get("idempotency_key"))) {
            return Optional.of(
                    OpenSearchProjectionCodec.snapshot(currentHead.source()));
        }
        return historyByIdempotency(namespace, key);
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
            ProjectionCommitPermit permit,
            Instant publishedAt) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(permit, "permit").requireAuthorizes(batch);
        Objects.requireNonNull(publishedAt, "publishedAt");
        Optional<ProjectionSnapshot> replay = findReplay(batch);
        if (replay.isPresent()) {
            ProjectionSnapshot snapshot = replay.orElseThrow();
            if (snapshot.batchId().equals(batch.id())) {
                RegisteredBatch registered = ensureRegistered(batch);
                bindCommitPermit(batch, permit, registered);
                finalizeVisibleBatch(batch);
            }
            return replay.orElseThrow();
        }

        RegisteredBatch registered = ensureRegistered(batch);
        if (ABORTED.equals(registered.status())) {
            throw new PublicationConflictException("an aborted batch cannot be published");
        }
        requirePrepared(batch);
        registered = bindCommitPermit(batch, permit, registered);
        claimForPublication(batch, registered);

        try {
            OpenSearchOperations.VersionedDocument currentHead =
                    operations.get(controlIndex, headId(batch.namespace()));
            long currentGeneration = currentHead == null
                    ? 0
                    : OpenSearchProjectionCodec.snapshot(currentHead.source())
                            .generation();
            if (currentGeneration != batch.expectedPreviousGeneration()) {
                throw new PublicationConflictException(
                        "expected generation "
                                + batch.expectedPreviousGeneration()
                                + " but current generation is "
                                + currentGeneration);
            }
            UUID currentBatchId = currentHead == null
                    ? null
                    : OpenSearchProjectionCodec.snapshot(currentHead.source()).batchId();
            if (batch.claimEpoch() > 0
                    && !Objects.equals(
                            currentBatchId, batch.expectedPreviousBatchId())) {
                throw new PublicationConflictException(
                        "expected predecessor batch does not match the namespace head");
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
        ProjectionAbortOutcome outcome = abortIfUnreachable(
                batch, normalizedReason, abortedAt);
        if (outcome.status() != ProjectionAbortOutcome.Status.DISCARD_ALLOWED) {
            throw new PublicationConflictException(
                    "a published or ambiguous batch cannot be aborted");
        }
    }

    @Override
    public ProjectionAbortOutcome abortIfUnreachable(
            ProjectionBatch batch,
            String reason,
            Instant abortedAt) {
        Objects.requireNonNull(batch, "batch");
        String normalizedReason = requireText(reason, "reason");
        Objects.requireNonNull(abortedAt, "abortedAt");
        Optional<ProjectionSnapshot> exact = exactPublication(batch);
        if (exact.isPresent()) {
            finalizeVisibleBatch(batch);
            return ProjectionAbortOutcome.published(exact.orElseThrow());
        }
        RegisteredBatch registered = ensureRegistered(batch);
        if (PUBLISHED.equals(registered.status())) {
            return ProjectionAbortOutcome.keepStaging();
        }
        if (PREPARING.equals(registered.status()) && hasPermit(registered)) {
            return ProjectionAbortOutcome.keepStaging();
        }
        if (ABORTED.equals(registered.status())) {
            return ProjectionAbortOutcome.discardAllowed(
                    discardPermit(batch, abortedAt));
        }
        if (COMMITTING.equals(registered.status())
                && !foreignHeadProvesLoss(batch)) {
            return ProjectionAbortOutcome.keepStaging();
        }
        if (!PREPARING.equals(registered.status())
                && !COMMITTING.equals(registered.status())) {
            return ProjectionAbortOutcome.keepStaging();
        }
        Map<String, Object> aborted = new LinkedHashMap<>(registered.document().source());
        aborted.put("status", ABORTED);
        aborted.put("aborted_at", abortedAt.toString());
        aborted.put("abort_reason", normalizedReason);
        if (operations.compareAndSet(
                controlIndex,
                batchId(batch.id()),
                registered.document(),
                aborted)) {
            return ProjectionAbortOutcome.discardAllowed(
                    discardPermit(batch, abortedAt));
        }
        RegisteredBatch latest = requireRegistered(batch.id());
        if (ABORTED.equals(latest.status())) {
            return ProjectionAbortOutcome.discardAllowed(
                    discardPermit(batch, abortedAt));
        }
        return ProjectionAbortOutcome.keepStaging();
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

    private Optional<RegisteredBatch> activeAttemptByIdempotency(
            ProjectionBatch candidate) {
        try {
            var response = operations.client().search(
                    request -> request
                            .index(controlIndex)
                            .size(10)
                            .seqNoPrimaryTerm(true)
                            .query(query -> query.bool(bool -> bool
                                    .filter(OpenSearchStoreSupport.term(
                                            "document_kind", "BATCH"))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.ORGANIZATION_ID,
                                            candidate.namespace()
                                                    .organizationId()
                                                    .toString()))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.WORKSPACE,
                                            candidate.namespace().workspace()))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.COLLECTION,
                                            candidate.namespace().collection()))
                                    .filter(OpenSearchStoreSupport.term(
                                            "idempotency_key",
                                            candidate.idempotencyKey())))),
                    Map.class);
            return response.hits().hits().stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source =
                                (Map<String, Object>) hit.source();
                        return new RegisteredBatch(
                                OpenSearchProjectionCodec.batch(source),
                                source.get("status").toString(),
                                new OpenSearchOperations.VersionedDocument(
                                        source,
                                        hit.seqNo().longValue(),
                                        hit.primaryTerm().longValue()));
                    })
                    .filter(batch -> !ABORTED.equals(batch.status()))
                    .findFirst();
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to find an active publication attempt",
                    exception);
        }
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

    private RegisteredBatch bindCommitPermit(
            ProjectionBatch batch,
            ProjectionCommitPermit permit,
            RegisteredBatch registered) {
        Object existing = registered.document().source().get("commit_permit_id");
        if (existing != null) {
            if (!permit.id().toString().equals(existing.toString())
                    || !Objects.equals(
                            permit.claimEpoch(),
                            ((Number) registered.document().source()
                                    .get("commit_permit_claim_epoch")).longValue())) {
                throw new PublicationConflictException(
                        "publication attempt is bound to a different commit permit");
            }
            return registered;
        }
        if (PUBLISHED.equals(registered.status())) {
            return registered;
        }
        if (!PREPARING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "only a preparing batch can bind a commit permit");
        }
        Map<String, Object> authorized =
                new LinkedHashMap<>(registered.document().source());
        authorized.put("commit_permit_id", permit.id().toString());
        authorized.put("commit_permit_claim_epoch", permit.claimEpoch());
        authorized.put("commit_permit_issued_at", permit.issuedAt().toString());
        if (!operations.compareAndSet(
                controlIndex,
                batchId(batch.id()),
                registered.document(),
                authorized)) {
            RegisteredBatch latest = requireRegistered(batch.id());
            return bindCommitPermit(batch, permit, latest);
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
        Optional<ProjectionSnapshot> idempotent =
                published(batch.namespace(), batch.idempotencyKey());
        idempotent.ifPresent(snapshot -> requireSamePublication(batch, snapshot));
        return idempotent;
    }

    private Optional<ProjectionSnapshot> exactPublication(ProjectionBatch batch) {
        Optional<ProjectionSnapshot> candidate =
                published(batch.namespace(), batch.generation());
        if (candidate.filter(snapshot -> snapshot.batchId().equals(batch.id()))
                .isPresent()) {
            requireSamePublication(batch, candidate.orElseThrow());
            return candidate;
        }
        return Optional.empty();
    }

    private void finalizeVisibleBatch(ProjectionBatch batch) {
        RegisteredBatch registered = requireRegistered(batch.id());
        if (PUBLISHED.equals(registered.status())) {
            return;
        }
        if (!COMMITTING.equals(registered.status())
                && !PREPARING.equals(registered.status())) {
            throw new PublicationConflictException(
                    "visible batch marker cannot be finalized from " + registered.status());
        }
        markPublishedFrom(registered);
    }

    private void markPublishedFrom(RegisteredBatch registered) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (PUBLISHED.equals(registered.status())) {
                return;
            }
            Map<String, Object> published =
                    new LinkedHashMap<>(registered.document().source());
            published.put("status", PUBLISHED);
            if (operations.compareAndSet(
                    controlIndex,
                    batchId(registered.batch().id()),
                    registered.document(),
                    published)) {
                return;
            }
            registered = requireRegistered(registered.batch().id());
        }
        throw new OpenSearchProjectionException(
                "visible namespace head could not finalize batch status");
    }

    private boolean foreignHeadProvesLoss(ProjectionBatch batch) {
        OpenSearchOperations.VersionedDocument head =
                operations.get(controlIndex, headId(batch.namespace()));
        if (head == null) {
            return false;
        }
        ProjectionSnapshot current = OpenSearchProjectionCodec.snapshot(head.source());
        if (current.batchId().equals(batch.id())) {
            return false;
        }
        if (current.generation() < batch.generation()) {
            return false;
        }
        return published(batch.namespace(), batch.generation())
                .filter(snapshot -> snapshot.batchId().equals(batch.id()))
                .isEmpty();
    }

    private Optional<ProjectionSnapshot> historyByIdempotency(
            ProjectionNamespace namespace,
            String idempotencyKey) {
        try {
            var response = operations.client().search(
                    request -> request
                            .index(controlIndex)
                            .size(1)
                            .query(query -> query.bool(bool -> bool
                                    .filter(OpenSearchStoreSupport.term(
                                            "document_kind",
                                            "HISTORY"))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.ORGANIZATION_ID,
                                            namespace.organizationId()
                                                    .toString()))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.WORKSPACE,
                                            namespace.workspace()))
                                    .filter(OpenSearchStoreSupport.term(
                                            OpenSearchProjectionCodec.COLLECTION,
                                            namespace.collection()))
                                    .filter(OpenSearchStoreSupport.term(
                                            "idempotency_key",
                                            idempotencyKey))))
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
        if (!expected.id().equals(actual.id())
                || !expected.namespace().equals(actual.namespace())
                || expected.expectedPreviousGeneration()
                        != actual.expectedPreviousGeneration()
                || !Objects.equals(
                        expected.expectedPreviousBatchId(),
                        actual.expectedPreviousBatchId())
                || expected.generation() != actual.generation()
                || !expected.idempotencyKey().equals(actual.idempotencyKey())
                || !expected.manifestFingerprint().equals(actual.manifestFingerprint())
                || !expected.requiredProjections().equals(actual.requiredProjections())
                || expected.claimEpoch() != actual.claimEpoch()) {
            throw new PublicationConflictException(
                    "a batch id cannot identify different publication content");
        }
    }

    private static void requireSameOperation(
            ProjectionBatch candidate, ProjectionBatch existing) {
        if (!candidate.namespace().equals(existing.namespace())
                || !candidate.idempotencyKey().equals(existing.idempotencyKey())
                || !candidate.manifestFingerprint().equals(existing.manifestFingerprint())
                || !candidate.requiredProjections().equals(existing.requiredProjections())) {
            throw new PublicationConflictException(
                    "an idempotency key cannot identify different publication content");
        }
    }

    private static boolean hasPermit(RegisteredBatch registered) {
        return registered.document().source().get("commit_permit_id") != null;
    }

    private static ProjectionDiscardPermit discardPermit(
            ProjectionBatch batch,
            Instant issuedAt) {
        return new ProjectionDiscardPermit(
                UUID.nameUUIDFromBytes(("discard:" + batch.id())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                batch.id(),
                issuedAt);
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
        return com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile.sha256(canonical);
    }

    private record RegisteredBatch(
            ProjectionBatch batch,
            String status,
            OpenSearchOperations.VersionedDocument document) {
    }
}
