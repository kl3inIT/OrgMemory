package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class IdentityTenantIntegrityMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_A =
            UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_B =
            UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID DEPARTMENT_A =
            UUID.fromString("a0000000-0000-4000-8000-000000000002");
    private static final UUID DEPARTMENT_B =
            UUID.fromString("b0000000-0000-4000-8000-000000000002");
    private static final UUID USER_A =
            UUID.fromString("a0000000-0000-4000-8000-000000000003");
    private static final UUID USER_B =
            UUID.fromString("b0000000-0000-4000-8000-000000000003");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabaseAtVersionSeven() {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        seedOrganizationsAndUsers();
    }

    @Test
    void populatedVersionSevenSchemaUpgradesAndRejectsCrossTenantIdentityReferences() {
        insertInvitation(
                UUID.randomUUID(),
                ORGANIZATION_A,
                DEPARTMENT_A,
                USER_A,
                null,
                "valid@example.test");

        migrateTo("8");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        DEPARTMENT_B,
                        "cross-department@example.test"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertInvitation(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        DEPARTMENT_B,
                        USER_A,
                        null,
                        "cross-department-invite@example.test"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertInvitation(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        DEPARTMENT_A,
                        USER_B,
                        null,
                        "cross-inviter@example.test"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertInvitation(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        DEPARTMENT_A,
                        USER_A,
                        USER_B,
                        "cross-accepted-user@example.test"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser(
                        UUID.randomUUID(),
                        ORGANIZATION_A,
                        DEPARTMENT_A,
                        "OWNER@EXAMPLE.TEST"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser(
                        UUID.randomUUID(),
                        ORGANIZATION_B,
                        DEPARTMENT_B,
                        "owner@example.test"));
    }

    @Test
    void populatedVersionSevenSchemaFailsPreflightInsteadOfGuessingOwnership() {
        insertUser(
                UUID.randomUUID(),
                ORGANIZATION_A,
                DEPARTMENT_B,
                "invalid-tenant@example.test");

        FlywayException failure = assertThrows(FlywayException.class, () -> migrateTo("8"));

        assertTrue(
                allMessages(failure).contains(
                        "identity tenant integrity preflight failed:"
                                + " duplicate_organization_emails=0,"
                                + " app_user_department=1,"
                                + " invitation_department=0,"
                                + " invitation_inviter=0,"
                                + " invitation_accepted_user=0"),
                allMessages(failure));
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void seedOrganizationsAndUsers() {
        insertOrganization(ORGANIZATION_A, "Organization A");
        insertOrganization(ORGANIZATION_B, "Organization B");
        insertDepartment(DEPARTMENT_A, ORGANIZATION_A, "Department A");
        insertDepartment(DEPARTMENT_B, ORGANIZATION_B, "Department B");
        insertUser(USER_A, ORGANIZATION_A, DEPARTMENT_A, "owner@example.test");
        insertUser(USER_B, ORGANIZATION_B, DEPARTMENT_B, "member@example.test");
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

    private void insertDepartment(UUID id, UUID organizationId, String name) {
        jdbc.update(
                """
                INSERT INTO departments (
                    id, organization_id, name, created_at, updated_at, version)
                VALUES (?, ?, ?, now(), now(), 0)
                """,
                id,
                organizationId,
                name);
    }

    private void insertUser(UUID id, UUID organizationId, UUID departmentId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role,
                    created_at, updated_at, version, active)
                VALUES (?, ?, ?, 'Identity test user', ?, 'EMPLOYEE', now(), now(), 0, true)
                """,
                id,
                organizationId,
                departmentId,
                email);
    }

    private void insertInvitation(
            UUID id,
            UUID organizationId,
            UUID departmentId,
            UUID invitedByUserId,
            UUID acceptedAppUserId,
            String email) {
        jdbc.update(
                """
                INSERT INTO user_invitations (
                    id, organization_id, email, department_id, role, invited_by_user_id,
                    accepted_at, accepted_app_user_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'EMPLOYEE', ?, CASE WHEN ?::uuid IS NULL THEN NULL ELSE now() END,
                        ?, now(), now(), 0)
                """,
                id,
                organizationId,
                email,
                departmentId,
                invitedByUserId,
                acceptedAppUserId,
                acceptedAppUserId);
    }

    private static String allMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}
