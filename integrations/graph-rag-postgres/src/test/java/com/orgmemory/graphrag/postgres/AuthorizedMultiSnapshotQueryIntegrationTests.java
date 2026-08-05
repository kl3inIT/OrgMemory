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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class AuthorizedMultiSnapshotQueryIntegrationTests {

    private static final int[] SPACE_COUNTS = {1, 7, 20};
    private static final int REPETITIONS = 5;
    private static final int STATEMENT_TIMEOUT_MS = 5_000;
    private static final double LATENCY_THRESHOLD_MS = 500.0;
    private static final ObjectMapper JSON = new ObjectMapper();

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
                int authorizedAssetsPerSpace = grant == MultiSnapshotSyntheticDataset.Grant.NARROW
                        ? 1
                        : MultiSnapshotSyntheticDataset.ASSETS_PER_SPACE;
                int expectedCandidates = Math.min(
                        request.globalLimit(),
                        spaceCount
                                * authorizedAssetsPerSpace
                                * MultiSnapshotSyntheticDataset.BASE_ENTITIES_PER_ASSET);
                assertThat(compound.rows())
                        .as("non-empty compound candidates for %s spaces and %s grants", spaceCount, grant)
                        .hasSize(expectedCandidates);
                assertThat(perSpace.rows())
                        .as("non-empty per-space candidates for %s spaces and %s grants", spaceCount, grant)
                        .hasSize(expectedCandidates);
                assertThat(compound.rows())
                        .as("compound equivalence for %s spaces and %s grants", spaceCount, grant)
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyElementsOf(perSpace.rows());
            }
        }
    }

    @Test
    @Order(2)
    void rejectsMismatchedAclGeneration() {
        MultiSnapshotSyntheticDataset.Dataset dataset =
                fixture.load(MultiSnapshotSyntheticDataset.Scale.CURRENT);
        AuthorizedMultiSnapshotQuery.Request request =
                dataset.request(1, MultiSnapshotSyntheticDataset.Grant.NARROW);
        AuthorizedMultiSnapshotQuery.SnapshotScope scope = request.scopes().getFirst();
        AuthorizedMultiSnapshotQuery.SnapshotScope mismatched = new AuthorizedMultiSnapshotQuery.SnapshotScope(
                scope.spaceId(),
                scope.batchId(),
                scope.generation(),
                scope.manifestFingerprint(),
                scope.aclGeneration() + 1,
                scope.authorizedAssetIds());

        assertThat(query.execute(withScope(request, mismatched)).rows()).isEmpty();
    }

    @Test
    @Order(3)
    void rejectsProjectionNameSubstrings() {
        MultiSnapshotSyntheticDataset.Dataset dataset =
                fixture.load(MultiSnapshotSyntheticDataset.Scale.CURRENT);
        AuthorizedMultiSnapshotQuery.Request request =
                dataset.request(1, MultiSnapshotSyntheticDataset.Grant.NARROW);
        UUID batchId = request.scopes().getFirst().batchId();
        new JdbcTemplate(dataSource).update(
                "UPDATE projection_publications SET projections = ? WHERE batch_id = ?",
                "GRAPHQL,VECTORIZED",
                batchId);

        assertThat(query.execute(request).rows()).isEmpty();
    }

    @Test
    @Order(4)
    void serializesManifestFingerprintAsJson() {
        MultiSnapshotSyntheticDataset.Dataset dataset =
                fixture.load(MultiSnapshotSyntheticDataset.Scale.CURRENT);
        AuthorizedMultiSnapshotQuery.Request request =
                dataset.request(1, MultiSnapshotSyntheticDataset.Grant.NARROW);
        AuthorizedMultiSnapshotQuery.SnapshotScope scope = request.scopes().getFirst();
        String escapedFingerprint = "msq-\\\"quoted\\\"-\\\\fingerprint";
        new JdbcTemplate(dataSource).update(
                "UPDATE projection_publications SET manifest_fingerprint = ? WHERE batch_id = ?",
                escapedFingerprint,
                scope.batchId());
        AuthorizedMultiSnapshotQuery.SnapshotScope escaped = new AuthorizedMultiSnapshotQuery.SnapshotScope(
                scope.spaceId(),
                scope.batchId(),
                scope.generation(),
                escapedFingerprint,
                scope.aclGeneration(),
                scope.authorizedAssetIds());
        AuthorizedMultiSnapshotQuery.Request escapedRequest = withScope(request, escaped);

        AuthorizedMultiSnapshotQuery.QueryExecution execution = query.execute(escapedRequest);
        query.validateRows(escapedRequest, execution.rows());
        assertThat(execution.rows()).isNotEmpty();
    }

    @Test
    @Order(5)
    void parsesExplainJsonFromItsDocumentStructure() {
        ExplainHighlight highlight = ExplainHighlight.from(
                "1x",
                1,
                "NARROW",
                """
                [{
                  "Plan": {
                    "Node Type": "Sort",
                    "Shared Hit Blocks": 23,
                    "Shared Read Blocks": 4,
                    "Plans": [{"Node Type": "Nested Loop", "Shared Hit Blocks": 99}]
                  },
                  "Planning Time": 1.25,
                  "Execution Time": 7.5
                }]
                """);

        assertThat(highlight.rootNode()).isEqualTo("Sort");
        assertThat(highlight.planningMs()).isEqualTo(1.25);
        assertThat(highlight.executionMs()).isEqualTo(7.5);
        assertThat(highlight.sharedHits()).isEqualTo(23.0);
        assertThat(highlight.sharedReads()).isEqualTo(4.0);
    }

    @Test
    @Order(100)
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

    private static AuthorizedMultiSnapshotQuery.Request withScope(
            AuthorizedMultiSnapshotQuery.Request request,
            AuthorizedMultiSnapshotQuery.SnapshotScope scope) {
        return new AuthorizedMultiSnapshotQuery.Request(
                request.organizationId(),
                request.embeddingProfileId(),
                request.queryVector(),
                request.seedLimit(),
                request.globalLimit(),
                request.minimumSimilarity(),
                List.of(scope));
    }

    private static ScenarioResult measure(
            String scale,
            int spaceCount,
            MultiSnapshotSyntheticDataset.Grant grant,
            int concurrency,
            Phase phase,
            AuthorizedMultiSnapshotQuery.Request request) {
        int samples = REPETITIONS;
        Supplier<AuthorizedMultiSnapshotQuery.QueryExecution> operation = phase == Phase.COLD
                ? () -> query.executeAfterDiscardingPlans(request, STATEMENT_TIMEOUT_MS)
                : () -> query.execute(request, STATEMENT_TIMEOUT_MS);
        List<Timing> compound = concurrentMeasurements(
                concurrency,
                samples,
                operation);
        Statistics compoundStats = Statistics.from(compound);
        return new ScenarioResult(
                scale,
                spaceCount,
                grant.name(),
                concurrency,
                phase.name(),
                samples,
                compoundStats,
                compoundStats.maxMs() <= LATENCY_THRESHOLD_MS);
    }

    private static List<Timing> concurrentMeasurements(
            int concurrency,
            int samples,
            Supplier<AuthorizedMultiSnapshotQuery.QueryExecution> operation) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Callable<Timing>> tasks = new ArrayList<>();
            for (int sample = 0; sample < samples; sample++) {
                tasks.add(() -> {
                    start.await();
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

    private static boolean isStatementTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql && "57014".equals(sql.getSQLState())) {
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
                .append("was reachable. `COLD` discards prepared plans on the measured connection before each ")
                .append("sample; PostgreSQL shared ")
                .append("buffers are not evicted, so production-shaped cold-buffer evidence remains plan step 4.\n\n")
                .append("Statements are capped at 5,000 ms, ten times the gate budget. A timeout is ")
                .append("recorded as 5,000 ms and therefore fails the 500 ms threshold.\n\n")
                .append("Observed-sample check: compound-query max-of-5 <= 500 ms for every row. Five ")
                .append("repetitions do not estimate p95 or p99; the median and observed maximum are reported ")
                .append("without percentile labels. The binding ADR p95 <= 500 ms gate is not weakened. ")
                .append("Concurrency 4 launches up to four samples together. Shadow equivalence ")
                .append("passed separately across 1/7/20 spaces and narrow/broad grants.\n\n")
                .append("## Dataset\n\n")
                .append("| Scale | Vectors | Entity contributions | Relation contributions |\n")
                .append("|---|---:|---:|---:|\n");
        for (DatasetSummary dataset : datasets) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %d |\n",
                    dataset.scale(),
                    dataset.vectors(),
                    dataset.entityContributions(),
                    dataset.relationContributions()));
        }
        markdown.append("\n## Scenario measurements\n\n")
                .append("| Scale | Spaces | Grant | Concurrency | Phase | Samples | Compound median | ")
                .append("Compound max | Pool wait max | Max-of-5 check |\n")
                .append("|---|---:|---|---:|---|---:|---:|---:|---:|---|\n");
        for (ScenarioResult result : results) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %s | %d | %s | %d | %.2f ms | %.2f ms | %.2f ms | %s |\n",
                    result.scale(),
                    result.spaces(),
                    result.grant(),
                    result.concurrency(),
                    result.phase(),
                    result.samples(),
                    result.compound().medianMs(),
                    result.compound().maxMs(),
                    result.compound().poolWaitMaxMs(),
                    result.passed() ? "PASS" : "FAIL"));
        }
        markdown.append("\n## EXPLAIN (ANALYZE, BUFFERS) highlights\n\n")
                .append("| Scale | Spaces | Grant | Root node | Planning | Execution | Shared hits | Shared reads |\n")
                .append("|---|---:|---|---|---:|---:|---:|---:|\n");
        for (ExplainHighlight explain : explains) {
            markdown.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %s | %s | %.2f ms | %.2f ms | %.0f | %.0f |\n",
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
        long medianFailures = results.stream()
                .filter(result -> result.compound().medianMs() > LATENCY_THRESHOLD_MS)
                .count();
        markdown.append("\n## Local latency verdict\n\n")
                .append(failures == 0
                        ? "PASS: all " + results.size()
                                + " completed local synthetic scenarios met the observed max-of-5 check."
                        : "FAIL: " + failures
                                + " of " + results.size()
                                + " completed local synthetic scenarios exceeded the observed max-of-5 check; "
                                + medianFailures + " rows also exceeded 500 ms at the median. A median above "
                                + "500 ms falsifies the binding p95 <= 500 ms gate without relying on a p95 "
                                + "estimate from five samples.")
                .append(" This is Phase 2 local evidence only; the restored-copy ZM run remains plan step 4.\n");
        return markdown.toString();
    }

    private enum Phase {
        COLD,
        WARM
    }

    private record Timing(double queryMs, double poolWaitMs) {}

    private record Statistics(
            double medianMs,
            double maxMs,
            double poolWaitMaxMs) {

        static Statistics from(List<Timing> timings) {
            List<Double> query = timings.stream().map(Timing::queryMs).sorted().toList();
            List<Double> waits = timings.stream().map(Timing::poolWaitMs).sorted().toList();
            return new Statistics(
                    percentile(query, 0.50),
                    query.getLast(),
                    waits.getLast());
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
            try {
                JsonNode document = JSON.readTree(json).path(0);
                JsonNode plan = document.path("Plan");
                JsonNode nodeType = plan.path("Node Type");
                return new ExplainHighlight(
                        scale,
                        spaces,
                        grant,
                        nodeType.isTextual() ? nodeType.asText() : "unknown",
                        number(document, "Planning Time"),
                        number(document, "Execution Time"),
                        number(plan, "Shared Hit Blocks"),
                        number(plan, "Shared Read Blocks"));
            } catch (JacksonException failure) {
                throw new IllegalArgumentException("EXPLAIN returned invalid JSON", failure);
            }
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

        private static double number(JsonNode node, String field) {
            JsonNode value = node.path(field);
            return value.isNumber() ? value.doubleValue() : 0.0;
        }
    }
}
