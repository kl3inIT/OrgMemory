package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
class IdentityOrganizationEmailCutoverMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_A =
            UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_B =
            UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID DEPARTMENT_A =
            UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID DEPARTMENT_B =
            UUID.fromString("d0000000-0000-4000-8000-000000000002");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAtVersionEight() {
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
                .target("8")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        insertOrganization(ORGANIZATION_A, "Organization A");
        insertOrganization(ORGANIZATION_B, "Organization B");
        insertDepartment(DEPARTMENT_A, ORGANIZATION_A, "Department A");
        insertDepartment(DEPARTMENT_B, ORGANIZATION_B, "Department B");
    }

    @Test
    void cutoverAllowsTheSameNormalizedEmailAcrossOrganizationsOnly() {
        insertUser(ORGANIZATION_A, DEPARTMENT_A, "shared@example.test");
        migrateLatest();

        assertDoesNotThrow(
                () -> insertUser(ORGANIZATION_B, DEPARTMENT_B, "SHARED@EXAMPLE.TEST"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertUser(ORGANIZATION_A, DEPARTMENT_A, "SHARED@EXAMPLE.TEST"));
    }

    @Test
    void cutoverPreflightRejectsDuplicatesWithinOneOrganization() {
        jdbc.execute("DROP INDEX public.uq_app_users_email_lower");
        insertUser(ORGANIZATION_A, DEPARTMENT_A, "duplicate@example.test");
        insertUser(ORGANIZATION_A, DEPARTMENT_A, "DUPLICATE@EXAMPLE.TEST");

        FlywayException failure = assertThrows(FlywayException.class, this::migrateLatest);

        assertTrue(
                allMessages(failure).contains(
                        "organization email cutover preflight failed:"
                                + " duplicate_organization_emails=1"),
                allMessages(failure));
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
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

    private void insertUser(UUID organizationId, UUID departmentId, String email) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role,
                    created_at, updated_at, version, active)
                VALUES (?, ?, ?, 'Email cutover user', ?, 'EMPLOYEE',
                        now(), now(), 0, true)
                """,
                UUID.randomUUID(),
                organizationId,
                departmentId,
                email);
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
