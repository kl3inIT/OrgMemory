package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SourceProcessingProfileMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION =
            UUID.fromString("c3000000-0000-4000-8000-000000000001");
    private static final UUID USER =
            UUID.fromString("c3000000-0000-4000-8000-000000000002");
    private static final UUID LEGACY_REVISION =
            UUID.fromString("c3000000-0000-4000-8000-000000000003");
    private static final UUID SPACE =
            UUID.fromString("c3000000-0000-4000-8000-000000000004");
    private static final UUID BLOB =
            UUID.fromString("c3000000-0000-4000-8000-000000000005");
    private static final UUID SOURCE_OBJECT =
            UUID.fromString("c3000000-0000-4000-8000-000000000006");
    private static final UUID EMBEDDING_PROFILE =
            UUID.fromString("c3000000-0000-4000-8000-000000000007");
    private static final UUID KNOWLEDGE_ASSET =
            UUID.fromString("c3000000-0000-4000-8000-000000000008");
    private static final String SHA256 = "0".repeat(64);

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateToVersionTwentyNineWithALegacyReadyRevision() {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("29")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, 'Processing Profile Migration', now(), now(), 0)",
                ORGANIZATION);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Legacy owner', 'legacy-owner@example.test',
                    'STANDARD', true, now(), now(), 0)
                """,
                USER,
                ORGANIZATION);
        jdbc.update(
                """
                INSERT INTO knowledge_spaces (
                    id, organization_id, audience_mode, audience_version,
                    space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, 'ORGANIZATION', 1, 'profile-migration',
                    'Profile migration', true, now(), now(), 0)
                """,
                SPACE,
                ORGANIZATION);
        jdbc.update(
                """
                INSERT INTO evidence_blobs (
                    id, organization_id, object_key, media_type, content_length,
                    content_sha256, scan_status, created_at, updated_at, version)
                VALUES (?, ?, 'migration/legacy.txt', 'text/plain', 1, ?,
                    'BASIC_VALIDATED', now(), now(), 0)
                """,
                BLOB,
                ORGANIZATION,
                SHA256);
        jdbc.update(
                """
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model,
                    dimensions, distance_metric, created_at)
                VALUES (?, ?, 'migration/profile/3', 'fixture', 'fixture-model',
                    3, 'COSINE', now())
                """,
                EMBEDDING_PROFILE,
                ORGANIZATION);
        jdbc.update(
                """
                INSERT INTO source_objects (
                    id, organization_id, created_by_user_id, knowledge_space_id,
                    acl_authority, source_system, source_connection_key,
                    external_object_id, title, classification, declared_access,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ORGMEMORY', 'upload', 'migration',
                    'legacy-object', 'Legacy source', 'INTERNAL', 'ALL_EMPLOYEES',
                    'ACTIVE', now(), now(), 0)
                """,
                SOURCE_OBJECT,
                ORGANIZATION,
                USER,
                SPACE);
        jdbc.update(
                """
                INSERT INTO knowledge_assets (
                    id, organization_id, knowledge_space_id, source_object_id,
                    archived_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, NULL, now(), now(), 0)
                """,
                KNOWLEDGE_ASSET,
                ORGANIZATION,
                SPACE,
                SOURCE_OBJECT);
        insertReadyRevision(LEGACY_REVISION, 1);
    }

    @Test
    void preservesLegacyReadyIdentityWhileEnforcingProfilesForNewReadyRows() {
        migrateLatest();

        var legacy = jdbc.queryForMap(
                """
                SELECT status, processing_profile, processing_profile_sha256
                FROM source_revisions
                WHERE id = ?
                """,
                LEGACY_REVISION);
        assertEquals("READY", legacy.get("status"));
        assertNull(legacy.get("processing_profile"));
        assertNull(legacy.get("processing_profile_sha256"));
        assertFalse(jdbc.queryForObject(
                """
                SELECT convalidated
                FROM pg_constraint
                WHERE conname = 'chk_source_revision_ready'
                """,
                Boolean.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertReadyRevision(
                        UUID.fromString("c3000000-0000-4000-8000-000000000009"), 2));
    }

    @Test
    void refusesToGuessAPolicyForLegacyNonterminalJobs() {
        jdbc.update(
                """
                INSERT INTO source_ingestion_jobs (
                    id, organization_id, source_revision_id, job_type, status,
                    available_at, attempt_count, max_attempts,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'PROCESS_SOURCE_REVISION', 'PENDING', now(), 1, 5,
                    now(), now(), 0)
                """,
                UUID.fromString("c3000000-0000-4000-8000-00000000000a"),
                ORGANIZATION,
                LEGACY_REVISION);

        FlywayException failure = assertThrows(FlywayException.class, this::migrateLatest);

        assertTrue(failure.getMessage().contains(
                "legacy source ingestion jobs to be drained before migration"));
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertReadyRevision(UUID revisionId, int revisionNumber) {
        jdbc.update(
                """
                INSERT INTO source_revisions (
                    id, organization_id, source_object_id, knowledge_space_id,
                    evidence_blob_id, revision_number, file_name, media_type,
                    content_length, content_sha256, classification, declared_access,
                    created_by_user_id, status, pipeline_version, parser_version,
                    chunker_version, embedding_profile_id, embedding_dimensions,
                    knowledge_asset_id, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'legacy.txt', 'text/plain', 1, ?,
                    'INTERNAL', 'ALL_EMPLOYEES', ?, 'READY', 'legacy-pipeline',
                    'legacy-parser', 'legacy-chunker', ?, 3, ?, now(), now(), now(), 0)
                """,
                revisionId,
                ORGANIZATION,
                SOURCE_OBJECT,
                SPACE,
                BLOB,
                revisionNumber,
                SHA256,
                USER,
                EMBEDDING_PROFILE,
                KNOWLEDGE_ASSET);
    }
}
