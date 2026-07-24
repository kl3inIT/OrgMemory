package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opensearch.client.opensearch._types.query_dsl.Query;

final class OpenSearchStoreSupport {

    private static final int TERMS_CHUNK_SIZE = 50_000;

    private OpenSearchStoreSupport() {
    }

    static void requireSameOrganization(
            ProjectionBatch batch,
            EvidenceReference evidence) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(evidence, "evidence");
        if (!batch.namespace().organizationId().equals(evidence.organizationId())) {
            throw new IllegalArgumentException(
                    "projection record belongs to another organization");
        }
    }

    static List<String> requireIds(Collection<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException(
                    "ids must contain only non-blank values");
        }
        return List.copyOf(ids);
    }

    static Query anyTerms(
            String field,
            Collection<String> values) {
        List<String> immutable = List.copyOf(Objects.requireNonNull(values, "values"));
        if (immutable.isEmpty()) {
            return Query.of(query -> query.matchNone(matchNone -> matchNone));
        }
        if (immutable.size() <= TERMS_CHUNK_SIZE) {
            return OpenSearchStagedIndex.terms(field, immutable);
        }
        List<Query> chunks = new ArrayList<>();
        for (int offset = 0; offset < immutable.size(); offset += TERMS_CHUNK_SIZE) {
            chunks.add(OpenSearchStagedIndex.terms(
                    field,
                    immutable.subList(
                            offset,
                            Math.min(offset + TERMS_CHUNK_SIZE, immutable.size()))));
        }
        return Query.of(query -> query.bool(bool -> bool
                .should(chunks)
                .minimumShouldMatch("1")));
    }
}
