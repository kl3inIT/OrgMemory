package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.storage.ProcessingStatusIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;

public final class OpenSearchProcessingStatusIndex implements ProcessingStatusIndex {

    private final OpenSearchOperations operations;
    private final String index;

    OpenSearchProcessingStatusIndex(
            OpenSearchOperations operations,
            OpenSearchIndexNames indexes) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.index = Objects.requireNonNull(indexes, "indexes").status();
        operations.ensureIndex(index, OpenSearchSchemas.status());
    }

    @Override
    public void upsert(StatusRecord record) {
        Objects.requireNonNull(record, "record");
        operations.index(
                index,
                id(record.organizationId(), record.sourceRevisionId()),
                OpenSearchProjectionCodec.status(record));
    }

    @Override
    public void delete(
            UUID organizationId,
            UUID sourceRevisionId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        operations.deleteIfExists(index, id(organizationId, sourceRevisionId));
    }

    @Override
    public Optional<StatusRecord> get(
            UUID organizationId,
            UUID sourceRevisionId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        OpenSearchOperations.VersionedDocument document =
                operations.get(index, id(organizationId, sourceRevisionId));
        return document == null
                ? Optional.empty()
                : Optional.of(OpenSearchProjectionCodec.status(document.source()));
    }

    @Override
    public StatusPage search(
            UUID organizationId,
            StatusQuery query) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(query, "query");
        Cursor cursor = decodeCursor(query.cursor());
        List<Query> filters = new ArrayList<>();
        filters.add(OpenSearchStagedIndex.term(
                OpenSearchProjectionCodec.ORGANIZATION_ID,
                organizationId.toString()));
        if (!query.states().isEmpty()) {
            filters.add(OpenSearchStagedIndex.terms(
                    "status",
                    query.states().stream().map(Enum::name).toList()));
        }
        try {
            var builder = new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                    .index(index)
                    .size(query.limit() + 1)
                    .query(candidate -> candidate.bool(bool -> bool.filter(filters)))
                    .sort(sort -> sort.field(field -> field
                            .field("observed_at")
                            .order(SortOrder.Desc)))
                    .sort(sort -> sort.field(field -> field
                            .field(OpenSearchProjectionCodec.REVISION_ID)
                            .order(SortOrder.Asc)));
            if (cursor != null) {
                builder.searchAfter(
                        FieldValue.of(cursor.observedAt().toEpochMilli()),
                        FieldValue.of(cursor.sourceRevisionId().toString()));
            }
            var response = operations.client().search(builder.build(), Map.class);
            List<StatusRecord> records = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                records.add(OpenSearchProjectionCodec.status(source));
            }
            if (records.size() <= query.limit()) {
                return new StatusPage(records, null);
            }
            List<StatusRecord> page = List.copyOf(records.subList(0, query.limit()));
            StatusRecord last = page.getLast();
            return new StatusPage(
                    page,
                    encodeCursor(last.observedAt(), last.sourceRevisionId()));
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to search processing status",
                    exception);
        }
    }

    private static String id(
            UUID organizationId,
            UUID sourceRevisionId) {
        return organizationId + ":" + sourceRevisionId;
    }

    private static String encodeCursor(
            Instant observedAt,
            UUID sourceRevisionId) {
        String value = observedAt.toEpochMilli() + "\n" + sourceRevisionId;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            int separator = value.indexOf('\n');
            if (separator < 1 || separator == value.length() - 1) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(value.substring(0, separator))),
                    UUID.fromString(value.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid processing status cursor", exception);
        }
    }

    private record Cursor(Instant observedAt, UUID sourceRevisionId) {
    }
}
