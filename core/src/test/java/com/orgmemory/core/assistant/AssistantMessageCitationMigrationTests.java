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
class AssistantMessageCitationMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID OTHER_ACTOR = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID ANSWER = UUID.randomUUID();
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateAndSeedAnswer() {
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
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, 'Citation Test', now(), now(), 0)",
                ORGANIZATION);
        insertActor(ACTOR, "actor@example.test");
        insertActor(OTHER_ACTOR, "other@example.test");
        jdbc.update(
                """
                INSERT INTO assistant_conversations (
                    id, organization_id, actor_user_id, title, last_activity_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'Policy', now(), now(), now(), 0)
                """,
                CONVERSATION, ORGANIZATION, ACTOR);
        jdbc.update(
                """
                INSERT INTO assistant_conversation_messages (
                    id, conversation_id, organization_id, actor_user_id, role,
                    content, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ASSISTANT', 'Sixty days. [1]', now(), now(), now(), 0)
                """,
                ANSWER, CONVERSATION, ORGANIZATION, ACTOR);
    }

    @Test
    void enforcesOwnedMessageTupleUniqueNumberAndCascadeDeletion() {
        insertCitation(UUID.randomUUID(), ACTOR, 1, UUID.randomUUID());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertCitation(UUID.randomUUID(), ACTOR, 1, UUID.randomUUID()));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertCitation(UUID.randomUUID(), OTHER_ACTOR, 2, UUID.randomUUID()));

        jdbc.update("DELETE FROM assistant_conversations WHERE id = ?", CONVERSATION);
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM assistant_message_citations", Integer.class));
    }

    private void insertActor(UUID actorId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Assistant Actor', ?, 'STANDARD', true, now(), now(), 0)
                """,
                actorId, ORGANIZATION, email);
    }

    private void insertCitation(UUID id, UUID actorId, int number, UUID chunkId) {
        jdbc.update(
                """
                INSERT INTO assistant_message_citations (
                    id, message_id, organization_id, actor_user_id,
                    citation_number, chunk_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                id, ANSWER, ORGANIZATION, actorId, number, chunkId);
    }
}
