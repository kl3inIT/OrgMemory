package com.orgmemory.graphrag.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class AuthorizedMultiSnapshotQueryIntegrationTests {

    private static final int[] SPACE_COUNTS = {1, 7, 20};
    private static final int REPETITIONS = 5;
    private static final int STATEMENT_TIMEOUT_MS = 5_000;
    private static final double LATENCY_THRESHOLD_MS = 500.0;
    private static final Pattern NUMBER_FIELD = Pattern.compile(
            "\\\"%s\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern NODE_TYPE = Pattern.compile(
            "\\\"Node Type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static HikariDataSource dataSource;
    private static MultiSnapshotSyntheticDataset fixture;
    private static AuthorizedMultiSnapshotQuery query;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(4);
        config.setPoolName("msq-benchmark");
        dataSource = new HikariDataSource(config);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        fixture = new MultiSnapshotSyntheticDataset(dataSource);
        query = new AuthorizedMultiSnapshotQuery(dataSource);
    }

    @AfterAll
    static void closePool() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @Order(1)
    void compoundQueryMatchesPerSpaceCandidatesAndAttribution() {
        MultiSnapshotSyntheticDataset.Dataset dataset =
                fixture.load(MultiSnapshotSyntheticDataset.Scale.CURRENT);

        for (int spaceCount : SPACE_COUNTS) {
            for (MultiSnapshotSyntheticDataset.Grant grant :
                    MultiSnapshotSyntheticDataset.Grant.values()) {
                AuthorizedMultiSnapshotQuery.Request request =
                        dataset.request(spaceCount, grant);
                AuthorizedMultiSnapshotQuery.QueryExecution compound = query.execute(request);
                AuthorizedMultiSnapshotQuery.QueryExecution perSpace =
                        query.executePerSpace(request);

                query.validateRows(request, compound.rows());
                query.validateRows(request, perSpace.rows());
                assertThat(compound.rows())
                        .as("compound equivalence for %s spaces and %s grants", spaceCount, grant)
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyElementsOf(perSpace.rows());
            }
        }
    }

    @Test
    @Order(2)
    @Tag("benchmark")
    @EnabledIfEnvironmentVariable(named = "ORGMEMORY_RUN_MSQ_BENCHMARK", matches = "true")
    void measuresThePredeclaredLatencyMatrixWithoutChangingRuntimeWiring() throws IOException {
        List<ScenarioResult> results = new ArrayList<>();
        List<ExplainHighlight> explains = new ArrayList<>();
        List<DatasetSummary> datasets = new ArrayList<>();
        Path report = Path.of("build", "reports", "msq", "results.md");
        Files.createDirectories(report.getParent());

        for (MultiSnapshotSyntheticDataset.Scale scale :
                MultiSnapshotSyntheticDataset.Scale.values()) {
            MultiSnapshotSyntheticDataset.Dataset dataset = fixture.load(scale);
            datasets.add(new DatasetSummary(
                    scale.label(),
                    dataset.vectorCount(),
                    dataset.entityContributionCount(),
                    dataset.relationContributionCount()));
            for (int spaceCount : SPACE_COUNTS) {
                for (MultiSnapshotSyntheticDataset.Grant grant :
                        MultiSnapshotSyntheticDataset.Grant.values()) {
                    AuthorizedMultiSnapshotQuery.Request request =
                            dataset.request(spaceCount, grant);
                    explains.add(explain(scale.label(), spaceCount, grant.name(), request));

                    for (int concurrency : List.of(1, 4)) {
                        results.add(measure(
                                scale.label(),
                                spaceCount,
                                grant,
                                concurrency,
                                Phase.COLD,
                                request));
                        Files.writeString(report, markdown(datasets, results, explains));
                        try {
                            query.execute(request, STATEMENT_TIMEOUT_MS);
                            query.execute(request, STATEMENT_TIMEOUT_MS);
                        } catch (RuntimeException failure) {
                            if (!isStatementTimeout(failure)) {
                                throw failure;
                            }
                        }
                        results.add(measure(
                                scale.label(),
                                spaceCount,
                                grant,
                                concurrency,
                                Phase.WARM,
                                request));
                        Files.writeString(report, markdown(datasets, results, explains));
                    }
                }
            }
        }

        Files.writeString(report, markdown(datasets, results, explains));
        assertThat(results).hasSize(72);
    }

    private static ScenarioResult measure(
            String scale,
            int spaceCount,
            MultiSnapshotSyntheticDataset.Grant grant,
            int concurrency,
            Phase phase,
            AuthorizedMultiSnapshotQuery.Request request) {
        int samples = REPETITIONS;
        List<Timing> compound = concurrentMeasurements(
                concurrency,
                samples,
                () -> query.execute(request, STATEMENT_TIMEOUT_MS),
                phase);
        Statistics compoundStats = Statistics.from(compound);
        return new ScenarioResult(
                scale,
                spaceCount,
                grant.name(),
                concurrency,
                phase.name(),
                samples,
                compoundStats,
                compoundStats.p95Ms() <= LATENCY_THRESHOLD_MS);
    }

    private static List<Timing> concurrentMeasurements(
            int concurrency,
            int samples,
            Supplier<AuthorizedMultiSnapshotQuery.QueryExecution> operation,
            Phase phase) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Callable<Timing>> tasks = new ArrayList<>();
            for (int sample = 0; sample < samples; sample++) {
                tasks.add(() -> {
                    start.await();
                    if (phase == Phase.COLD) {
                        discardPlans();
                    }
                    try {
                        AuthorizedMultiSnapshotQuery.QueryExecution execution = operation.get();
                        return new Timing(
                                execution.queryNanos() / 1_000_000.0,
                                execution.connectionWaitNanos() / 1_000_000.0);
                    } catch (RuntimeException failure) {
                        if (isStatementTimeout(failure)) {
                            return new Timing(STATEMENT_TIMEOUT_MS, 0.0);
                        }
                        throw failure;
                    }
                });
            }
            List<Future<Timing>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            start.countDown();
            List<Timing> timings = new ArrayList<>(samples);
            for (Future<Timing> future : futures) {
                timings.add(future.get());
            }
            return List.copyOf(timings);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("benchmark interrupted", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("benchmark execution failed", failure);
        }
    }

    private static void discardPlans() {
        new JdbcTemplate(dataSource).execute("DISCARD PLANS");
    }

    private static boolean isStatementTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql
                    && "57014".equals(sql.getSQLState())
                    && sql.getMessage().toLowerCase(Locale.ROOT).contains("statement timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ExplainHighlight explain(
            String scale,
            int spaces,
            String grant,
            AuthorizedMultiSnapshotQuery.Request request) {
        try {
            return ExplainHighlight.from(
                    scale,
                    spaces,
                    grant,
                    query.explain(request, STATEMENT_TIMEOUT_MS));
        } catch (RuntimeException failure) {
            if (isStatementTimeout(failure)) {
                return ExplainHighlight.timedOut(scale, spaces, grant);
            }
            throw failure;
        }
    }

    private static String markdown(
            List<DatasetSummary> datasets,
            List<ScenarioResult> results,
            List<ExplainHighlight> explains) {
        StringBuilder markdown = new StringBuilder()
                .append("# ADR 0020 Phase 2 local benchmark results\n\n")
                .append("Generated: ")
                .append(Instant.now())
                .append("\n\n")
                .append("Environment: disposable Testcontainers PostgreSQL 18 + pgvector, ")
                .append("fixed four-connection Hikari pool, fixed synthetic seed, 1536 dimensions. ")
                .append("No Spring runtime query bean was registered and no non-Testcontainers database ")
                .append("was reachable. `COLD` discards prepared plans before each sample; PostgreSQL shared ")
                .append("buffers are not evicted, so production-shaped cold-buffer evidence remains plan step 4.\n\n")
                .append("Statements are capped at 5,000 ms, ten times the gate budget. A timeout is ")
                .append("recorded as 5,000 ms and therefore fails the 500 ms threshold.\n\n")
                .append("Threshold: compound-query p95 <= 500 ms for every row. Five repetitions per ")
                .append("row are measured; concurrency 4 launches up to four samples together. Shadow equivalence ")
                .append("passed separately across 1/7/20 spaces and narrow/broad grants.\n\n")
                .append("## Dataset\n\n")
                .append("| Scale | Vectors | Entity contributions | Relation contributions |\n")
                .append("|---|---:|---:|---:|\n");
        for (DatasetSummary dataset : datasets) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %d |%n",
                    dataset.scale(),
                    dataset.vectors(),
                    dataset.entityContributions(),
                    dataset.relationContributions()));
        }
        markdown.append("\n## Scenario measurements\n\n")
                .append("| Scale | Spaces | Grant | Concurrency | Phase | Samples | Compound p50 | ")
                .append("Compound p95 | Compound p99 | Pool wait p95 | Gate |\n")
                .append("|---|---:|---|---:|---|---:|---:|---:|---:|---:|---|\n");
        for (ScenarioResult result : results) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %s | %d | %s | %d | %.2f ms | %.2f ms | %.2f ms | %.2f ms | %s |%n",
                    result.scale(),
                    result.spaces(),
                    result.grant(),
                    result.concurrency(),
                    result.phase(),
                    result.samples(),
                    result.compound().p50Ms(),
                    result.compound().p95Ms(),
                    result.compound().p99Ms(),
                    result.compound().poolWaitP95Ms(),
                    result.passed() ? "PASS" : "FAIL"));
        }
        markdown.append("\n## EXPLAIN (ANALYZE, BUFFERS) highlights\n\n")
                .append("| Scale | Spaces | Grant | Root node | Planning | Execution | Shared hits | Shared reads |\n")
                .append("|---|---:|---|---|---:|---:|---:|---:|\n");
        for (ExplainHighlight explain : explains) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %s | %s | %.2f ms | %.2f ms | %.0f | %.0f |%n",
                    explain.scale(),
                    explain.spaces(),
                    explain.grant(),
                    explain.rootNode(),
                    explain.planningMs(),
                    explain.executionMs(),
                    explain.sharedHits(),
                    explain.sharedReads()));
        }
        long failures = results.stream().filter(result -> !result.passed()).count();
        markdown.append("\n## Local latency verdict\n\n")
                .append(failures == 0
                        ? "PASS: all " + results.size()
                                + " completed local synthetic scenarios met the predeclared p95 threshold."
                        : "FAIL: " + failures
                                + " of " + results.size()
                                + " completed local synthetic scenarios exceeded the predeclared p95 threshold.")
                .append(" This is Phase 2 local evidence only; the restored-copy ZM run remains plan step 4.\n");
        return markdown.toString();
    }

    private enum Phase {
        COLD,
        WARM
    }

    private record Timing(double queryMs, double poolWaitMs) {}

    private record Statistics(
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double poolWaitP95Ms) {

        static Statistics from(List<Timing> timings) {
            List<Double> query = timings.stream().map(Timing::queryMs).sorted().toList();
            List<Double> waits = timings.stream().map(Timing::poolWaitMs).sorted().toList();
            return new Statistics(
                    percentile(query, 0.50),
                    percentile(query, 0.95),
                    percentile(query, 0.99),
                    percentile(waits, 0.95));
        }

        private static double percentile(List<Double> sorted, double percentile) {
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
            return sorted.get(index);
        }
    }

    private record ScenarioResult(
            String scale,
            int spaces,
            String grant,
            int concurrency,
            String phase,
            int samples,
            Statistics compound,
            boolean passed) {}

    private record DatasetSummary(
            String scale,
            int vectors,
            int entityContributions,
            int relationContributions) {}

    private record ExplainHighlight(
            String scale,
            int spaces,
            String grant,
            String rootNode,
            double planningMs,
            double executionMs,
            double sharedHits,
            double sharedReads) {

        static ExplainHighlight from(String scale, int spaces, String grant, String json) {
            Matcher node = NODE_TYPE.matcher(json);
            return new ExplainHighlight(
                    scale,
                    spaces,
                    grant,
                    node.find() ? node.group(1) : "unknown",
                    number(json, "Planning Time"),
                    number(json, "Execution Time"),
                    number(json, "Shared Hit Blocks"),
                    number(json, "Shared Read Blocks"));
        }

        static ExplainHighlight timedOut(String scale, int spaces, String grant) {
            return new ExplainHighlight(
                    scale,
                    spaces,
                    grant,
                    "statement timeout",
                    0.0,
                    STATEMENT_TIMEOUT_MS,
                    0.0,
                    0.0);
        }

        private static double number(String json, String field) {
            Matcher matcher = Pattern.compile(String.format(
                            Locale.ROOT, NUMBER_FIELD.pattern(), Pattern.quote(field)))
                    .matcher(json);
            return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
        }
    }
}
