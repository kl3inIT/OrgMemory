package com.orgmemory.graphrag.postgres;

import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Provisions the replaceable pgvector index layer.
 *
 * <p>The relational contribution ledger remains authoritative. Indexes are
 * rebuildable accelerators and may be changed without rewriting graph evidence.
 */
public final class PostgresGraphVectorIndexManager {

    private static final TableSpec[] TABLES = {
        new TableSpec(
                "graph_entity_embeddings",
                "content_vector",
                "embedding_dimensions"),
        new TableSpec(
                "graph_relation_embeddings",
                "content_vector",
                "embedding_dimensions"),
        new TableSpec(
                "projection_vector_records",
                "embedding",
                "dimensions")
    };

    private final JdbcTemplate jdbc;

    public PostgresGraphVectorIndexManager(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public void ensureConfiguredIndexes(PostgresGraphStoreOptions options) {
        Objects.requireNonNull(options, "options");
        ensurePgvector();
        if (options.vectorIndexStrategy() == PostgresVectorIndexStrategy.EXACT) {
            for (int dimension : options.indexedVectorDimensions()) {
                for (TableSpec table : TABLES) {
                    dropCompetingIndexes(table, dimension, null);
                }
            }
            return;
        }
        validateStrategySupport(options.vectorIndexStrategy(), options.indexedVectorDimensions());
        for (int dimension : options.indexedVectorDimensions()) {
            for (TableSpec table : TABLES) {
                String indexName =
                        indexName(table.table(), dimension, options.vectorIndexStrategy());
                dropCompetingIndexes(table, dimension, indexName);
                createIndex(table, dimension, indexName, options);
            }
        }
    }

    private void ensurePgvector() {
        Boolean installed = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class);
        if (!Boolean.TRUE.equals(installed)) {
            throw new IllegalStateException("pgvector extension is required");
        }
    }

    private void validateStrategySupport(
            PostgresVectorIndexStrategy strategy, Iterable<Integer> dimensions) {
        if (strategy == PostgresVectorIndexStrategy.VCHORDRQ) {
            Boolean installed = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vchord')",
                    Boolean.class);
            if (!Boolean.TRUE.equals(installed)) {
                throw new IllegalStateException(
                        "VCHORDRQ requires the vchord extension; choose another strategy or install it");
            }
        }
        for (int dimension : dimensions) {
            if ((strategy == PostgresVectorIndexStrategy.HNSW
                            || strategy == PostgresVectorIndexStrategy.IVFFLAT
                            || strategy == PostgresVectorIndexStrategy.VCHORDRQ)
                    && dimension > 2_000) {
                throw new IllegalArgumentException(
                        strategy + " with vector cosine operators supports at most 2000 dimensions; "
                                + "use HNSW_HALFVEC for larger embeddings");
            }
            if (strategy == PostgresVectorIndexStrategy.HNSW_HALFVEC && dimension > 4_000) {
                throw new IllegalArgumentException(
                        "HNSW_HALFVEC supports at most 4000 dimensions");
            }
        }
    }

    private void createIndex(
            TableSpec table,
            int dimension,
            String indexName,
            PostgresGraphStoreOptions options) {
        String expression;
        String using;
        String withClause;
        switch (options.vectorIndexStrategy()) {
            case HNSW -> {
                using = "hnsw";
                expression = "(" + table.vectorColumn() + "::vector("
                        + dimension + ")) vector_cosine_ops";
                withClause = " WITH (m = " + options.hnswM()
                        + ", ef_construction = " + options.hnswEfConstruction() + ")";
            }
            case HNSW_HALFVEC -> {
                using = "hnsw";
                expression = "(" + table.vectorColumn() + "::halfvec("
                        + dimension + ")) halfvec_cosine_ops";
                withClause = " WITH (m = " + options.hnswM()
                        + ", ef_construction = " + options.hnswEfConstruction() + ")";
            }
            case IVFFLAT -> {
                using = "ivfflat";
                expression = "(" + table.vectorColumn() + "::vector("
                        + dimension + ")) vector_cosine_ops";
                withClause = " WITH (lists = " + options.ivfFlatLists() + ")";
            }
            case VCHORDRQ -> {
                using = "vchordrq";
                expression = "(" + table.vectorColumn() + "::vector("
                        + dimension + ")) vector_cosine_ops";
                withClause = options.vchordBuildOptions().isBlank()
                        ? ""
                        : " WITH (options = " + quoteLiteral(options.vchordBuildOptions()) + ")";
            }
            case EXACT -> throw new IllegalStateException("EXACT does not create an index");
            default -> throw new IllegalStateException(
                    "unsupported vector index strategy " + options.vectorIndexStrategy());
        }
        jdbc.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + indexName
                + " ON " + table.table()
                + " USING " + using
                + " (" + expression + ")"
                + withClause
                + " WHERE " + table.dimensionsColumn() + " = " + dimension);
    }

    private void dropCompetingIndexes(
            TableSpec table, int dimension, String retainedIndexName) {
        String prefix =
                "idx_" + table.table().replace("graph_", "") + "_" + dimension + "_";
        jdbc.queryForList("""
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = current_schema()
                          AND tablename = ?
                          AND indexname LIKE ? ESCAPE '\\'
                        """,
                        String.class,
                        table.table(),
                        escapeLike(prefix) + "%\\_cosine")
                .stream()
                .filter(indexName -> !indexName.equals(retainedIndexName))
                .forEach(indexName -> jdbc.execute(
                        "DROP INDEX CONCURRENTLY IF EXISTS " + quoteIdentifier(indexName)));
    }

    private static String indexName(
            String table, int dimension, PostgresVectorIndexStrategy strategy) {
        return "idx_" + table.replace("graph_", "")
                + "_" + dimension + "_"
                + strategy.name().toLowerCase(Locale.ROOT)
                + "_cosine";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("%", "\\%");
    }

    private static String quoteIdentifier(String value) {
        if (!value.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("unsafe PostgreSQL identifier");
        }
        return '"' + value + '"';
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private record TableSpec(
            String table,
            String vectorColumn,
            String dimensionsColumn) {}
}
