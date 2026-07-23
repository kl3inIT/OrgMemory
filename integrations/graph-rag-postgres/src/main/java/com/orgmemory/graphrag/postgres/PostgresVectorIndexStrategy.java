package com.orgmemory.graphrag.postgres;

/**
 * PostgreSQL vector index strategies supported by the GraphRAG projection.
 *
 * <p>The names intentionally follow LightRAG's PostgreSQL storage options so an
 * operator can move between the implementations without translating concepts.
 */
public enum PostgresVectorIndexStrategy {
    EXACT,
    HNSW,
    HNSW_HALFVEC,
    IVFFLAT,
    VCHORDRQ
}
