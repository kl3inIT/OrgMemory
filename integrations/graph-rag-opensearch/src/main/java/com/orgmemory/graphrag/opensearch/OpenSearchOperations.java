package com.orgmemory.graphrag.opensearch;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.httpclient5.ResponseException;

final class OpenSearchOperations {

    private final OpenSearchClient client;
    private final int bulkMaximumOperations;

    OpenSearchOperations(
            OpenSearchClient client,
            int bulkMaximumOperations) {
        this.client = Objects.requireNonNull(client, "client");
        if (bulkMaximumOperations <= 0) {
            throw new IllegalArgumentException("bulkMaximumOperations must be positive");
        }
        this.bulkMaximumOperations = bulkMaximumOperations;
    }

    OpenSearchClient client() {
        return client;
    }

    boolean indexExists(String index) {
        try {
            return client.indices()
                    .exists(request -> request
                            .index(index)
                            .allowNoIndices(true)
                            .ignoreUnavailable(true))
                    .value();
        } catch (IOException | OpenSearchException exception) {
            throw failure("check index " + index, exception);
        }
    }

    void ensureIndex(
            String index,
            Consumer<CreateIndexRequest.Builder> customizer) {
        try {
            if (client.indices().exists(request -> request.index(index)).value()) {
                return;
            }
            CreateIndexRequest.Builder request = new CreateIndexRequest.Builder().index(index);
            customizer.accept(request);
            try {
                client.indices().create(request.build());
            } catch (OpenSearchException conflict) {
                if (conflict.status() != 400
                        || !"resource_already_exists_exception"
                                .equals(conflict.error().type())) {
                    throw conflict;
                }
            } catch (IOException conflict) {
                if (!hasStatus(conflict, 400)
                        || !conflict.getMessage()
                                .contains("resource_already_exists_exception")) {
                    throw conflict;
                }
            }
        } catch (IOException | OpenSearchException exception) {
            throw failure("ensure index " + index, exception);
        }
    }

    void deleteIndex(String index) {
        try {
            client.indices().delete(request -> request
                    .index(index)
                    .allowNoIndices(true)
                    .ignoreUnavailable(true));
        } catch (IOException | OpenSearchException exception) {
            throw failure("delete index " + index, exception);
        }
    }

    void bulk(List<BulkOperation> operations) {
        if (operations.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < operations.size(); offset += bulkMaximumOperations) {
            int end = Math.min(offset + bulkMaximumOperations, operations.size());
            bulkBatch(operations.subList(offset, end));
        }
    }

    void index(
            String index,
            String id,
            Map<String, Object> document) {
        try {
            client.index(request -> request
                    .index(index)
                    .id(id)
                    .document(document)
                    .refresh(Refresh.WaitFor));
        } catch (IOException | OpenSearchException exception) {
            throw failure("index document " + index + "/" + id, exception);
        }
    }

    void deleteIfExists(
            String index,
            String id) {
        try {
            client.delete(request -> request
                    .index(index)
                    .id(id)
                    .refresh(Refresh.WaitFor));
        } catch (OpenSearchException missing) {
            if (missing.status() != 404) {
                throw failure("delete document " + index + "/" + id, missing);
            }
        } catch (IOException missing) {
            if (!hasStatus(missing, 404)) {
                throw failure("delete document " + index + "/" + id, missing);
            }
        }
    }

    VersionedDocument get(String index, String id) {
        try {
            var response = client.get(
                    request -> request.index(index).id(id),
                    Map.class);
            if (!response.found() || response.source() == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) response.source();
            return new VersionedDocument(
                    Map.copyOf(source),
                    response.seqNo(),
                    response.primaryTerm());
        } catch (IOException | OpenSearchException exception) {
            throw failure("get document " + index + "/" + id, exception);
        }
    }

    boolean create(
            String index,
            String id,
            Map<String, Object> document) {
        try {
            client.create(request -> request
                    .index(index)
                    .id(id)
                    .document(document)
                    .refresh(Refresh.WaitFor));
            return true;
        } catch (OpenSearchException conflict) {
            if (conflict.status() == 409) {
                return false;
            }
            throw failure("create document " + index + "/" + id, conflict);
        } catch (IOException exception) {
            if (hasStatus(exception, 409)) {
                return false;
            }
            throw failure("create document " + index + "/" + id, exception);
        }
    }

    boolean compareAndSet(
            String index,
            String id,
            VersionedDocument expected,
            Map<String, Object> document) {
        try {
            client.index(request -> request
                    .index(index)
                    .id(id)
                    .document(document)
                    .ifSeqNo(expected.sequenceNumber())
                    .ifPrimaryTerm(expected.primaryTerm())
                    .refresh(Refresh.WaitFor));
            return true;
        } catch (OpenSearchException conflict) {
            if (conflict.status() == 409) {
                return false;
            }
            throw failure("compare-and-set document " + index + "/" + id, conflict);
        } catch (IOException exception) {
            if (hasStatus(exception, 409)) {
                return false;
            }
            throw failure("compare-and-set document " + index + "/" + id, exception);
        }
    }

    private void bulkBatch(List<BulkOperation> operations) {
        try {
            var response = client.bulk(request -> request
                    .operations(operations)
                    .refresh(Refresh.WaitFor));
            if (!response.errors()) {
                return;
            }
            List<BulkResponseItem> failed = response.items().stream()
                    .filter(item -> item.error() != null)
                    .toList();
            String sample = failed.stream()
                    .limit(5)
                    .map(item -> item.operationType().jsonValue()
                            + "/"
                            + item.index()
                            + "/"
                            + item.id()
                            + "/"
                            + item.status()
                            + "/"
                            + item.error().reason())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unknown bulk failure");
            throw new OpenSearchProjectionException(
                    failed.size() + " OpenSearch bulk operations failed: " + sample);
        } catch (IOException | OpenSearchException exception) {
            throw failure("bulk projection write", exception);
        }
    }

    private static OpenSearchProjectionException failure(
            String operation,
            Exception cause) {
        return new OpenSearchProjectionException(
                "OpenSearch failed to " + operation,
                cause);
    }

    private static boolean hasStatus(
            Throwable failure,
            int status) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OpenSearchException openSearch
                    && openSearch.status() == status) {
                return true;
            }
            if (current instanceof ResponseException response
                    && response.status() == status) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    record VersionedDocument(
            Map<String, Object> source,
            long sequenceNumber,
            long primaryTerm) {

        VersionedDocument {
            source = Map.copyOf(Objects.requireNonNull(source, "source"));
            if (sequenceNumber < 0 || primaryTerm <= 0) {
                throw new IllegalArgumentException(
                        "OpenSearch concurrency tokens are invalid");
            }
        }
    }
}
