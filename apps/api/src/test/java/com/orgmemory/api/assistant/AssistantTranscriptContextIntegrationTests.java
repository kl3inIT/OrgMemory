package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assistant.AssistantContextMessage;
import com.orgmemory.core.assistant.AssistantConversationRole;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantTurnRef;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantTranscriptContextIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssistantConversationService conversations;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void returnsCompletedTurnsOldestFirstWithEachQuestionBeforeItsAnswer() {
        CurrentActor actor = seededActor();
        UUID conversationId = ask(actor, null, "First question", "First answer");
        ask(actor, conversationId, "Second question", "Second answer");

        assertEquals(
                List.of(
                        user("First question"),
                        assistant("First answer"),
                        user("Second question"),
                        assistant("Second answer")),
                conversations.recentCompletedTurns(
                        actor.organizationId(), conversationId, 10));
    }

    @Test
    void excludesTheQuestionOfTheTurnStillInFlight() {
        CurrentActor actor = seededActor();
        UUID conversationId = ask(actor, null, "First question", "First answer");

        // beginTurn commits the question before the model is called, and the
        // caller passes that same question as the user message. Returning it
        // here would send it to the model twice.
        conversations.beginTurn(actor, conversationId, "Question still streaming");

        assertEquals(
                List.of(user("First question"), assistant("First answer")),
                conversations.recentCompletedTurns(
                        actor.organizationId(), conversationId, 10));
    }

    @Test
    void excludesATurnThatFailedBeforeProducingAnAnswer() {
        CurrentActor actor = seededActor();
        UUID conversationId = ask(actor, null, "First question", "First answer");
        conversations.beginTurn(actor, conversationId, "Question whose turn failed");
        ask(actor, conversationId, "Later question", "Later answer");

        assertEquals(
                List.of(
                        user("First question"),
                        assistant("First answer"),
                        user("Later question"),
                        assistant("Later answer")),
                conversations.recentCompletedTurns(
                        actor.organizationId(), conversationId, 10));
    }

    @Test
    void pairsOverlappingTurnsThatPersistedOutOfOrder() {
        CurrentActor actor = seededActor();
        UUID conversationId = conversations.beginTurn(actor, null, "Opening question")
                .conversationId();
        conversations.completeTurn(
                actor,
                new AssistantTurnRef(conversationId, openingTurnId(conversationId)),
                UUID.randomUUID(),
                "Opening answer");

        AssistantTurnRef first = conversations.beginTurn(actor, conversationId, "First question");
        AssistantTurnRef second = conversations.beginTurn(actor, conversationId, "Second question");
        conversations.completeTurn(actor, second, UUID.randomUUID(), "Second answer");
        conversations.completeTurn(actor, first, UUID.randomUUID(), "First answer");

        // Persisted order is U1, U2, A2, A1. Turn identity, not sequence order,
        // decides which answer belongs to which question.
        assertEquals(
                List.of(
                        user("Opening question"),
                        assistant("Opening answer"),
                        user("First question"),
                        assistant("First answer"),
                        user("Second question"),
                        assistant("Second answer")),
                conversations.recentCompletedTurns(
                        actor.organizationId(), conversationId, 10));
    }

    @Test
    void keepsTheWindowToWholeTurnsSoItNeverOpensOnAnAnswer() {
        CurrentActor actor = seededActor();
        UUID conversationId = ask(actor, null, "Question 0", "Answer 0");
        for (int turn = 1; turn < 12; turn++) {
            ask(actor, conversationId, "Question " + turn, "Answer " + turn);
        }

        List<AssistantContextMessage> context =
                conversations.recentCompletedTurns(actor.organizationId(), conversationId, 10);

        assertEquals(20, context.size());
        assertEquals(user("Question 2"), context.getFirst());
        assertEquals(assistant("Answer 11"), context.getLast());
        for (int index = 0; index < context.size(); index += 2) {
            assertEquals(AssistantConversationRole.USER, context.get(index).role());
            assertEquals(AssistantConversationRole.ASSISTANT, context.get(index + 1).role());
        }
    }

    @Test
    void readsNothingForAConversationOfAnotherOrganization() {
        CurrentActor actor = seededActor();
        CurrentActor other = seededActor();
        UUID conversationId = ask(actor, null, "First question", "First answer");

        assertTrue(conversations
                .recentCompletedTurns(other.organizationId(), conversationId, 10)
                .isEmpty());
    }

    @Test
    void ignoresRowsWrittenBeforeTurnIdentityExisted() {
        CurrentActor actor = seededActor();
        UUID conversationId = ask(actor, null, "First question", "First answer");
        insertLegacyMessage(actor, conversationId, "USER", "Legacy question");
        insertLegacyMessage(actor, conversationId, "ASSISTANT", "Legacy answer");

        // Transcript-visible but context-ineligible: their pairing cannot be
        // recovered, so they must not be offered to the model as a turn.
        assertEquals(
                List.of(user("First question"), assistant("First answer")),
                conversations.recentCompletedTurns(
                        actor.organizationId(), conversationId, 10));
    }

    private UUID ask(CurrentActor actor, UUID conversationId, String question, String answer) {
        AssistantTurnRef turn = conversations.beginTurn(actor, conversationId, question);
        conversations.completeTurn(actor, turn, UUID.randomUUID(), answer);
        return turn.conversationId();
    }

    private UUID openingTurnId(UUID conversationId) {
        return jdbc.queryForObject(
                """
                SELECT turn_id FROM assistant_conversation_messages
                WHERE conversation_id = ? ORDER BY sequence_id LIMIT 1
                """,
                UUID.class,
                conversationId);
    }

    private static AssistantContextMessage user(String content) {
        return new AssistantContextMessage(AssistantConversationRole.USER, content);
    }

    private static AssistantContextMessage assistant(String content) {
        return new AssistantContextMessage(AssistantConversationRole.ASSISTANT, content);
    }

    private void insertLegacyMessage(
            CurrentActor actor, UUID conversationId, String role, String content) {
        jdbc.update(
                """
                INSERT INTO assistant_conversation_messages (
                    id, conversation_id, turn_id, organization_id, actor_user_id,
                    role, content, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(),
                conversationId,
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
                VALUES (?, 'Transcript context', now(), now(), 0)
                """,
                organizationId);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Transcript context actor', ?, 'STANDARD', true, now(), now(), 0)
                """,
                actorId,
                organizationId,
                actorId + "@example.test");
        return new CurrentActor(
                actorId,
                organizationId,
                null,
                "Transcript context actor",
                actorId + "@example.test",
                Clearance.STANDARD);
    }
}
