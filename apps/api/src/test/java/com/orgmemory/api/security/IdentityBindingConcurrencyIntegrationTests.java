package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.UserProvisioningService;
import com.orgmemory.core.organization.Clearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class IdentityBindingConcurrencyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID DEPARTMENT_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000002");
    private static final UUID INVITER_ID =
            UUID.fromString("71000000-0000-4000-8000-000000000003");
    private static final String ISSUER = "https://identity.example.test/realms/acme";
    private static final String SUBJECT = "concurrent-workforce-user";
    private static final String EMAIL = "concurrent@example.test";

    @Autowired
    UserProvisioningService provisioning;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentFirstLoginLeavesOneBindingAndOneAcceptedInvitation() throws Exception {
        seedInvitation();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<AppUser>>> attempts = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(12)) {
            for (int index = 0; index < 50; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return provisioning.provisionFromInvitation(ISSUER, SUBJECT, EMAIL);
                }));
            }
            start.countDown();

            UUID winningUserId = null;
            for (Future<Optional<AppUser>> attempt : attempts) {
                Optional<AppUser> result = attempt.get();
                assertTrue(result.isPresent());
                if (winningUserId == null) {
                    winningUserId = result.orElseThrow().getId();
                }
                assertEquals(winningUserId, result.orElseThrow().getId());
            }
        }

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM external_identities"
                                + " WHERE issuer = ? AND subject = ?",
                        Integer.class,
                        ISSUER,
                        SUBJECT));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM user_invitations"
                                + " WHERE organization_id = ? AND email = ?"
                                + " AND accepted_at IS NOT NULL",
                        Integer.class,
                        ORGANIZATION_ID,
                        EMAIL));
    }

    private void seedInvitation() {
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Concurrency organization', now(), now(), 0)
                """,
                ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO departments (
                    id, organization_id, name, created_at, updated_at, version)
                VALUES (?, ?, 'Identity', now(), now(), 0)
                """,
                DEPARTMENT_ID,
                ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, clearance,
                    created_at, updated_at, version, active)
                VALUES (?, ?, ?, 'Identity admin', 'identity-admin@example.test', 'STANDARD',
                        now(), now(), 0, true)
                """,
                INVITER_ID,
                ORGANIZATION_ID,
                DEPARTMENT_ID);
        provisioning.invite(
                ORGANIZATION_ID,
                EMAIL,
                DEPARTMENT_ID,
                Clearance.STANDARD,
                INVITER_ID);
    }
}
