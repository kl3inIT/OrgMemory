package com.orgmemory.api.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** One-shot, restored-copy-only export for the ADR 0020 retrieval recall gate. */
@Component
@Profile("retrieval-observation")
@EnableConfigurationProperties(RetrievalObservationProperties.class)
final class RetrievalObservationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrievalObservationRunner.class);
    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String DATASET_ID = "orgmemory-public-evaluation-allow-v1";
    private static final int EXPECTED_ALLOW_CASES = 43;
    /** Must match DefaultGraphRagKnowledgeRetrievalService.DIAGNOSTIC_TOP_K and the scorer. */
    private static final int KEYWORD_DOCUMENT_LIMIT = 60;
    /** Must match DefaultGraphRagKnowledgeRetrievalService.RECALL_TOP_K and the scorer. */
    private static final int BYPASS_DOCUMENT_LIMIT = 40;

    private final RetrievalObservationProperties properties;
    private final GraphRagKnowledgeRetrievalService retrieval;
    private final AppUserRepository users;
    private final ObjectMapper json;
    private final DataSource dataSource;
    private final Environment environment;

    RetrievalObservationRunner(
            RetrievalObservationProperties properties,
            GraphRagKnowledgeRetrievalService retrieval,
            AppUserRepository users,
            ObjectMapper json,
            DataSource dataSource,
            Environment environment) {
        this.properties = properties;
        this.retrieval = retrieval;
        this.users = users;
        this.json = json;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        requireSafeRestoredCopy();
        List<OfficialCase> cases = read(properties.officialCases(), new TypeReference<>() {});
        List<DocumentManifestEntry> manifest =
                read(properties.documentManifest(), new TypeReference<>() {});
        Map<String, String> documentIdByTitle = indexDocuments(manifest);
        Map<String, CaseObservation> observations = loadCheckpoint();
        Set<String> caseIds = new LinkedHashSet<>();
        for (OfficialCase officialCase : cases) {
            if (!"allow".equalsIgnoreCase(officialCase.expectedPermission())) {
                continue;
            }
            if (!caseIds.add(officialCase.questionId())) {
                throw new IllegalArgumentException(
                        "duplicate official case " + officialCase.questionId());
            }
            if (observations.containsKey(officialCase.questionId())) {
                LOGGER.info("Skipping checkpointed retrieval observation case={}", officialCase.questionId());
                continue;
            }
            CurrentActor actor = actor(officialCase.userId());
            var observed = retrieval.observe(
                    actor,
                    officialCase.questionVi(),
                    "retrieval-observation-" + officialCase.questionId().toLowerCase(Locale.ROOT));
            List<String> keywordDocuments = documentIds(
                    observed.keywordSeededDocuments(), documentIdByTitle, KEYWORD_DOCUMENT_LIMIT);
            List<String> bypassDocuments = documentIds(
                    observed.bypassDocuments(), documentIdByTitle, BYPASS_DOCUMENT_LIMIT);
            List<String> goldenDocuments = goldenDocuments(officialCase.expectedDocumentId());
            observations.put(officialCase.questionId(), new CaseObservation(
                    officialCase.questionId(),
                    keywordDocuments,
                    bypassDocuments,
                    ranks(goldenDocuments, keywordDocuments),
                    ranks(goldenDocuments, bypassDocuments),
                    new KeywordPlan(
                            observed.keywordPlan().highLevel(),
                            observed.keywordPlan().lowLevel(),
                            observed.keywordPlan().source())));
            List<CaseObservation> ordered = orderedObservations(cases, observations);
            writeCheckpoint(ordered);
            LOGGER.info(
                    "Captured retrieval observation case={} completed={}/{}",
                    officialCase.questionId(),
                    ordered.size(),
                    EXPECTED_ALLOW_CASES);
        }
        List<CaseObservation> ordered = orderedObservations(cases, observations);
        if (ordered.size() != EXPECTED_ALLOW_CASES) {
            throw new IllegalArgumentException(
                    "expected " + EXPECTED_ALLOW_CASES + " Allow cases but found " + ordered.size());
        }
        write(new ObservationSet(
                "orgmemory.retrieval-observations.v2",
                DATASET_ID,
                ordered));
        Files.deleteIfExists(checkpointPath());
    }

    private void requireSafeRestoredCopy() throws SQLException {
        if (!properties.enabled()) {
            throw new IllegalStateException("retrieval observation mode is not enabled");
        }
        requireFalse("spring.flyway.enabled");
        requireFalse("orgmemory.graph-rag.postgres.provision-indexes");
        requireFalse("orgmemory.graph-rag.postgres.reconcile-published-batches");
        String actualDatabase;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select current_database()")) {
            if (!result.next()) {
                throw new IllegalStateException("current_database() returned no row");
            }
            actualDatabase = result.getString(1);
        }
        if ("orgmemory".equalsIgnoreCase(actualDatabase)) {
            throw new IllegalStateException(
                    "retrieval observations refuse the live orgmemory database");
        }
        if (!properties.expectedDatabase().equals(actualDatabase)) {
            throw new IllegalStateException(
                    "connected database " + actualDatabase
                            + " does not match expected restored copy "
                            + properties.expectedDatabase());
        }
    }

    private void requireFalse(String property) {
        if (environment.getProperty(property, Boolean.class, true)) {
            throw new IllegalStateException(property + " must be false");
        }
    }

    private CurrentActor actor(String fixtureUserId) {
        int userNumber;
        try {
            if (fixtureUserId == null || !fixtureUserId.matches("U\\d{3}")) {
                throw new NumberFormatException();
            }
            userNumber = Integer.parseInt(fixtureUserId.substring(1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid fixture user id " + fixtureUserId, invalid);
        }
        UUID id = UUID.fromString(String.format(
                Locale.ROOT,
                "d1000000-0000-4000-8000-%012d",
                userNumber));
        AppUser user = users.findById(id).orElseThrow(() ->
                new IllegalArgumentException("fixture user is missing: " + fixtureUserId));
        if (!ORGANIZATION_ID.equals(user.getOrganizationId()) || !user.isActive()) {
            throw new IllegalArgumentException(
                    "fixture user is not an active official-dataset actor: " + fixtureUserId);
        }
        return new CurrentActor(
                id,
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getName(),
                user.getEmail(),
                user.getRole());
    }

    private Map<String, String> indexDocuments(List<DocumentManifestEntry> manifest) {
        Map<String, String> byTitle = new LinkedHashMap<>();
        for (DocumentManifestEntry entry : manifest) {
            addDocumentAlias(byTitle, entry.title(), entry.documentId());
            addDocumentAlias(byTitle, entry.documentId(), entry.documentId());
            addDocumentAlias(byTitle, entry.file(), entry.documentId());
            addDocumentAlias(
                    byTitle,
                    entry.documentId() + " - " + entry.title() + ".md",
                    entry.documentId());
        }
        return Map.copyOf(byTitle);
    }

    private static void addDocumentAlias(
            Map<String, String> aliases, String alias, String documentId) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        String previous = aliases.putIfAbsent(alias, documentId);
        if (previous != null && !previous.equals(documentId)) {
            throw new IllegalArgumentException("duplicate document alias in manifest: " + alias);
        }
    }

    private static List<String> documentIds(
            List<GraphRagKnowledgeRetrievalService.RetrievedDocument> documents,
            Map<String, String> documentIdByTitle,
            int limit) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (var document : documents) {
            String documentId = documentIdByTitle.get(document.title());
            if (documentId == null) {
                documentId = "source:" + document.sourceObjectId();
            }
            ids.add(documentId);
            if (ids.size() == limit) {
                break;
            }
        }
        return List.copyOf(ids);
    }

    private static List<String> goldenDocuments(String raw) {
        LinkedHashSet<String> documents = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(raw, "expectedDocumentId").split(";")) {
            String document = value.strip();
            if (!document.isEmpty()) {
                documents.add(document);
            }
        }
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Allow case has no golden document");
        }
        return List.copyOf(documents);
    }

    private static Map<String, Integer> ranks(
            List<String> goldenDocuments,
            List<String> retrievedDocuments) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (String golden : goldenDocuments) {
            int index = retrievedDocuments.indexOf(golden);
            ranks.put(golden, index < 0 ? null : index + 1);
        }
        return ranks;
    }

    private <T> T read(Path path, TypeReference<T> type) throws IOException {
        return json.readValue(path.toFile(), type);
    }

    private Map<String, CaseObservation> loadCheckpoint() throws IOException {
        Path checkpointPath = checkpointPath();
        if (!Files.exists(checkpointPath)) {
            return new LinkedHashMap<>();
        }
        Checkpoint checkpoint = read(checkpointPath, new TypeReference<>() {});
        if (!"orgmemory.retrieval-observation-checkpoint.v1".equals(checkpoint.schemaVersion())
                || !DATASET_ID.equals(checkpoint.datasetId())
                || !properties.expectedDatabase().equals(checkpoint.database())) {
            throw new IllegalStateException("retrieval observation checkpoint does not match this run");
        }
        Map<String, CaseObservation> observations = new LinkedHashMap<>();
        for (CaseObservation observation : checkpoint.observations()) {
            if (observations.put(observation.caseId(), observation) != null) {
                throw new IllegalStateException(
                        "duplicate checkpointed case " + observation.caseId());
            }
        }
        return observations;
    }

    private void writeCheckpoint(List<CaseObservation> observations) throws IOException {
        writeAtomic(checkpointPath(), new Checkpoint(
                "orgmemory.retrieval-observation-checkpoint.v1",
                DATASET_ID,
                properties.expectedDatabase(),
                observations));
    }

    private static List<CaseObservation> orderedObservations(
            List<OfficialCase> cases, Map<String, CaseObservation> observations) {
        List<CaseObservation> ordered = new ArrayList<>();
        for (OfficialCase officialCase : cases) {
            CaseObservation observation = observations.get(officialCase.questionId());
            if (observation != null) {
                ordered.add(observation);
            }
        }
        return List.copyOf(ordered);
    }

    private Path checkpointPath() {
        Path output = properties.output().toAbsolutePath().normalize();
        return output.resolveSibling(output.getFileName() + ".checkpoint");
    }

    private void write(ObservationSet output) throws IOException {
        writeAtomic(properties.output().toAbsolutePath().normalize(), output);
    }

    private void writeAtomic(Path absolute, Object output) throws IOException {
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), output);
        Files.move(
                temporary,
                absolute,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OfficialCase(
            @JsonProperty("expected_document_id") String expectedDocumentId,
            @JsonProperty("expected_permission") String expectedPermission,
            @JsonProperty("question_vi") String questionVi,
            @JsonProperty("user_id") String userId,
            @JsonProperty("question_id") String questionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DocumentManifestEntry(
            @JsonProperty("documentId") String documentId,
            String title,
            String file) {
    }

    private record ObservationSet(
            @JsonProperty("schema_version") String schemaVersion,
            @JsonProperty("dataset_id") String datasetId,
            List<CaseObservation> observations) {
    }

    private record Checkpoint(
            @JsonProperty("schema_version") String schemaVersion,
            @JsonProperty("dataset_id") String datasetId,
            String database,
            List<CaseObservation> observations) {
    }

    private record CaseObservation(
            @JsonProperty("case_id") String caseId,
            @JsonProperty("keyword_seeded_document_ids") List<String> keywordSeededDocumentIds,
            @JsonProperty("bypass_document_ids") List<String> bypassDocumentIds,
            @JsonProperty("keyword_seeded_golden_ranks") Map<String, Integer> keywordSeededGoldenRanks,
            @JsonProperty("bypass_golden_ranks") Map<String, Integer> bypassGoldenRanks,
            @JsonProperty("keyword_plan") KeywordPlan keywordPlan) {
    }

    private record KeywordPlan(
            @JsonProperty("high_level_keywords") List<String> highLevelKeywords,
            @JsonProperty("low_level_keywords") List<String> lowLevelKeywords,
            String source) {
    }
}
