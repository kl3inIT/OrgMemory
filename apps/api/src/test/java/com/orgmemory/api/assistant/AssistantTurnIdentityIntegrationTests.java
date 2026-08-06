package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantTurnRef;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantTurnIdentityIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssistantConversationService conversations;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pairsTheQuestionAndAnswerOfOneTurnUnderOneIdentity() {
        CurrentActor actor = seededActor();

        AssistantTurnRef turn = conversations.beginTurn(actor, null, "How long is probation?");
        conversations.completeTurn(actor, turn, UUID.randomUUID(), "Sixty days.");

        assertEquals(
                List.of("USER", "ASSISTANT"),
                jdbc.queryForList(
                        """
                        SELECT role FROM assistant_conversation_messages
                        WHERE turn_id = ? ORDER BY sequence_id
                        """,
                        String.class,
                        turn.turnId()));
    }

    @Test
    void pairsOverlappingTurnsThatPersistOutOfOrder() {
        CurrentActor actor = seededActor();
        UUID conversationId = conversations.beginTurn(actor, null, "Opening turn")
                .conversationId();

        // Two turns open before either answers, and the second answers first:
        // U1, U2, A2, A1. No ordering heuristic over sequence_id pairs this.
        AssistantTurnRef first = conversations.beginTurn(actor, conversationId, "First question");
        AssistantTurnRef second = conversations.beginTurn(actor, conversationId, "Second question");
        conversations.completeTurn(actor, second, UUID.randomUUID(), "Second answer");
        conversations.completeTurn(actor, first, UUID.randomUUID(), "First answer");

        assertEquals(
                List.of("First question", "First answer"),
                contentOfTurn(first.turnId()));
        assertEquals(
                List.of("Second question", "Second answer"),
                contentOfTurn(second.turnId()));
    }

    @Test
    void givesEveryConcurrentQuestionInOneConversationItsOwnIdentity() throws Exception {
        CurrentActor actor = seededActor();
        UUID conversationId = conversations.beginTurn(actor, null, "Opening turn")
                .conversationId();
        int turnCount = 12;

        CountDownLatch ready = new CountDownLatch(turnCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AssistantTurnRef>> started = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(turnCount)) {
            for (int index = 0; index < turnCount; index++) {
                int turn = index;
                started.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return conversations.beginTurn(
                            actor, conversationId, "Concurrent question " + turn);
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<AssistantTurnRef> attempt : started) {
                assertNotNull(attempt.get(30, TimeUnit.SECONDS));
            }
        }

        assertEquals(
                turnCount + 1,
                jdbc.queryForObject(
                        """
                        SELECT count(DISTINCT turn_id) FROM assistant_conversation_messages
                        WHERE conversation_id = ?
                        """,
                        Integer.class,
                        conversationId));
    }

    @Test
    void refusesASecondMessageOfTheSameRoleInOneTurn() {
        CurrentActor actor = seededActor();
        AssistantTurnRef turn = conversations.beginTurn(actor, null, "How long is probation?");

        assertThrows(
                DuplicateKeyException.class,
                () -> insertMessage(actor, turn.conversationId(), turn.turnId(), "USER", "Again"));
    }

    @Test
    void leavesRowsWrittenBeforeTurnIdentityExistedUnconstrained() {
        CurrentActor actor = seededActor();
        UUID conversationId = conversations.beginTurn(actor, null, "Opening turn")
                .conversationId();

        insertMessage(actor, conversationId, null, "USER", "Legacy question");
        insertMessage(actor, conversationId, null, "USER", "Another legacy question");

        // Partial uniqueness: legacy rows carry no pairing to protect, so they
        // must not collide with one another and must stay in the transcript.
        assertEquals(
                2,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM assistant_conversation_messages
                        WHERE conversation_id = ? AND turn_id IS NULL
                        """,
                        Integer.class,
                        conversationId));
    }

    private List<String> contentOfTurn(UUID turnId) {
        return jdbc.queryForList(
                """
                SELECT content FROM assistant_conversation_messages
                WHERE turn_id = ? ORDER BY sequence_id
                """,
                String.class,
                turnId);
    }

    private void insertMessage(
            CurrentActor actor, UUID conversationId, UUID turnId, String role, String content) {
        jdbc.update(
                """
                INSERT INTO assistant_conversation_messages (
                    id, conversation_id, turn_id, organization_id, actor_user_id,
                    role, content, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(),
                conversationId,
                turnId,
                actor.organizationId(),
                actor.userId(),
                role,
                content,
                java.sql.Timestamp.from(Instant.now()));
    }

    private CurrentActor seededActor() {
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Turn identity', now(), now(), 0)
                """,
                organizationId);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Turn identity actor', ?, 'STANDARD', true, now(), now(), 0)
                """,
                actorId,
                organizationId,
                actorId + "@example.test");
        return new CurrentActor(
                actorId,
                organizationId,
                null,
                "Turn identity actor",
                actorId + "@example.test",
                Clearance.STANDARD);
    }
}
