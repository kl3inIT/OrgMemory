package com.orgmemory.graphrag.postgres;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

final class PostgresBatchOperations {

    static final int DEFAULT_BATCH_SIZE = 500;

    private PostgresBatchOperations() {}

    static <T> void batchUpdate(
            NamedParameterJdbcTemplate jdbc,
            String sql,
            List<T> rows,
            int batchSize,
            BiFunction<Integer, T, SqlParameterSource> parameterFactory) {
        Objects.requireNonNull(jdbc, "jdbc");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(parameterFactory, "parameterFactory");
        requireBatchSize(batchSize);
        for (int start = 0; start < rows.size(); start += batchSize) {
            int end = Math.min(rows.size(), start + batchSize);
            SqlParameterSource[] parameters = new SqlParameterSource[end - start];
            for (int index = start; index < end; index++) {
                parameters[index - start] = parameterFactory.apply(index, rows.get(index));
            }
            jdbc.batchUpdate(sql, parameters);
        }
    }

    static int requireBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        return batchSize;
    }
}
