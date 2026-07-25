package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuildable read model for document-processing visibility.
 *
 * <p>This contract never owns leases, retries, cancellation, or recovery.
 * Those remain canonical worker-job responsibilities. Implementations index
 * observed state so operators and product surfaces can query it efficiently.
 */
public interface ProcessingStatusIndex {

    int MAXIMUM_PAGE_SIZE = 1_000;

    void upsert(StatusRecord record);

    void delete(UUID organizationId, UUID sourceRevisionId);

    Optional<StatusRecord> get(UUID organizationId, UUID sourceRevisionId);

    StatusPage search(UUID organizationId, StatusQuery query);

    record StatusRecord(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID workerJobId,
            State state,
            String contentSha256,
            String errorCode,
            Instant observedAt,
            Map<String, String> metadata) {

        public StatusRecord {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
            Objects.requireNonNull(workerJobId, "workerJobId");
            Objects.requireNonNull(state, "state");
            contentSha256 = requireSha256(contentSha256);
            errorCode = normalizeOptional(errorCode);
            Objects.requireNonNull(observedAt, "observedAt");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    record StatusQuery(
            Set<State> states,
            int limit,
            String cursor) {

        public StatusQuery {
            states = Set.copyOf(Objects.requireNonNull(states, "states"));
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
            if (limit > MAXIMUM_PAGE_SIZE) {
                throw new IllegalArgumentException(
                        "limit must not exceed " + MAXIMUM_PAGE_SIZE);
            }
            cursor = normalizeOptional(cursor);
        }
    }

    record StatusPage(List<StatusRecord> records, String nextCursor) {

        public StatusPage {
            records = List.copyOf(Objects.requireNonNull(records, "records"));
            nextCursor = normalizeOptional(nextCursor);
        }
    }

    enum State {
        QUEUED,
        PROCESSING,
        READY,
        FAILED,
        CANCELLED
    }

    private static String requireSha256(String value) {
        String normalized = Objects.requireNonNull(value, "contentSha256").strip();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "contentSha256 must be lowercase SHA-256 hex");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
