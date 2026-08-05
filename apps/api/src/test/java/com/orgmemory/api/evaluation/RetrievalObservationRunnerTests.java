package com.orgmemory.api.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.UserRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RetrievalObservationRunnerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesScorerCompatibleObservationsForEveryAllowCase() throws Exception {
        Path root = repositoryRoot();
        Path output = temporaryDirectory.resolve("observations.json");
        ObjectMapper json = new ObjectMapper();
        GraphRagKnowledgeRetrievalService retrieval =
                mock(GraphRagKnowledgeRetrievalService.class);
        var document = new GraphRagKnowledgeRetrievalService.RetrievedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DOC001 - Sổ tay nhân viên.md");
        var nonBenchmarkDocument = new GraphRagKnowledgeRetrievalService.RetrievedDocument(
                UUID.randomUUID(),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "internal.txt");
        when(retrieval.observe(any(), anyString(), anyString())).thenReturn(
                new GraphRagKnowledgeRetrievalService.RetrievalObservation(
                        List.of(nonBenchmarkDocument, document),
                        List.of(nonBenchmarkDocument, document),
                        new GraphRagKnowledgeRetrievalService.KeywordPlanSnapshot(
                                List.of("chính sách"),
                                List.of("thử việc"),
                                "model")));
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findById(any())).thenReturn(Optional.of(new AppUser(
                ORGANIZATION_ID,
                null,
                "Fixture User",
                "fixture@example.test",
                UserRole.EMPLOYEE)));
        RetrievalObservationRunner runner = new RetrievalObservationRunner(
                new RetrievalObservationProperties(
                        true,
                        root.resolve("demo/fixtures/public-evaluation.json"),
                        root.resolve("demo/fixtures/documents/manifest.json"),
                        output,
                        "orgmemory_retrieval_observation"),
                retrieval,
                users,
                json,
                dataSource("orgmemory_retrieval_observation"),
                safeEnvironment());

        runner.run(mock(ApplicationArguments.class));

        JsonNode written = json.readTree(output.toFile());
        assertEquals("orgmemory.retrieval-observations.v2", written.get("schema_version").asText());
        assertEquals(43, written.get("observations").size());
        JsonNode p031 = findCase(written, "P031");
        assertEquals(2, p031.get("keyword_seeded_golden_ranks").get("DOC001").asInt());
        assertTrue(p031.get("keyword_seeded_document_ids").get(0).asText().startsWith("source:"));
        assertEquals(
                "model",
                p031.get("keyword_plan").get("source").asText());
    }

    @Test
    void refusesTheLiveDatabaseNameBeforeReadingFixtures() throws Exception {
        Path missing = temporaryDirectory.resolve("missing.json");
        RetrievalObservationRunner runner = new RetrievalObservationRunner(
                new RetrievalObservationProperties(
                        true,
                        missing,
                        missing,
                        temporaryDirectory.resolve("output.json"),
                        "orgmemory"),
                mock(GraphRagKnowledgeRetrievalService.class),
                mock(AppUserRepository.class),
                new ObjectMapper(),
                dataSource("orgmemory"),
                safeEnvironment());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> runner.run(mock(ApplicationArguments.class)));

        assertEquals(
                "retrieval observations refuse the live orgmemory database",
                failure.getMessage());
        assertEquals(false, Files.exists(temporaryDirectory.resolve("output.json")));
    }

    private static JsonNode findCase(JsonNode output, String caseId) {
        for (JsonNode observation : output.get("observations")) {
            if (caseId.equals(observation.get("case_id").asText())) {
                return observation;
            }
        }
        throw new AssertionError("missing case " + caseId);
    }

    private static Environment safeEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.flyway.enabled", Boolean.class, true))
                .thenReturn(false);
        when(environment.getProperty(
                        "orgmemory.graph-rag.postgres.provision-indexes",
                        Boolean.class,
                        true))
                .thenReturn(false);
        when(environment.getProperty(
                        "orgmemory.graph-rag.postgres.reconcile-published-batches",
                        Boolean.class,
                        true))
                .thenReturn(false);
        return environment;
    }

    private static DataSource dataSource(String database) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn(database);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery("select current_database()")).thenReturn(result);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null
                && !Files.isRegularFile(candidate.resolve("demo/fixtures/public-evaluation.json"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("repository root not found");
        }
        return candidate;
    }
}
