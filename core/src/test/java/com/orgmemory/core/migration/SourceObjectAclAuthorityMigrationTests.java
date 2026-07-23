package com.orgmemory.core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migrates a database that already holds evidence.
 *
 * <p>Every other suite starts Flyway against an empty schema, which is the one condition under
 * which a data-transforming migration cannot fail: an UPDATE over no rows satisfies every
 * constraint. V23 rewrites the values in a column while a check constraint referencing that
 * column is still in force, and passed everywhere until it met a database with a single row in
 * it. So this stops at the version before, seeds the shapes the old schema allowed, and
 * migrates the rest of the way.
 *
 * <p>The version numbers moved once, when the connector migrations were renumbered to land
 * after main's; the migration this pins is the one separating source system from ACL authority,
 * whatever ordinal it currently carries.
 *
 * <p>It uses Flyway directly rather than a Spring context because the point is the migration
 * sequence, and a context would only offer to run it in one shot.
 */
@Testcontainers
class SourceObjectAclAuthorityMigrationTests {

    /** The last version at which {@code source_objects.source_type} still existed. */
    private static final String BEFORE_THE_SPLIT = "24";

    private static final UUID ORG = UUID.fromString("c9000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("c9000000-0000-4000-8000-000000000002");
    private static final UUID USER = UUID.fromString("c9000000-0000-4000-8000-000000000003");
    private static final UUID UPLOADED = UUID.fromString("c9000000-0000-4000-8000-000000000010");
    private static final UUID CRAWLED = UUID.fromString("c9000000-0000-4000-8000-000000000011");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Test
    void anExistingObjectKeepsItsSystemAndGainsTheRightAuthority() throws SQLException {
        migrate(BEFORE_THE_SPLIT);
        seedOneOfEachOldShape();

        migrate(null);

        Map<UUID, String[]> rows = readAuthorityAndSystem();
        assertEquals(
                "ORGMEMORY",
                rows.get(UPLOADED)[0],
                "an upload keeps the ingestion ACL intersected with the current one");
        assertEquals("upload", rows.get(UPLOADED)[1]);
        assertEquals(
                "SOURCE",
                rows.get(CRAWLED)[0],
                "a crawled object defers to the source's latest sealed generation");
        assertEquals("slack", rows.get(CRAWLED)[1], "the source's name survives, lowercased");
    }

    private static void seedOneOfEachOldShape() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO organizations (id, name, created_at, updated_at, version)
                    VALUES ('%s', 'Migration Fixture', now(), now(), 0)
                    """.formatted(ORG));
            statement.execute("""
                    INSERT INTO app_users (
                        id, organization_id, department_id, name, email, role,
                        created_at, updated_at, version)
                    VALUES ('%s', '%s', NULL, 'Fixture', 'fixture@example.com',
                            'ORGANIZATION_ADMIN', now(), now(), 0)
                    """.formatted(USER, ORG));
            statement.execute("""
                    INSERT INTO knowledge_spaces (
                        id, organization_id, department_id, space_key, name, active,
                        created_at, updated_at, version)
                    VALUES ('%s', '%s', NULL, 'fixture', 'Fixture', true, now(), now(), 0)
                    """.formatted(SPACE, ORG));
            insertObject(statement, UPLOADED, "UPLOAD", "manual", "doc-1");
            insertObject(statement, CRAWLED, "SLACK", "T-fixture", "C-thread-1");
        }
    }

    private static void insertObject(
            Statement statement, UUID id, String sourceType, String connectionKey, String externalId)
            throws SQLException {
        statement.execute("""
                INSERT INTO source_objects (
                    id, organization_id, knowledge_space_id, department_id, created_by_user_id,
                    source_type, source_connection_key, external_object_id, title, classification,
                    declared_access, current_revision_id, status, created_at, updated_at, version)
                VALUES ('%s', '%s', '%s', NULL, '%s', '%s', '%s', '%s', 'Fixture object',
                        'INTERNAL', 'ALL_EMPLOYEES', NULL, 'ACTIVE', now(), now(), 0)
                """.formatted(id, ORG, SPACE, USER, sourceType, connectionKey, externalId));
    }

    private static Map<UUID, String[]> readAuthorityAndSystem() throws SQLException {
        Map<UUID, String[]> rows = new LinkedHashMap<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, acl_authority, source_system FROM source_objects");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rows.put(
                        UUID.fromString(results.getString("id")),
                        new String[] {results.getString("acl_authority"), results.getString("source_system")});
            }
        }
        return rows;
    }

    private static void migrate(String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target == null ? "latest" : target)
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
