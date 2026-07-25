package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** PostgreSQL adapter for exact model-invocation cache entries. */
public final class PostgresModelInvocationCache implements ModelInvocationCache {

    private final PostgresGraphRagCacheStore delegate;

    public PostgresModelInvocationCache(
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
    public void invalidate(ProjectionNamespace namespace) {
        delegate.invalidate(namespace);
    }
}
