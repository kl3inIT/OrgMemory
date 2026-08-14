package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class AssistantPrivateFileMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID TURN = UUID.randomUUID();
    private static final UUID USER_MESSAGE = UUID.randomUUID();
    private static final UUID ANSWER = UUID.randomUUID();
    private static final UUID FILE = UUID.randomUUID();
    private static final UUID CHUNK = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateAndSeedPrivateFile() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) VALUES (?, 'Private file test', now(), now(), 0)",
                ORGANIZATION);
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Private owner', 'private-owner@example.test',
                    'STANDARD', true, now(), now(), 0)
                """, ACTOR, ORGANIZATION);
        jdbc.update("""
                INSERT INTO assistant_conversations (
                    id, organization_id, actor_user_id, title, last_activity_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'Private files', now(), now(), now(), 0)
                """, CONVERSATION, ORGANIZATION, ACTOR);
        insertMessage(USER_MESSAGE, "USER", "Compare these files");
        insertMessage(ANSWER, "ASSISTANT", "The policy says this. [1]");
        jdbc.update("""
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model,
                    dimensions, distance_metric, created_at)
                VALUES (?, ?, 'private/test/3', 'fixture', 'fixture-model',
                    3, 'COSINE', now())
                """, PROFILE, ORGANIZATION);
        jdbc.update("""
                INSERT INTO assistant_files (
                    id, organization_id, actor_user_id, file_name, media_type,
                    content_length, content_sha256, object_key, status, expires_at,
                    processing_generation, requested_profile_canonical,
                    requested_profile_sha256, resolved_profile_canonical,
                    resolved_profile_sha256, embedding_profile_id,
                    embedding_dimensions, processing_attempt,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'policy.txt', 'text/plain', 10, ?, ?, 'READY',
                    now() + interval '30 days', 1, 'requested', ?, 'resolved', ?,
                    ?, 3, 1, now(), now(), 0)
                """,
                FILE,
                ORGANIZATION,
                ACTOR,
                "a".repeat(64),
                "organizations/" + ORGANIZATION + "/assistant-files/" + FILE + "/policy.txt",
                "b".repeat(64),
                "c".repeat(64),
                PROFILE);
        jdbc.update("""
                INSERT INTO assistant_file_chunks (
                    id, assistant_file_id, organization_id, actor_user_id,
                    processing_generation, chunk_index, content, token_count,
                    source_block_indexes, canonical_text_sha256, embedding,
                    embedding_dimensions, embedding_profile_id,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, 0, 'Private policy excerpt', 4,
                    '{}', ?, '[0.1,0.2,0.3]', 3, ?, now(), now(), 0)
                """, CHUNK, FILE, ORGANIZATION, ACTOR, "d".repeat(64), PROFILE);
    }

    @Test
    void keepsPrivateCitationIdentitySeparateWhilePreservingKnowledgeRows() {
        insertPrivateCitation(UUID.randomUUID(), 1, FILE, CHUNK);
        jdbc.update("""
                INSERT INTO assistant_message_citations (
                    id, message_id, organization_id, actor_user_id,
                    citation_number, chunk_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 2, ?, now(), now(), 0)
                """, UUID.randomUUID(), ANSWER, ORGANIZATION, ACTOR, UUID.randomUUID());

        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM assistant_message_citations WHERE message_id = ?",
                Integer.class,
                ANSWER));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPrivateCitation(UUID.randomUUID(), 3, UUID.randomUUID(), CHUNK));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPrivateCitation(UUID.randomUUID(), 3, FILE, CHUNK, 2));
    }

    @Test
    void privateCitationMarkerSurvivesProjectionCleanup() {
        insertPrivateCitation(UUID.randomUUID(), 1, FILE, CHUNK);

        assertEquals(1, jdbc.update(
                "DELETE FROM assistant_file_chunks WHERE assistant_file_id = ?",
                FILE));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM assistant_message_citations WHERE message_id = ?",
                Integer.class,
                ANSWER));
    }

    @Test
    void rejectsUnknownLifecycleStates() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE assistant_files SET status = 'QUARANTINED' WHERE id = ?", FILE));
    }

    private void insertMessage(UUID id, String role, String content) {
        jdbc.update("""
                INSERT INTO assistant_conversation_messages (
                    id, conversation_id, turn_id, organization_id, actor_user_id,
                    role, content, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now(), now(), 0)
                """, id, CONVERSATION, TURN, ORGANIZATION, ACTOR, role, content);
    }

    private void insertPrivateCitation(UUID id, int number, UUID fileId, UUID chunkId) {
        insertPrivateCitation(id, number, fileId, chunkId, 1);
    }

    private void insertPrivateCitation(
            UUID id,
            int number,
            UUID fileId,
            UUID chunkId,
            long generation) {
        jdbc.update("""
                INSERT INTO assistant_message_citations (
                    id, message_id, organization_id, actor_user_id,
                    citation_number, evidence_kind, chunk_id, assistant_file_id,
                    processing_generation, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ASSISTANT_FILE', ?, ?, ?, now(), now(), 0)
                """, id, ANSWER, ORGANIZATION, ACTOR, number, chunkId, fileId, generation);
    }
}
