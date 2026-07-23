package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class KnowledgeAssetLegacyResetMigrationIntegrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateAcrossTheResetBoundary() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("21")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        assertEquals(
                5,
                jdbc.queryForObject("SELECT count(*) FROM knowledge_assets", Integer.class));

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void removesLegacyAssetsInsteadOfCarryingCompatibilityDataForward() {
        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM knowledge_assets", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_asset_versions", Integer.class));
    }

    @Test
    void leavesTheNewAssetVersionSchemaReadyForFreshIngestion() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'knowledge_asset_versions'
                          AND column_name = 'knowledge_asset_id'
                          AND is_nullable = 'NO'
                        """,
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'source_revisions'
                          AND column_name = 'knowledge_asset_version_id'
                        """,
                        Integer.class));
    }
}
