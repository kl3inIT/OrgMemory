package com.orgmemory.graphrag.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

/** Owns copy-forward coordination and bounded page-to-bulk transport. */
final class OpenSearchCopyForwardCoordinator {

    static final String COPYING = "COPYING";
    static final String READY = "READY";
    static final String FAILED = "FAILED";

    private static final int MAXIMUM_SCAN_PAGE_SIZE = 500;
    private static final int BULK_ENVELOPE_BYTES = 64;

    private final MarkerStore markers;
    private final PageScanner scanner;
    private final BulkSink bulkSink;
    private final int maximumOperations;
    private final long maximumBytes;
    private final Clock clock;
    private final Supplier<String> owners;
    private final ConcurrentHashMap<String, ReentrantLock> copyLocks;
    private final ObjectMapper objectMapper;

    OpenSearchCopyForwardCoordinator(
            OpenSearchOperations operations,
            String controlIndex,
            long maximumBytes) {
        this(
                new OperationsMarkerStore(operations, controlIndex),
                new OpenSearchScanner(operations)::scanPages,
                operations::bulk,
                operations.bulkMaximumOperations(),
                maximumBytes,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString(),
                new ConcurrentHashMap<>(),
                new ObjectMapper());
    }

    OpenSearchCopyForwardCoordinator(
            MarkerStore markers,
            PageScanner scanner,
            BulkSink bulkSink,
            int maximumOperations,
            long maximumBytes,
            Clock clock,
            Supplier<String> owners,
            ConcurrentHashMap<String, ReentrantLock> copyLocks,
            ObjectMapper objectMapper) {
        this.markers = Objects.requireNonNull(markers, "markers");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.bulkSink = Objects.requireNonNull(bulkSink, "bulkSink");
        if (maximumOperations <= 0) {
            throw new IllegalArgumentException("maximumOperations must be positive");
        }
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumOperations = maximumOperations;
        this.maximumBytes = maximumBytes;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.owners = Objects.requireNonNull(owners, "owners");
        this.copyLocks = Objects.requireNonNull(copyLocks, "copyLocks");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    void copyForward(
            ProjectionBatch batch,
            CopyUnit unit,
            Runnable copy,
            Runnable cleanup) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(cleanup, "cleanup");
        String markerId = markerId(batch, unit);
        while (true) {
            ReentrantLock lock =
                    copyLocks.computeIfAbsent(markerId, ignored -> new ReentrantLock());
            lock.lock();
            boolean retire = false;
            try {
                if (copyLocks.get(markerId) != lock) {
                    continue;
                }
                retire = true;
                OpenSearchOperations.VersionedDocument marker = markers.get(markerId);
                String status = status(marker);
                if (READY.equals(status)) {
                    return;
                }
                if (COPYING.equals(status)) {
                    throw new OpenSearchProjectionException(
                            "copy-forward is already in progress for " + markerId);
                }
                if (marker != null && !FAILED.equals(status)) {
                    throw new OpenSearchProjectionException(
                            "copy-forward marker has unsupported state for " + markerId);
                }

                long attempt = marker == null ? 1 : attempt(marker) + 1;
                String owner = owners.get();
                Map<String, Object> copying = copying(batch, unit, owner, attempt);
                boolean claimed = marker == null
                        ? markers.create(markerId, copying)
                        : markers.compareAndSet(markerId, marker, copying);
                if (!claimed) {
                    OpenSearchOperations.VersionedDocument winner = markers.get(markerId);
                    if (READY.equals(status(winner))) {
                        return;
                    }
                    throw new OpenSearchProjectionException(
                            "another process claimed copy-forward for " + markerId);
                }

                try {
                    if (marker != null) {
                        cleanup.run();
                    }
                    copy.run();
                    transitionReady(markerId, owner, attempt);
                } catch (RuntimeException failure) {
                    try {
                        cleanup.run();
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    try {
                        transitionFailed(markerId, owner, attempt, failure);
                    } catch (RuntimeException markerFailure) {
                        failure.addSuppressed(markerFailure);
                    }
                    throw failure;
                }
                return;
            } finally {
                if (retire) {
                    copyLocks.remove(markerId, lock);
                }
                lock.unlock();
            }
        }
    }

    void stream(
            String sourceIndex,
            Query query,
            Function<OpenSearchScanner.StoredHit, CopyOperation> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        BulkAccumulator accumulator = new BulkAccumulator();
        scanner.scan(
                sourceIndex,
                query,
                Integer.MAX_VALUE,
                Math.min(maximumOperations, MAXIMUM_SCAN_PAGE_SIZE),
                page -> page.forEach(hit -> accumulator.add(mapper.apply(hit))));
        accumulator.flush();
    }

    CopyOperation indexOperation(
            String index,
            String id,
            Map<String, Object> document) {
        Objects.requireNonNull(document, "document");
        BulkOperation operation = BulkOperation.of(candidate -> candidate.index(write -> write
                .index(index)
                .id(id)
                .document(document)));
        return new CopyOperation(
                operation,
                encodedBytes(index, id, document));
    }

    CopyOperation deleteOperation(
            String index,
            String id) {
        BulkOperation operation = BulkOperation.of(candidate -> candidate.delete(delete -> delete
                .index(index)
                .id(id)));
        return new CopyOperation(
                operation,
                encodedBytes(index, id, Map.of()));
    }

    String markerId(
            ProjectionBatch batch,
            CopyUnit unit) {
        String target = unit.targetIdentity();
        return "copy:"
                + batch.id()
                + ":"
                + unit.logicalUnit()
                + ":"
                + target.length()
                + ":"
                + target;
    }

    int localLockCount() {
        return copyLocks.size();
    }

    private Map<String, Object> copying(
            ProjectionBatch batch,
            CopyUnit unit,
            String owner,
            long attempt) {
        Map<String, Object> document = OpenSearchProjectionCodec.batch(batch, "PREPARING");
        document.put("document_kind", "COPY_FORWARD");
        document.put("projection_kind", unit.projectionKind().name());
        document.put("copy_unit", unit.logicalUnit());
        document.put("target_index", unit.targetIdentity());
        document.put("copy_status", COPYING);
        document.put("copy_owner", owner);
        document.put("copy_attempt", attempt);
        document.put("copy_started_at", clock.instant().toString());
        return document;
    }

    private void transitionReady(
            String markerId,
            String owner,
            long attempt) {
        OpenSearchOperations.VersionedDocument owned = requireOwned(
                markerId, owner, attempt);
        Map<String, Object> ready = new LinkedHashMap<>(owned.source());
        ready.put("copy_status", READY);
        ready.put("copy_completed_at", clock.instant().toString());
        if (!markers.compareAndSet(markerId, owned, ready)) {
            throw new OpenSearchProjectionException(
                    "could not complete copy-forward marker " + markerId);
        }
    }

    private void transitionFailed(
            String markerId,
            String owner,
            long attempt,
            RuntimeException failure) {
        OpenSearchOperations.VersionedDocument owned = requireOwned(
                markerId, owner, attempt);
        Map<String, Object> failed = new LinkedHashMap<>(owned.source());
        failed.put("copy_status", FAILED);
        failed.put("copy_failed_at", clock.instant().toString());
        failed.put("copy_failure", failure.getClass().getSimpleName());
        if (!markers.compareAndSet(markerId, owned, failed)) {
            throw new OpenSearchProjectionException(
                    "could not fail copy-forward marker " + markerId);
        }
    }

    private OpenSearchOperations.VersionedDocument requireOwned(
            String markerId,
            String owner,
            long attempt) {
        OpenSearchOperations.VersionedDocument marker = markers.get(markerId);
        if (marker == null
                || !COPYING.equals(status(marker))
                || !owner.equals(marker.source().get("copy_owner"))
                || attempt(marker) != attempt) {
            throw new OpenSearchProjectionException(
                    "copy-forward ownership changed for " + markerId);
        }
        return marker;
    }

    private long encodedBytes(
            String index,
            String id,
            Map<String, Object> document) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(id, "id");
        try {
            return BULK_ENVELOPE_BYTES
                    + index.getBytes(StandardCharsets.UTF_8).length
                    + id.getBytes(StandardCharsets.UTF_8).length
                    + objectMapper.writeValueAsBytes(document).length;
        } catch (JsonProcessingException failure) {
            throw new OpenSearchProjectionException(
                    "could not measure copy-forward bulk operation",
                    failure);
        }
    }

