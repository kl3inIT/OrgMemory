package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
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
class ClearanceSeparationMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION =
            UUID.fromString("ce000000-0000-4000-8000-000000000001");
    private static final UUID DEPARTMENT =
            UUID.fromString("ce000000-0000-4000-8000-000000000002");
    private static final UUID STANDARD_USER =
            UUID.fromString("ce000000-0000-4000-8000-000000000003");
    private static final UUID EXECUTIVE_USER =
            UUID.fromString("ce000000-0000-4000-8000-000000000004");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAtVersionTwentyThree() {
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
                .target("23")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);

        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, 'Clearance Test', now(), now(), 0)",
                ORGANIZATION);
        jdbc.update(
                "INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'Finance', now(), now(), 0)",
                DEPARTMENT,
                ORGANIZATION);
        insertLegacyUser(STANDARD_USER, "standard@example.test", "ADMIN");
        insertLegacyUser(EXECUTIVE_USER, "executive@example.test", "EXECUTIVE");
        insertLegacyInvitation("lead@example.test", "TEAM_LEAD");
        insertLegacyInvitation("executive-invite@example.test", "EXECUTIVE");
    }

    @Test
    void legacyRolesCollapseToTheClosedClearanceEnum() {
        migrateLatest();

        assertEquals(
                Map.of(STANDARD_USER, "STANDARD", EXECUTIVE_USER, "EXECUTIVE"),
                jdbc.query(
                                "SELECT id, clearance FROM app_users ORDER BY id",
                                (result, row) -> Map.entry(
                                        result.getObject("id", UUID.class),
                                        result.getString("clearance")))
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(
                Map.of(
                        "lead@example.test", "STANDARD",
                        "executive-invite@example.test", "EXECUTIVE"),
                jdbc.query(
                                "SELECT email, clearance FROM user_invitations ORDER BY email",
                                (result, row) -> Map.entry(
                                        result.getString("email"),
                                        result.getString("clearance")))
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name IN ('app_users', 'user_invitations')
                          AND column_name = 'role'
                        """,
                        Integer.class));
    }

    @Test
    void databaseRejectsUnknownClearanceValues() {
        migrateLatest();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "UPDATE app_users SET clearance = 'MANAGER' WHERE id = ?",
                        STANDARD_USER));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(
                        "UPDATE user_invitations SET clearance = 'DIRECTOR' WHERE email = ?",
                        "lead@example.test"));
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertLegacyUser(UUID id, String email, String role) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role,
                    active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                """,
                id,
                ORGANIZATION,
                DEPARTMENT,
                email,
                email,
                role);
    }

    private void insertLegacyInvitation(String email, String role) {
        jdbc.update(
                """
                INSERT INTO user_invitations (
                    id, organization_id, email, department_id, role,
                    invited_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(),
                ORGANIZATION,
                email,
                DEPARTMENT,
                role,
                STANDARD_USER);
    }
}
