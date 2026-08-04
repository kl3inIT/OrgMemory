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
class AssistantAnswerFeedbackMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION =
            UUID.fromString("ae000000-0000-4000-8000-000000000001");
    private static final UUID ACTOR =
            UUID.fromString("ae000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_ACTOR =
            UUID.fromString("ae000000-0000-4000-8000-000000000003");
    private static final UUID CONVERSATION =
            UUID.fromString("ae000000-0000-4000-8000-000000000004");
    private static final UUID ANSWER =
            UUID.fromString("ae000000-0000-4000-8000-000000000005");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateAndSeedOwnedAnswer() {
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
                        + "VALUES (?, 'Assistant Feedback Test', now(), now(), 0)",
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
                CONVERSATION,
                ORGANIZATION,
                ACTOR);
        jdbc.update(
                """
                INSERT INTO assistant_conversation_messages (
                    id, conversation_id, organization_id, actor_user_id, role,
                    content, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ASSISTANT', 'Sixty days.', now(), now(), now(), 0)
                """,
                ANSWER,
                CONVERSATION,
                ORGANIZATION,
                ACTOR);
    }

    @Test
    void enforcesOwnedMessageTupleSentimentAndCascadeDeletion() {
        insertFeedback(ACTOR, "HELPFUL");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM assistant_answer_feedback",
                        Integer.class));

        jdbc.update("DELETE FROM assistant_conversations WHERE id = ?", CONVERSATION);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM assistant_answer_feedback",
                        Integer.class));
    }

    @Test
    void rejectsMismatchedActorAndUnknownSentiment() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertFeedback(OTHER_ACTOR, "HELPFUL"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertFeedback(ACTOR, "MAYBE"));
    }

    private void insertActor(UUID actorId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, role, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Assistant Actor', ?, 'EMPLOYEE', true, now(), now(), 0)
                """,
                actorId,
                ORGANIZATION,
                email);
    }

    private void insertFeedback(UUID actorId, String sentiment) {
        jdbc.update(
                """
                INSERT INTO assistant_answer_feedback (
                    message_id, organization_id, actor_user_id, sentiment,
                    updated_at, version)
                VALUES (?, ?, ?, ?, now(), 0)
                """,
                ANSWER,
                ORGANIZATION,
                actorId,
                sentiment);
    }
}
