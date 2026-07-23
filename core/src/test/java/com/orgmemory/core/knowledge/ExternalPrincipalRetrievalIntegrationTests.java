package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the real {@link SecureKnowledgeRetrievalStore} SQL against PostgreSQL with a
 * Slack-shaped ledger (acl_authority SOURCE, SOURCE_GROUP + SOURCE_USER ACL entries, sealed
 * group membership). Proves external-principal resolution: unmapped denies, a verified
 * mapping grants existing documents without re-ingestion, revocation closes access, direct
 * SOURCE_USER entries resolve, and a mapped non-member is not over-granted.
 */
@Testcontainers
class ExternalPrincipalRetrievalIntegrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORG = UUID.fromString("f0000000-0000-4000-8000-0000000000a1");
    private static final UUID DEPT = UUID.fromString("f0000000-0000-4000-8000-0000000000a2");
    private static final UUID AN_USER = UUID.fromString("f0000000-0000-4000-8000-0000000000a3");
    private static final UUID BOB_USER = UUID.fromString("f0000000-0000-4000-8000-0000000000a4");
    private static final UUID CHARLIE_USER = UUID.fromString("f0000000-0000-4000-8000-0000000000a5");

    private static final UUID PROFILE = UUID.fromString("f0000000-0000-4000-8000-0000000000b6");
    private static final UUID BLOB = UUID.fromString("f0000000-0000-4000-8000-0000000000b7");
    private static final UUID RAW = UUID.fromString("f0000000-0000-4000-8000-0000000000b8");
    private static final UUID SNAPSHOT = UUID.fromString("f0000000-0000-4000-8000-0000000000b9");
    private static final UUID NORMALIZED = UUID.fromString("f0000000-0000-4000-8000-0000000000ba");
    private static final UUID ASSET = UUID.fromString("f0000000-0000-4000-8000-0000000000bb");
    private static final UUID OBJECT = UUID.fromString("f0000000-0000-4000-8000-0000000000bc");
    private static final UUID REVISION = UUID.fromString("f0000000-0000-4000-8000-0000000000bd");
    private static final UUID CHUNK = UUID.fromString("f0000000-0000-4000-8000-0000000000be");
    private static final UUID PUBLICATION = UUID.fromString("f0000000-0000-4000-8000-0000000000bf");
    private static final UUID SPACE = UUID.fromString("f0000000-0000-4000-8000-0000000000c0");

    // Second ledger for the ADR 0009 ceiling proof: a live source whose ingestion generation
    // denies everyone while the current generation grants An through the channel.
    private static final UUID RAW2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d1");
    private static final UUID ING_SNAPSHOT = UUID.fromString("f0000000-0000-4000-8000-0000000000d2");
    private static final UUID CUR_SNAPSHOT = UUID.fromString("f0000000-0000-4000-8000-0000000000d3");
    private static final UUID NORMALIZED2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d4");
    private static final UUID ASSET2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d5");
    private static final UUID OBJECT2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d6");
    private static final UUID REVISION2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d7");
    private static final UUID CHUNK2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d8");
    private static final UUID PUBLICATION2 = UUID.fromString("f0000000-0000-4000-8000-0000000000d9");

    private static final UUID CHANNEL_PRINCIPAL = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID AN_PRINCIPAL = UUID.fromString("a0000000-0000-4000-8000-000000000002");
    private static final UUID BOB_PRINCIPAL = UUID.fromString("a0000000-0000-4000-8000-000000000003");
    private static final UUID CHARLIE_PRINCIPAL = UUID.fromString("a0000000-0000-4000-8000-000000000004");

    private static final String MODEL_ID = "test-model";
    private static final String SHA = "0".repeat(64);
    private static final Instant CAPTURED = Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant EVALUATED_AT = CAPTURED.plus(1, ChronoUnit.HOURS);

    private static JdbcTemplate jdbc;
    private static SecureKnowledgeRetrievalStore store;

    @BeforeAll
    static void migrateAndSeed() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        store = new SecureKnowledgeRetrievalStore(new NamedParameterJdbcTemplate(dataSource));
        seedLedger();
        seedCeilingLedger();
    }

    @BeforeEach
    void resetMappings() {
        jdbc.update("DELETE FROM source_principal_mappings");
    }

    @Test
    void unmappedPrincipalSeesNothing() {
        assertFalse(visibleAs(AN_USER), "An without a mapping must not see the Slack document");
        assertFalse(visibleAs(BOB_USER), "Bob without a mapping must not see the Slack document");
    }

    @Test
    void groupMembershipMappingGrantsWithoutReingestion() {
        mapActive(AN_PRINCIPAL, AN_USER);
        assertTrue(visibleAs(AN_USER),
                "A verified mapping to a channel member must grant the existing document");
    }

    @Test
    void revokingMappingClosesAccess() {
        mapActive(AN_PRINCIPAL, AN_USER);
        assertTrue(visibleAs(AN_USER));
        jdbc.update("UPDATE source_principal_mappings SET status = 'REVOKED', revoked_at = now() "
                + "WHERE source_principal_id = ?", AN_PRINCIPAL);
        assertFalse(visibleAs(AN_USER), "Revoking the mapping must close access");
    }

    @Test
    void directSourceUserEntryResolvesThroughMapping() {
        mapActive(BOB_PRINCIPAL, BOB_USER);
        assertTrue(visibleAs(BOB_USER),
                "Bob is not a channel member but has a direct SOURCE_USER ALLOW entry");
    }

    @Test
    void mappedNonMemberWithoutEntryIsDenied() {
        mapActive(CHARLIE_PRINCIPAL, CHARLIE_USER);
        assertFalse(visibleAs(CHARLIE_USER),
                "Charlie is neither a channel member nor has a direct entry; must be denied");
    }

    @Test
    void liveSourceCeilingUsesCurrentGenerationNotIngestion() {
        // The ingestion generation denies everyone; only the current generation grants An.
        // A live (SLACK) source must enforce the current generation per ADR 0009, so An is granted.
        mapActive(AN_PRINCIPAL, AN_USER);
        assertTrue(visible(AN_USER, ASSET2, OBJECT2),
                "A live-source ceiling must use the current generation, not the deny-all ingestion snapshot");
    }

    private boolean visibleAs(UUID userId) {
        return visible(userId, ASSET, OBJECT);
    }

    private boolean visible(UUID userId, UUID assetId, UUID objectId) {
        List<UUID> visible = store.visibleSourceObjectIds(new SecureKnowledgeRetrievalStore.RetrievalScope(
                ORG, userId, DEPT, false, List.of(assetId), MODEL_ID, EVALUATED_AT));
        return visible.contains(objectId);
    }

    private void mapActive(UUID principalId, UUID userId) {
        jdbc.update("""
                INSERT INTO source_principal_mappings (
                    id, organization_id, source_principal_id, app_user_id, method, evidence,
                    status, verified_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ADMIN_CONFIRMED', 'test', 'ACTIVE', now(), now(), now(), 0)
                """, UUID.randomUUID(), ORG, principalId, userId);
    }

    private static void seedLedger() {
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Dept', now(), now(), 0)", DEPT, ORG);
        insertUser(AN_USER, "an@slacktest.example", true);
        insertUser(BOB_USER, "bob@slacktest.example", true);
        insertUser(CHARLIE_USER, "charlie@slacktest.example", true);

        jdbc.update("""
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model, dimensions, distance_metric, created_at)
                VALUES (?, ?, 'test/profile/3', 'test', 'test-embed', 3, 'COSINE', now())
                """, PROFILE, ORG);

        jdbc.update("""
                INSERT INTO evidence_blobs (
                    id, organization_id, object_key, media_type, content_length, content_sha256,
                    scan_status, created_at, updated_at, version)
                VALUES (?, ?, 'blobs/slacktest-doc-1', 'text/plain', 42, ?, 'BASIC_VALIDATED', now(), now(), 0)
                """, BLOB, ORG, SHA);

        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'slacktest-space', 'Slack Test Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);

        jdbc.update("""
                INSERT INTO raw_source_objects (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    source_version, object_type, title, raw_content, payload_sha256, classification,
                    declared_access, status, created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-doc-1', 'v1', 'message', 'Slack doc',
                    'body', ?, 'INTERNAL', 'ALL_EMPLOYEES', 'NORMALIZED', now(), now(), 0)
                """, RAW, ORG, SHA);

        // ACL snapshot gen1 acts as both ingestion and current for the base scenarios.
        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation, capture_status,
                    default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 1, 'COMPLETE', 'DENY', ?, ?, ?)
                """, SNAPSHOT, ORG, RAW, SHA, ts(CAPTURED), ts(CAPTURED.plus(2, ChronoUnit.HOURS)));

        insertEntry(SNAPSHOT, "SOURCE_GROUP", CHANNEL_PRINCIPAL.toString());
        insertEntry(SNAPSHOT, "SOURCE_USER", BOB_PRINCIPAL.toString());

        insertPrincipal(CHANNEL_PRINCIPAL, "C-channel", "SOURCE_GROUP");
        insertPrincipal(AN_PRINCIPAL, "U-an", "SOURCE_USER");
        insertPrincipal(BOB_PRINCIPAL, "U-bob", "SOURCE_USER");
        insertPrincipal(CHARLIE_PRINCIPAL, "U-charlie", "SOURCE_USER");

        // Channel membership sealed with this generation: An is a member, Bob and Charlie are not.
        jdbc.update("""
                INSERT INTO source_acl_group_members (
                    id, organization_id, source_acl_snapshot_id, group_principal_id, member_principal_id, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), ORG, SNAPSHOT, CHANNEL_PRINCIPAL, AN_PRINCIPAL);

        // Seal after entries and membership are in place (triggers reject later inserts).
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at)
                VALUES (?, ?, 2, ?, now())
                """, SNAPSHOT, ORG, SHA);

        jdbc.update("""
                INSERT INTO source_acl_heads (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    current_raw_source_object_id, current_snapshot_id, acl_generation,
                    created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-doc-1', ?, ?, 1, now(), now(), 0)
                """, UUID.randomUUID(), ORG, RAW, SNAPSHOT);

        jdbc.update("""
                INSERT INTO normalized_records (
                    id, organization_id, raw_source_object_id, source_acl_snapshot_id, normalizer_version,
                    title, normalized_content, language, classification, declared_access, content_sha256,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'norm-v1', 'Slack doc', 'normalized body', 'en',
                    'INTERNAL', 'ALL_EMPLOYEES', ?, 'PROMOTED', now(), now(), 0)
                """, NORMALIZED, ORG, RAW, SNAPSHOT, SHA);

        jdbc.update("""
                INSERT INTO knowledge_assets (
                    id, organization_id, raw_source_object_id, normalized_record_id, source_acl_snapshot_id,
                    knowledge_space_id, title, content, language, classification, declared_access, content_sha256,
                    orgmemory_gate, status, activated_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'Slack doc', 'normalized body', 'en', 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'ALLOW', 'ACTIVE', now(), now(), now(), 0)
                """, ASSET, ORG, RAW, NORMALIZED, SNAPSHOT, SPACE, SHA);

        // Source object carries acl_authority SOURCE (drives the ADR 0009 live-source ceiling).
        jdbc.update("""
                INSERT INTO source_objects (
                    id, organization_id, created_by_user_id, knowledge_space_id, acl_authority, source_system,
                    source_connection_key, external_object_id, title, classification, declared_access,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'SOURCE', 'slack', 'T-workspace', 'C-doc-1', 'Slack doc', 'INTERNAL',
                    'ALL_EMPLOYEES', 'ACTIVE', now(), now(), 0)
                """, OBJECT, ORG, AN_USER, SPACE);

        jdbc.update("""
                INSERT INTO source_revisions (
                    id, organization_id, source_object_id, knowledge_space_id, evidence_blob_id,
                    revision_number, file_name, media_type, content_length, content_sha256, classification,
                    declared_access, created_by_user_id, status, pipeline_version, parser_version,
                    chunker_version, embedding_profile_id, embedding_dimensions, raw_source_object_id,
                    normalized_record_id, knowledge_asset_id, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 1, 'doc.txt', 'text/plain', 42, ?, 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'READY', 'pipe-v1', 'parse-v1', 'chunk-v1', ?, 3, ?, ?, ?, now(), now(), now(), 0)
                """, REVISION, ORG, OBJECT, SPACE, BLOB, SHA, AN_USER, PROFILE, RAW, NORMALIZED, ASSET);

        jdbc.update("UPDATE source_objects SET current_revision_id = ?, updated_at = now() WHERE id = ?",
                REVISION, OBJECT);

        jdbc.update("""
                INSERT INTO knowledge_chunks (
                    id, organization_id, source_object_id, source_revision_id, knowledge_asset_id,
                    chunk_index, content, content_sha256, embedding, embedding_profile_id,
                    embedding_dimensions, pipeline_version, projection_generation, active, created_at)
                VALUES (?, ?, ?, ?, ?, 0, 'normalized body', ?, '[0.1,0.2,0.3]'::vector, ?, 3,
                    'pipe-v1', 1, true, now())
                """, CHUNK, ORG, OBJECT, REVISION, ASSET, SHA, PROFILE);

        jdbc.update("""
                INSERT INTO knowledge_asset_publication_outbox (
                    id, organization_id, source_revision_id, source_object_id, knowledge_asset_id,
                    knowledge_space_id, owner_user_id, projection_generation, embedding_profile_id,
                    embedding_dimensions, pipeline_version, status, attempt_count, authorization_model_id,
                    applied_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, 3, 'pipe-v1', 'APPLIED', 1, ?, now(), now(), now(), 0)
                """, PUBLICATION, ORG, REVISION, OBJECT, ASSET, SPACE, AN_USER, PROFILE, MODEL_ID);
    }

    private static void seedCeilingLedger() {
        jdbc.update("""
                INSERT INTO raw_source_objects (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    source_version, object_type, title, raw_content, payload_sha256, classification,
                    declared_access, status, created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-doc-2', 'v1', 'message', 'Slack doc 2',
                    'body', ?, 'INTERNAL', 'ALL_EMPLOYEES', 'NORMALIZED', now(), now(), 0)
                """, RAW2, ORG, SHA);

        // Generation 1 = ingestion snapshot: complete but denies everyone (no ALLOW entries).
        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation, capture_status,
                    default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 1, 'COMPLETE', 'DENY', ?, ?, ?)
                """, ING_SNAPSHOT, ORG, RAW2, SHA, ts(CAPTURED), ts(CAPTURED.plus(2, ChronoUnit.HOURS)));
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at)
                VALUES (?, ?, 0, ?, now())
                """, ING_SNAPSHOT, ORG, SHA);

        // Generation 2 = current snapshot: grants An through the channel.
        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation, capture_status,
                    default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 2, 'COMPLETE', 'DENY', ?, ?, ?)
                """, CUR_SNAPSHOT, ORG, RAW2, SHA, ts(CAPTURED), ts(CAPTURED.plus(2, ChronoUnit.HOURS)));
        insertEntry(CUR_SNAPSHOT, "SOURCE_GROUP", CHANNEL_PRINCIPAL.toString());
        jdbc.update("""
                INSERT INTO source_acl_group_members (
                    id, organization_id, source_acl_snapshot_id, group_principal_id, member_principal_id, created_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), ORG, CUR_SNAPSHOT, CHANNEL_PRINCIPAL, AN_PRINCIPAL);
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at)
                VALUES (?, ?, 1, ?, now())
                """, CUR_SNAPSHOT, ORG, SHA);

        jdbc.update("""
                INSERT INTO source_acl_heads (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    current_raw_source_object_id, current_snapshot_id, acl_generation,
                    created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-doc-2', ?, ?, 2, now(), now(), 0)
                """, UUID.randomUUID(), ORG, RAW2, CUR_SNAPSHOT);

        jdbc.update("""
                INSERT INTO normalized_records (
                    id, organization_id, raw_source_object_id, source_acl_snapshot_id, normalizer_version,
                    title, normalized_content, language, classification, declared_access, content_sha256,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'norm-v1', 'Slack doc 2', 'normalized body 2', 'en',
                    'INTERNAL', 'ALL_EMPLOYEES', ?, 'PROMOTED', now(), now(), 0)
                """, NORMALIZED2, ORG, RAW2, ING_SNAPSHOT, SHA);

        jdbc.update("""
                INSERT INTO knowledge_assets (
                    id, organization_id, raw_source_object_id, normalized_record_id, source_acl_snapshot_id,
                    knowledge_space_id, title, content, language, classification, declared_access, content_sha256,
                    orgmemory_gate, status, activated_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'Slack doc 2', 'normalized body 2', 'en', 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'ALLOW', 'ACTIVE', now(), now(), now(), 0)
                """, ASSET2, ORG, RAW2, NORMALIZED2, ING_SNAPSHOT, SPACE, SHA);

        jdbc.update("""
                INSERT INTO source_objects (
                    id, organization_id, created_by_user_id, knowledge_space_id, acl_authority, source_system,
                    source_connection_key, external_object_id, title, classification, declared_access,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'SOURCE', 'slack', 'T-workspace', 'C-doc-2', 'Slack doc 2', 'INTERNAL',
                    'ALL_EMPLOYEES', 'ACTIVE', now(), now(), 0)
                """, OBJECT2, ORG, AN_USER, SPACE);

        jdbc.update("""
                INSERT INTO source_revisions (
                    id, organization_id, source_object_id, knowledge_space_id, evidence_blob_id,
                    revision_number, file_name, media_type, content_length, content_sha256, classification,
                    declared_access, created_by_user_id, status, pipeline_version, parser_version,
                    chunker_version, embedding_profile_id, embedding_dimensions, raw_source_object_id,
                    normalized_record_id, knowledge_asset_id, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 1, 'doc2.txt', 'text/plain', 42, ?, 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'READY', 'pipe-v1', 'parse-v1', 'chunk-v1', ?, 3, ?, ?, ?, now(), now(), now(), 0)
                """, REVISION2, ORG, OBJECT2, SPACE, BLOB, SHA2(), AN_USER, PROFILE, RAW2, NORMALIZED2, ASSET2);
        jdbc.update("UPDATE source_objects SET current_revision_id = ?, updated_at = now() WHERE id = ?",
                REVISION2, OBJECT2);

        jdbc.update("""
                INSERT INTO knowledge_chunks (
                    id, organization_id, source_object_id, source_revision_id, knowledge_asset_id,
                    chunk_index, content, content_sha256, embedding, embedding_profile_id,
                    embedding_dimensions, pipeline_version, projection_generation, active, created_at)
                VALUES (?, ?, ?, ?, ?, 0, 'normalized body 2', ?, '[0.1,0.2,0.3]'::vector, ?, 3,
                    'pipe-v1', 1, true, now())
                """, CHUNK2, ORG, OBJECT2, REVISION2, ASSET2, SHA, PROFILE);

        jdbc.update("""
                INSERT INTO knowledge_asset_publication_outbox (
                    id, organization_id, source_revision_id, source_object_id, knowledge_asset_id,
                    knowledge_space_id, owner_user_id, projection_generation, embedding_profile_id,
                    embedding_dimensions, pipeline_version, status, attempt_count, authorization_model_id,
                    applied_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, 3, 'pipe-v1', 'APPLIED', 1, ?, now(), now(), now(), 0)
                """, PUBLICATION2, ORG, REVISION2, OBJECT2, ASSET2, SPACE, AN_USER, PROFILE, MODEL_ID);
    }

    // evidence_blobs is reused across both ledgers but source_revisions requires a distinct
    // (source_object_id, content_sha256); a second sha keeps the second revision's content unique.
    private static String SHA2() {
        return "1".repeat(64);
    }

    private static void insertUser(UUID id, String email, boolean active) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'CONTRIBUTOR', ?, now(), now(), 0)
                """, id, ORG, DEPT, email, email, active);
    }

    private static void insertEntry(UUID snapshotId, String type, String key) {
        jdbc.update("""
                INSERT INTO source_acl_entries (
                    id, organization_id, source_acl_snapshot_id, principal_type, principal_key, gate, created_at)
                VALUES (?, ?, ?, ?, ?, 'ALLOW', now())
                """, UUID.randomUUID(), ORG, snapshotId, type, key);
    }

    private static void insertPrincipal(UUID id, String externalKey, String kind) {
        jdbc.update("""
                INSERT INTO source_principals (
                    id, organization_id, source_system, source_connection_key, external_key, kind,
                    sso_verified, last_seen_at, created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', ?, ?, false, now(), now(), now(), 0)
                """, id, ORG, externalKey, kind);
    }

    private static java.sql.Timestamp ts(Instant instant) {
        return java.sql.Timestamp.from(instant);
    }
}
