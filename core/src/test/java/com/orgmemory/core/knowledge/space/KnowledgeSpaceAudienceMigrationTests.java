package com.orgmemory.core.knowledge.space;

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
class KnowledgeSpaceAudienceMigrationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION =
            UUID.fromString("ad000000-0000-4000-8000-000000000001");
    private static final UUID DEPARTMENT =
            UUID.fromString("ad000000-0000-4000-8000-000000000002");
    private static final UUID ORGANIZATION_SPACE =
            UUID.fromString("ad000000-0000-4000-8000-000000000003");
    private static final UUID DEPARTMENT_SPACE =
            UUID.fromString("ad000000-0000-4000-8000-000000000004");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAtVersionEighteen() {
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
                .target("18")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);

        jdbc.update(
                "INSERT INTO organizations (id, name, created_at, updated_at, version) "
                        + "VALUES (?, 'Audience Test', now(), now(), 0)",
                ORGANIZATION);
        jdbc.update(
                "INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'Sales', now(), now(), 0)",
                DEPARTMENT,
                ORGANIZATION);
        insertVersionEighteenSpace(ORGANIZATION_SPACE, null, "company");
        insertVersionEighteenSpace(DEPARTMENT_SPACE, DEPARTMENT, "sales");
    }

    @Test
    void populatedSpacesBackfillToOneValidVersionedAudience() {
        migrateLatest();

        assertEquals(
                Map.of(ORGANIZATION_SPACE, "ORGANIZATION", DEPARTMENT_SPACE, "DEPARTMENT"),
                jdbc.query(
                                "SELECT id, audience_mode FROM knowledge_spaces ORDER BY id",
                                (result, row) -> Map.entry(
                                        result.getObject("id", UUID.class),
                                        result.getString("audience_mode")))
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_spaces WHERE audience_version = 1",
                        Integer.class));
    }

    @Test
    void databaseRejectsModeAndDepartmentContradictions() {
        migrateLatest();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertCurrentSpace(UUID.randomUUID(), DEPARTMENT, "ORGANIZATION", "invalid-org"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertCurrentSpace(UUID.randomUUID(), null, "DEPARTMENT", "invalid-dept"));
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertVersionEighteenSpace(UUID id, UUID departmentId, String key) {
        jdbc.update(
                """
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, true, now(), now(), 0)
                """,
                id,
                ORGANIZATION,
                departmentId,
                key,
                key);
    }

    private void insertCurrentSpace(UUID id, UUID departmentId, String mode, String key) {
        jdbc.update(
                """
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, audience_mode, audience_version,
                    space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, ?, ?, true, now(), now(), 0)
                """,
                id,
                ORGANIZATION,
                departmentId,
                mode,
                key,
                key);
    }
}
