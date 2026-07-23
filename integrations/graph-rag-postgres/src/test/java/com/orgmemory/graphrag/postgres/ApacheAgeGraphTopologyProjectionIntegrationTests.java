package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ApacheAgeGraphTopologyProjectionIntegrationTests {

    private static final UUID ORGANIZATION_ID = id("age-organization");
    private static final UUID ASSET_ID = id("age-asset");
    private static final UUID REVISION_ID = id("age-revision");
    private static final UUID CHUNK_ID = id("age-chunk");
    private static final UUID ACL_ID = id("age-acl");
    private static final UUID SOURCE_ID = id("age-source");
    private static final UUID TARGET_ID = id("age-target");
    private static final UUID RELATION_ID = id("age-relation");
    private static final UUID RELATION_CONTRIBUTION_ID = id("age-relation-contribution");
    private static final String GRAPH_NAME =
            "orgmemory_" + ORGANIZATION_ID.toString().replace("-", "");

    private static final DockerImageName IMAGE = DockerImageName.parse(
                    System.getenv().getOrDefault(
                            "ORGMEMORY_POSTGRES_RAG_TEST_IMAGE",
                            "orgmemory/postgres-rag:pg18-age1.7.0-pgvector0.8.2"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE);

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static ApacheAgeGraphTopologyProjection projection;

    @BeforeAll
    static void connect() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        projection = new ApacheAgeGraphTopologyProjection(
                new NamedParameterJdbcTemplate(dataSource),
                ApacheAgeMode.REQUIRED);
    }

    @Test
    void mirrorsTopologyWithoutCopyingEvidenceContentAndSupportsReplacement() {
        assertTrue(projection.isAvailable());
        GraphRevisionContributions contributions = projectionBatch();

        transactions.executeWithoutResult(status -> projection.replaceRevision(contributions));
        transactions.executeWithoutResult(status -> projection.replaceRevision(contributions));

        assertEquals(1L, graphRowCount("DIRECTED"));
        assertEquals(2L, graphRowCount("base"));
        String properties = jdbc.queryForObject(
                "SELECT properties::text FROM "
                        + quote(GRAPH_NAME)
                        + "."
                        + quote("DIRECTED")
                        + " LIMIT 1",
                String.class);
        assertTrue(properties.contains(RELATION_CONTRIBUTION_ID.toString()));
        assertTrue(properties.contains(ASSET_ID.toString()));
        assertFalse(properties.contains("sensitive evidence description"));
        assertEquals(
                List.of(TARGET_ID),
                transactions.execute(status -> projection.expandEntityIds(
                        ORGANIZATION_ID,
                        Set.of(SOURCE_ID),
                        Set.of(ASSET_ID),
                        2,
                        10)));
        assertTrue(transactions.execute(status -> projection.expandEntityIds(
                        ORGANIZATION_ID,
                        Set.of(SOURCE_ID),
                        Set.of(id("denied-asset")),
                        2,
                        10))
                .isEmpty());

        transactions.executeWithoutResult(
                status -> projection.removeRevision(ORGANIZATION_ID, REVISION_ID));
        assertEquals(0L, graphRowCount("DIRECTED"));
    }

    private static long graphRowCount(String label) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + quote(GRAPH_NAME) + "." + quote(label),
                Long.class);
        return count == null ? 0L : count;
    }

    private static GraphRevisionContributions projectionBatch() {
        CanonicalEntity source = new CanonicalEntity(SOURCE_ID, "OrgMemory", "PRODUCT");
        CanonicalEntity target = new CanonicalEntity(TARGET_ID, "Secure Search", "CAPABILITY");
        CanonicalRelation relation = new CanonicalRelation(
                RELATION_ID,
                SOURCE_ID,
                TARGET_ID,
                "BUILDS",
                RelationOrientation.DIRECTED);
        EvidenceProvenance provenance = new EvidenceProvenance(
                new EvidenceReference(
                        ORGANIZATION_ID,
                        ASSET_ID,
                        REVISION_ID,
                        CHUNK_ID,
                        ACL_ID,
                        1),
                1,
                "openai",
                "gpt-5.6-sol",
                "orgmemory-graph-extraction-v1",
                0.9,
                Instant.parse("2026-07-23T00:00:00Z"));
        return new GraphRevisionContributions(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                1,
                List.of(
                        new EntityContribution(
                                id("age-source-contribution"),
                                source,
                                "sensitive evidence description",
                                provenance),
                        new EntityContribution(
                                id("age-target-contribution"),
                                target,
                                "another sensitive description",
                                provenance)),
                List.of(new RelationContribution(
                        RELATION_CONTRIBUTION_ID,
                        relation,
                        List.of("sensitive"),
                        "sensitive evidence description",
                        provenance)));
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