    private static String status(OpenSearchOperations.VersionedDocument marker) {
        return marker == null ? null : Objects.toString(marker.source().get("copy_status"), null);
    }

    private static long attempt(OpenSearchOperations.VersionedDocument marker) {
        Object value = marker.source().get("copy_attempt");
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        throw new OpenSearchProjectionException("copy-forward marker has no valid attempt");
    }

    record CopyUnit(
            ProjectionKind projectionKind,
            String logicalUnit,
            String targetIdentity) {

        CopyUnit {
            projectionKind = Objects.requireNonNull(projectionKind, "projectionKind");
            logicalUnit = requireText(logicalUnit, "logicalUnit");
            targetIdentity = requireText(targetIdentity, "targetIdentity");
        }
    }

    record CopyOperation(BulkOperation operation, long encodedBytes) {

        CopyOperation {
            operation = Objects.requireNonNull(operation, "operation");
            if (encodedBytes <= 0) {
                throw new IllegalArgumentException("encodedBytes must be positive");
            }
        }
    }

    interface MarkerStore {

        OpenSearchOperations.VersionedDocument get(String markerId);

        boolean create(String markerId, Map<String, Object> document);

        boolean compareAndSet(
                String markerId,
                OpenSearchOperations.VersionedDocument expected,
                Map<String, Object> document);
    }

