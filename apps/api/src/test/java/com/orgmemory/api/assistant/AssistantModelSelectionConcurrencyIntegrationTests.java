package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.ai.AiGatewayAdministrationService;
import com.orgmemory.core.ai.AiGatewayCategory;
import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProfileView;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiAssistantModelDefinition;
import com.orgmemory.core.ai.AiAssistantModelActivationView;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.ai.AssistantModelRouteAuthority;
import com.orgmemory.core.ai.AssistantModelSelectionRef;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.secret.SecretValue;
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
class AssistantModelSelectionConcurrencyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssistantConversationService conversations;

    @Autowired
    AssistantModelAuthorityService authority;

    @Autowired
    AiGatewayAdministrationService administration;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentDisableSelectAndTurnNeverRevivesTheDisabledActivation() throws Exception {
        Scenario scenario = scenario();

        runConcurrently(24, index -> {
            if (index % 3 == 0) {
                return () -> {
                    administration.replaceAssistantModels(
                            scenario.actor().organizationId(),
                            scenario.profileId(),
                            List.of(),
                            scenario.actor().userId());
                    return null;
                };
            }
            if (index % 3 == 1) {
                return () -> {
                    try {
                        AssistantModelRouteAuthority selected = authority.authorize(
                                scenario.actor().organizationId(),
                                scenario.activationId());
                        conversations.selectModel(
                                scenario.actor(),
                                scenario.conversationId(),
                                authority.selectionRef(selected));
                    } catch (BusinessConflictException expectedRace) {
                        // The disable won before authorization; fail-closed is the outcome.
                    }
                    return null;
                };
            }
            return () -> {
                try {
                    AssistantModelRouteAuthority selected = authority.authorize(
                            scenario.actor().organizationId(),
                            scenario.activationId());
                    conversations.beginTurn(
                            scenario.actor(),
                            scenario.conversationId(),
                            "Concurrent governed turn",
                            authority.selectionRef(selected));
                    try {
                        authority.revalidate(selected);
                    } catch (BusinessConflictException expectedRace) {
                        // The disable committed between controller authorization and subscribe.
                    }
                } catch (BusinessConflictException expectedRace) {
                    // The disable won before controller authorization.
                }
                return null;
            };
        });

        administration.replaceAssistantModels(
                scenario.actor().organizationId(),
                scenario.profileId(),
                List.of(),
                scenario.actor().userId());
        AssistantModelSelectionRef stored = conversations.modelSelection(
                scenario.actor(), scenario.conversationId());

        assertThrows(
                BusinessConflictException.class,
                () -> authority.authorize(
                        scenario.actor().organizationId(), scenario.activationId()));
        assertNull(authority.resolveSelectedActivation(
                scenario.actor().organizationId(), stored));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM ai_assistant_model_activations WHERE id = ? AND enabled",
                        Integer.class,
                        scenario.activationId()));
    }

    private Scenario scenario() {
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) VALUES (?, 'Model concurrency', now(), now(), 0)",
                organizationId);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Model actor', ?, 'STANDARD', true, now(), now(), 0)
                """,
                actorId,
                organizationId,
                actorId + "@example.test");
        AiGatewayProfileView profile = administration.create(
                organizationId,
                "concurrent-ai",
                "Concurrent AI",
                AiGatewayPreset.OPENAI,
                AiGatewayCategory.DIRECT_PROVIDER,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                "https://api.openai.com/v1",
                60,
                true,
                SecretValue.of("test-concurrent-secret"),
                actorId);
        administration.setRoute(
                organizationId,
                AiWorkload.ASSISTANT_CHAT,
                profile.id(),
                "gpt-default",
                actorId);
        AiAssistantModelActivationView activation = administration.replaceAssistantModels(
                        organizationId,
                        profile.id(),
                        List.of(new AiAssistantModelDefinition("gpt-fast", "Fast")),
                        actorId)
                .getFirst();
        CurrentActor actor = new CurrentActor(
                actorId,
                organizationId,
                null,
                "Model actor",
                actorId + "@example.test",
                Clearance.STANDARD);
        UUID conversationId =
                conversations.beginTurn(actor, null, "Initial turn").conversationId();
        return new Scenario(actor, profile.id(), activation.id(), conversationId);
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
                throw new IllegalStateException("Model selection attempts did not become ready");
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

    private record Scenario(
            CurrentActor actor,
            UUID profileId,
            UUID activationId,
            UUID conversationId) {
    }
}
