package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.cache.RetrievalResultCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** PostgreSQL adapter for authorization- and publication-scoped retrieval results. */
public final class PostgresRetrievalResultCache implements RetrievalResultCache {

    private final PostgresGraphRagCacheStore delegate;

    public PostgresRetrievalResultCache(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        delegate = new PostgresGraphRagCacheStore(jdbc, transactionManager);
    }

    @Override
    public Optional<Entry> get(Key key, Instant now) {
        return delegate.get(key, now);
    }

    @Override
    public void put(Key key, Entry entry) {
        delegate.put(key, entry);
    }

    @Override
    public void invalidateNamespace(ProjectionNamespace namespace) {
        delegate.invalidateNamespace(namespace);
    }
}
