package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.core.assistant.AssistantAnswerSentiment;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class AssistantAnswerFeedbackConcurrencyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssistantConversationService conversations;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void serializesConcurrentSetOperationsForOneAnswer() throws Exception {
        Scenario scenario = scenario();

        runConcurrently(24, index -> () -> {
            conversations.setAnswerFeedback(
                    scenario.actor(),
                    scenario.answerId(),
                    index % 2 == 0
                            ? AssistantAnswerSentiment.HELPFUL
                            : AssistantAnswerSentiment.NOT_HELPFUL);
            return null;
        });

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM assistant_answer_feedback WHERE message_id = ?",
                        Integer.class,
                        scenario.answerId()));
    }

    @Test
    void serializesConcurrentSetAndDeleteOperationsForOneAnswer() throws Exception {
        Scenario scenario = scenario();
        conversations.setAnswerFeedback(
                scenario.actor(), scenario.answerId(), AssistantAnswerSentiment.HELPFUL);

        runConcurrently(24, index -> () -> {
            if (index % 2 == 0) {
                conversations.setAnswerFeedback(
                        scenario.actor(),
                        scenario.answerId(),
                        AssistantAnswerSentiment.NOT_HELPFUL);
            } else {
                conversations.deleteAnswerFeedback(scenario.actor(), scenario.answerId());
            }
            return null;
        });

        conversations.setAnswerFeedback(
                scenario.actor(), scenario.answerId(), AssistantAnswerSentiment.HELPFUL);
        assertEquals(
                "HELPFUL",
                jdbc.queryForObject(
                        "SELECT sentiment FROM assistant_answer_feedback WHERE message_id = ?",
                        String.class,
                        scenario.answerId()));
    }

    private Scenario scenario() {
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Feedback concurrency', now(), now(), 0)
                """,
                organizationId);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, role, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Feedback actor', ?, 'EMPLOYEE', true, now(), now(), 0)
                """,
                actorId,
                organizationId,
                actorId + "@example.test");

        CurrentActor actor = new CurrentActor(
                actorId,
                organizationId,
                null,
                "Feedback actor",
                actorId + "@example.test",
                UserRole.EMPLOYEE);
        UUID conversationId = conversations.beginTurn(actor, null, "What is the policy?");
        UUID answerId = UUID.randomUUID();
        conversations.completeTurn(actor, conversationId, answerId, "The policy is available.");
        return new Scenario(actor, answerId);
    }

    private static void runConcurrently(
            int attemptCount, AttemptFactory attemptFactory) throws Exception {
        CountDownLatch ready = new CountDownLatch(attemptCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> attempts = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(attemptCount)) {
            for (int index = 0; index < attemptCount; index++) {
                int attemptIndex = index;
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attemptFactory.create(attemptIndex).call();
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Feedback attempts did not become ready");
            }
            start.countDown();
            for (Future<Void> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }
        }
    }

    @FunctionalInterface
    private interface AttemptFactory {

        Callable<Void> create(int index);
    }

    private record Scenario(CurrentActor actor, UUID answerId) {
    }
}
