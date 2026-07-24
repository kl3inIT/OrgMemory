package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class OpenSearchContentStore implements ContentStore {

    private final OpenSearchStagedIndex staged;

    OpenSearchContentStore(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes) {
        Objects.requireNonNull(operations, "operations")
                .ensureIndex(indexes.content(), OpenSearchSchemas.content());
        this.staged = new OpenSearchStagedIndex(
                operations,
                publications,
                indexes.control(),
                indexes.content(),
                ProjectionKind.CONTENT);
    }

    @Override
    public void stageUpsert(
            ProjectionBatch batch,
            Collection<ContentRecord> records) {
        List<ContentRecord> immutable = List.copyOf(Objects.requireNonNull(records, "records"));
        immutable.forEach(record -> OpenSearchStoreSupport.requireSameOrganization(
                batch, record.evidence()));
        staged.stageUpsert(
                batch,
                immutable.stream()
                        .map(record -> OpenSearchProjectionCodec.content(batch, record))
                        .toList());
    }

    @Override
    public void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        staged.stageDelete(batch, OpenSearchStoreSupport.requireIds(ids));
    }

    @Override
    public Optional<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            String id) {
        return get(scope, snapshot, List.of(Objects.requireNonNull(id, "id")))
                .stream()
                .findFirst();
    }

    @Override
    public List<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids) {
        List<String> immutable = OpenSearchStoreSupport.requireIds(ids);
        return staged.load(scope, snapshot, immutable).stream()
                .map(OpenSearchProjectionCodec::content)
                .toList();
    }

    @Override
    public void discard(ProjectionBatch batch) {
        staged.discard(batch);
    }
}