    @FunctionalInterface
    interface PageScanner {

        void scan(
                String index,
                Query query,
                int limit,
                int pageSize,
                java.util.function.Consumer<List<OpenSearchScanner.StoredHit>> consumer);
    }

    @FunctionalInterface
    interface BulkSink {

        void write(List<BulkOperation> operations);
    }

    private final class BulkAccumulator {

        private final List<BulkOperation> operations = new ArrayList<>();
        private long bytes;

        void add(CopyOperation operation) {
            Objects.requireNonNull(operation, "operation");
            if (operation.encodedBytes() > maximumBytes) {
                throw new OpenSearchProjectionException(
                        "copy-forward operation exceeds byte limit " + maximumBytes);
            }
            if (!operations.isEmpty()
                    && (operations.size() >= maximumOperations
                            || bytes + operation.encodedBytes() > maximumBytes)) {
                flush();
            }
            operations.add(operation.operation());
            bytes += operation.encodedBytes();
        }

        void flush() {
            if (operations.isEmpty()) {
                return;
            }
            bulkSink.write(List.copyOf(operations));
            operations.clear();
            bytes = 0;
        }
    }

    private record OperationsMarkerStore(
            OpenSearchOperations operations,
            String controlIndex) implements MarkerStore {

        private OperationsMarkerStore {
            operations = Objects.requireNonNull(operations, "operations");
            controlIndex = requireText(controlIndex, "controlIndex");
        }

        @Override
        public OpenSearchOperations.VersionedDocument get(String markerId) {
            return operations.get(controlIndex, markerId);
        }

        @Override
        public boolean create(
                String markerId,
                Map<String, Object> document) {
            return operations.create(controlIndex, markerId, document);
        }

        @Override
        public boolean compareAndSet(
                String markerId,
                OpenSearchOperations.VersionedDocument expected,
                Map<String, Object> document) {
            return operations.compareAndSet(controlIndex, markerId, expected, document);
        }
    }

    private static String requireText(
            String value,
            String name) {
        String required = Objects.requireNonNull(value, name).strip();
        if (required.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }
}
