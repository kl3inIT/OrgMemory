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
class AssistantModelSelectionMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION = UUID.fromString("af000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ORGANIZATION = UUID.fromString("af000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("af000000-0000-4000-8000-000000000003");
    private static final UUID OTHER_ACTOR = UUID.fromString("af000000-0000-4000-8000-000000000004");
    private static final UUID PROFILE = UUID.fromString("af000000-0000-4000-8000-000000000005");
    private static final UUID OTHER_PROFILE = UUID.fromString("af000000-0000-4000-8000-000000000006");
    private static final UUID CONVERSATION = UUID.fromString("af000000-0000-4000-8000-000000000007");
    private static final UUID ACTIVATION = UUID.fromString("af000000-0000-4000-8000-000000000008");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateAndSeedTenantData() {
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
        insertOrganization(ORGANIZATION, "Model Selection Test");
        insertOrganization(OTHER_ORGANIZATION, "Other Tenant");
        insertActor(ACTOR, ORGANIZATION, "actor@example.test");
        insertActor(OTHER_ACTOR, OTHER_ORGANIZATION, "other@example.test");
        insertProfile(PROFILE, ORGANIZATION, ACTOR, "primary-ai");
        insertProfile(OTHER_PROFILE, OTHER_ORGANIZATION, OTHER_ACTOR, "other-ai");
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
        insertActivation(ACTIVATION, ORGANIZATION, PROFILE, ACTOR, "gpt-fast", true);
    }

    @Test
    void activeModelIdentityIsUniqueButARevokedIdentityCanBeReplaced() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertActivation(
                        UUID.randomUUID(), ORGANIZATION, PROFILE, ACTOR, "gpt-fast", true));

        jdbc.update(
                """
                UPDATE ai_assistant_model_activations
                SET enabled = false, disabled_by_user_id = ?, disabled_at = now(), updated_at = now()
                WHERE id = ?
                """,
                ACTOR,
                ACTIVATION);
        UUID replacement = UUID.randomUUID();
        insertActivation(replacement, ORGANIZATION, PROFILE, ACTOR, "gpt-fast", true);

        assertEquals(
                replacement,
                jdbc.queryForObject(
                        "SELECT id FROM ai_assistant_model_activations WHERE enabled",
                        UUID.class));
    }

    @Test
    void conversationSelectionRequiresACompleteSameTenantActivationTuple() {
        jdbc.update(
                """
                UPDATE assistant_conversations
                SET selected_model_activation_id = ?,
                    selected_route_override_id = ?,
                    selected_route_override_version = 2
                WHERE id = ?
                """,
                ACTIVATION,
                UUID.randomUUID(),
                CONVERSATION);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "UPDATE assistant_conversations SET selected_route_override_id = NULL WHERE id = ?",
                        CONVERSATION));

        UUID foreignActivation = UUID.randomUUID();
        insertActivation(
                foreignActivation,
                OTHER_ORGANIZATION,
                OTHER_PROFILE,
                OTHER_ACTOR,
                "other-model",
                true);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "UPDATE assistant_conversations SET selected_model_activation_id = ? WHERE id = ?",
                        foreignActivation,
                        CONVERSATION));
    }

    private void insertOrganization(UUID id, String name) {
        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) VALUES (?, ?, now(), now(), 0)",
                id,
                name);
    }

    private void insertActor(UUID id, UUID organizationId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, 'Assistant Actor', ?, 'STANDARD', true, now(), now(), 0)
                """,
                id,
                organizationId,
                email);
    }

    private void insertProfile(UUID id, UUID organizationId, UUID actorId, String key) {
        jdbc.update(
                """
                INSERT INTO ai_gateway_profiles (
                    id, organization_id, gateway_key, display_name, preset, category,
                    protocol, supports_openai_reasoning_effort, base_url,
                    request_timeout_seconds, enabled, runtime_revision,
                    created_by_user_id, updated_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Organization AI', 'OPENAI', 'DIRECT_PROVIDER',
                    'OPENAI_COMPATIBLE', true, 'https://api.openai.com/v1',
                    60, true, 1, ?, ?, now(), now(), 0)
                """,
                id,
                organizationId,
                key,
                actorId,
                actorId);
    }

    private void insertActivation(
            UUID id,
            UUID organizationId,
            UUID profileId,
            UUID actorId,
            String modelId,
            boolean enabled) {
        jdbc.update(
                """
                INSERT INTO ai_assistant_model_activations (
                    id, organization_id, gateway_profile_id, model_id, display_name,
                    enabled, enabled_by_user_id, disabled_by_user_id, disabled_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, now(), now(), 0)
                """,
                id,
                organizationId,
                profileId,
                modelId,
                modelId,
                enabled,
                actorId);
    }
}
