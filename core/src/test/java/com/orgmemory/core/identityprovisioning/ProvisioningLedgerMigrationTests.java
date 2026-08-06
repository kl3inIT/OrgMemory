package com.orgmemory.core.identityprovisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class ProvisioningLedgerMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_A =
            UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_B =
            UUID.fromString("f0000000-0000-4000-8000-000000000001");
    private static final UUID USER_A =
            UUID.fromString("e0000000-0000-4000-8000-000000000002");
    private static final UUID USER_B =
            UUID.fromString("f0000000-0000-4000-8000-000000000002");
    private static final UUID CONNECTION_A =
            UUID.fromString("e0000000-0000-4000-8000-000000000003");
    private static final UUID CONNECTION_B =
            UUID.fromString("f0000000-0000-4000-8000-000000000003");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateLatestAndSeedTenants() {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        insertOrganization(ORGANIZATION_A, "Organization A");
        insertOrganization(ORGANIZATION_B, "Organization B");
        insertUser(USER_A, ORGANIZATION_A, "owner-a@example.test");
        insertUser(USER_B, ORGANIZATION_B, "owner-b@example.test");
        insertConnection(CONNECTION_A, ORGANIZATION_A, "directory-a");
        insertConnection(CONNECTION_B, ORGANIZATION_B, "directory-b");
    }

    @Test
    void tenantCompositeKeysRejectCrossOrganizationReferences() {
        assertFalse(insertResource(
                UUID.randomUUID(),
                ORGANIZATION_A,
                CONNECTION_B,
                null,
                "external-cross",
                "cross@example.test",
                "workforce-cross"));

        assertFalse(insertResource(
                UUID.randomUUID(),
                ORGANIZATION_A,
                CONNECTION_A,
                USER_B,
                "external-cross-user",
                "cross-user@example.test",
                "workforce-cross-user"));
    }

    @Test
    void duplicateDirectoryIdentifiersAreDatabaseConstrainedUnderConcurrency()
            throws Exception {
        assertEquals(1, concurrentlyInsertResources("external-1", "first@example.test", "wk-1"));
        clearResources();
        assertEquals(1, concurrentlyInsertResources(null, "same@example.test", "wk-2"));
        clearResources();
        assertEquals(1, concurrentlyInsertResources(null, "one@example.test", "same-workforce"));
    }

    @Test
    void publicTokenIdAndOneActiveConnectionAreDatabaseConstrainedUnderConcurrency()
            throws Exception {
        UUID secondConnection = UUID.randomUUID();
        insertConnection(secondConnection, ORGANIZATION_A, "directory-a-secondary");

        assertEquals(
                1,
                concurrentSuccesses(List.of(
                        () -> insertCredential(UUID.randomUUID(), CONNECTION_A, "public-shared"),
                        () -> insertCredential(UUID.randomUUID(), secondConnection, "public-shared"))));

        assertEquals(
                1,
                concurrentSuccesses(List.of(
                        () -> enableConnection(CONNECTION_A),
                        () -> enableConnection(secondConnection))));
    }

    @Test
    void compatibilityTriggerCannotReviveDirectoryDisabledUser() {
        jdbc.update(
                "UPDATE app_users SET directory_access_enabled = false WHERE id = ?",
                USER_A);
        assertFalse(readActive(USER_A));

        // This is the only column a previous binary would update.
        jdbc.update("UPDATE app_users SET active = true WHERE id = ?", USER_A);

        assertFalse(readActive(USER_A));
        assertTrue(jdbc.queryForObject(
                "SELECT local_access_enabled FROM app_users WHERE id = ?",
                Boolean.class,
                USER_A));
    }

    @Test
    void insertTriggerDistinguishesOldBinaryLocalDisableFromDirectoryDisable() {
        UUID oldBinaryUser = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance,
                    created_at, updated_at, version, active)
                VALUES (?, ?, 'Old binary inactive', 'old-inactive@example.test',
                    'STANDARD', now(), now(), 0, false)
                """,
                oldBinaryUser,
                ORGANIZATION_A);
        assertFalse(readActive(oldBinaryUser));
        assertFalse(jdbc.queryForObject(
                "SELECT local_access_enabled FROM app_users WHERE id = ?",
                Boolean.class,
                oldBinaryUser));

        UUID directoryDisabledUser = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    local_access_enabled, directory_access_enabled,
                    provisioning_access_ready, created_at, updated_at, version)
                VALUES (?, ?, 'Directory inactive', 'directory-inactive@example.test',
                    'STANDARD', false, true, false, true, now(), now(), 0)
                """,
                directoryDisabledUser,
                ORGANIZATION_A);
        assertFalse(readActive(directoryDisabledUser));
        assertTrue(jdbc.queryForObject(
                "SELECT local_access_enabled FROM app_users WHERE id = ?",
                Boolean.class,
                directoryDisabledUser));

        jdbc.update(
                "UPDATE app_users SET directory_access_enabled = true WHERE id = ?",
                directoryDisabledUser);
        assertTrue(readActive(directoryDisabledUser));
    }

    @Test
    void localSuspensionWinsOverDirectoryActivationInDatabase() {
        jdbc.update(
                """
                UPDATE app_users
                SET local_access_enabled = false,
                    directory_access_enabled = true,
                    active = true
                WHERE id = ?
                """,
                USER_A);

        assertFalse(readActive(USER_A));
    }

    @Test
    void auditRowsAreAppendOnlyAndSchemaHasNoRawSecretOrPayloadColumns() {
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO provisioning_events (
                    id, organization_id, connection_id, request_id, operation,
                    outcome, changed_fields, occurred_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'request-safe', 'PATCH', 'SUCCEEDED',
                    'active,userName', now(), now(), now(), 0)
                """,
                eventId,
                ORGANIZATION_A,
                CONNECTION_A);

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE provisioning_events SET reason_code = 'MUTATED' WHERE id = ?",
                        eventId));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM provisioning_events WHERE id = ?", eventId));

        List<String> forbiddenColumns = jdbc.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'provisioning_connections',
                      'provisioning_credentials',
                      'scim_user_resources',
                      'provisioning_events')
                  AND (
                      column_name LIKE '%raw%'
                      OR column_name LIKE '%payload%'
                      OR column_name IN ('secret', 'token', 'token_value', 'credential')
                  )
                """,
                String.class);
        assertTrue(forbiddenColumns.isEmpty(), forbiddenColumns.toString());
    }

    private int concurrentlyInsertResources(
            String externalId, String userName, String workforceKey) throws Exception {
        return concurrentSuccesses(List.of(
                () -> insertResource(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        CONNECTION_A,
                        null,
                        externalId,
                        userName,
                        workforceKey),
                () -> insertResource(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        CONNECTION_A,
                        null,
                        externalId,
                        userName,
                        workforceKey)));
    }

    private int concurrentSuccesses(List<Callable<Boolean>> writes) throws Exception {
        try (var executor = Executors.newFixedThreadPool(writes.size())) {
            int successes = 0;
            for (var result : executor.invokeAll(new ArrayList<>(writes))) {
                if (result.get()) {
                    successes++;
                }
            }
            return successes;
        }
    }

    private boolean insertResource(
            UUID id,
            UUID organizationId,
            UUID connectionId,
            UUID appUserId,
            String externalId,
            String userName,
            String workforceKey) {
        try {
            new JdbcTemplate(dataSource).update(
                    """
                    INSERT INTO scim_user_resources (
                        id, organization_id, connection_id, app_user_id, external_id,
                        normalized_user_name, workforce_key, directory_active,
                        created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                    """,
                    id,
                    organizationId,
                    connectionId,
                    appUserId,
                    externalId,
                    userName,
                    workforceKey);
            return true;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }

    private boolean insertCredential(UUID id, UUID connectionId, String publicTokenId) {
        try {
            new JdbcTemplate(dataSource).update(
                    """
                    INSERT INTO provisioning_credentials (
                        id, organization_id, connection_id, public_token_id,
                        verifier_digest, verifier_key_version, users_scope, groups_scope,
                        created_by_user_id, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
                        1, true, false, ?,
                        now(), now(), 0)
                    """,
                    id,
                    ORGANIZATION_A,
                    connectionId,
                    publicTokenId,
                    USER_A);
            return true;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }

    private boolean enableConnection(UUID connectionId) {
        try {
            return new JdbcTemplate(dataSource).update(
                            """
                            UPDATE provisioning_connections
                            SET operational_state = 'ENABLED', updated_at = now(),
                                version = version + 1
                            WHERE id = ?
                            """,
                            connectionId)
                    == 1;
        } catch (DataIntegrityViolationException expected) {
            return false;
        }
    }

    private void insertOrganization(UUID id, String name) {
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                """,
                id,
                name);
    }

    private void insertUser(UUID id, UUID organizationId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance,
                    created_at, updated_at, version, active)
                VALUES (?, ?, 'Provisioning owner', ?, 'STANDARD', now(), now(), 0, true)
                """,
                id,
                organizationId,
                email);
    }

    private void insertConnection(UUID id, UUID organizationId, String alias) {
        jdbc.update(
                """
                INSERT INTO provisioning_connections (
                    id, organization_id, alias, provider_profile,
                    configuration_status, operational_state, users_enabled,
                    groups_enabled, correlation_probe_status,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, 'GENERIC_SCIM', 'DRAFT', 'DISABLED', true,
                    false, 'NOT_RUN', now(), now(), 0)
                """,
                id,
                organizationId,
                alias);
    }

    private boolean readActive(UUID userId) {
        return jdbc.queryForObject(
                "SELECT active FROM app_users WHERE id = ?",
                Boolean.class,
                userId);
    }

    private void clearResources() {
        jdbc.update("DELETE FROM scim_user_resources");
    }
}
