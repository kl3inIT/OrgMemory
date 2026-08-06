package com.orgmemory.api.identityprovisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.identityprovisioning.ProvisioningConflictException;
import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import com.orgmemory.core.identityprovisioning.ProvisioningProviderProfile;
import com.orgmemory.core.organization.UserProvisioningService;
import com.orgmemory.core.organization.Clearance;
import com.orgmemory.core.shared.error.BusinessConflictException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class ProvisioningUserAdoptionIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("a3000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("a3000000-0000-4000-8000-000000000002");
    private static final UUID EXISTING_USER_ID =
            UUID.fromString("a3000000-0000-4000-8000-000000000003");
    private static final String EMAIL = "employee@example.test";
    private static final String ISSUER =
            "https://identity.example.test/realms/orgmemory";
    private static final String SUBJECT = "keycloak-subject-employee";

    @Autowired
    ProvisioningLedgerService ledger;

    @Autowired
    UserProvisioningService userProvisioning;

    @Autowired
    JdbcTemplate jdbc;

    private UUID connectionId;

    @BeforeEach
    void seedExistingInvitedUser() {
        jdbc.update(
                "DELETE FROM external_identities WHERE app_user_id IN (?, ?)",
                ADMIN_USER_ID,
                EXISTING_USER_ID);
        jdbc.update(
                "DELETE FROM user_invitations WHERE organization_id = ?",
                ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM scim_user_resources WHERE organization_id = ?",
                ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM provisioning_credentials WHERE organization_id = ?",
                ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM provisioning_connections WHERE organization_id = ?",
                ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM app_users WHERE organization_id = ?",
                ORGANIZATION_ID);
        jdbc.update("DELETE FROM organizations WHERE id = ?", ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'SCIM adoption tenant', now(), now(), 0)
                """,
                ORGANIZATION_ID);
        insertUser(
                ADMIN_USER_ID,
                "SCIM Admin",
                "scim-admin@example.test",
                Clearance.STANDARD);
        insertUser(
                EXISTING_USER_ID,
                "Existing Employee",
                EMAIL,
                Clearance.STANDARD);
        userProvisioning.invite(
                ORGANIZATION_ID,
                EMAIL,
                null,
                Clearance.STANDARD,
                ADMIN_USER_ID);
        connectionId = ledger.createDisabledConnection(
                        ORGANIZATION_ID,
                        "workforce",
                        ProvisioningProviderProfile.MICROSOFT_ENTRA,
                        true,
                        false)
                .id();
    }

    @Test
    void scimAdoptsTheExistingUserConsumesTheInviteAndFirstLoginBindsByEmail() {
        var registered = register(connectionId, "entra-object-123");

        assertEquals(EXISTING_USER_ID, registered.appUserId());
        assertTrue(registered.adoptedExistingUser());
        assertTrue(registered.consumedInvitationId() != null);
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM app_users
                        WHERE organization_id = ? AND lower(email) = lower(?)
                        """,
                        Integer.class,
                        ORGANIZATION_ID,
                        EMAIL));
        assertTrue(jdbc.queryForObject(
                """
                SELECT directory_access_enabled
                FROM app_users WHERE id = ?
                """,
                Boolean.class,
                EXISTING_USER_ID));
        assertEquals(
                "ACCEPTED",
                jdbc.queryForObject(
                        """
                        SELECT CASE WHEN accepted_at IS NULL THEN 'OPEN' ELSE 'ACCEPTED' END
                        FROM user_invitations WHERE id = ?
                        """,
                        String.class,
                        registered.consumedInvitationId()));

        var signedIn = userProvisioning.provisionForVerifiedSignIn(
                ISSUER, SUBJECT, EMAIL);
        assertEquals(EXISTING_USER_ID, signedIn.orElseThrow().getId());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM external_identities
                        WHERE issuer = ? AND subject = ? AND app_user_id = ?
                        """,
                        Integer.class,
                        ISSUER,
                        SUBJECT,
                        EXISTING_USER_ID));
    }

    @Test
    void invitationCannotReclaimAScimManagedUser() {
        register(connectionId, "entra-object-123");

        var failure = assertThrows(
                BusinessConflictException.class,
                () -> userProvisioning.invite(
                        ORGANIZATION_ID,
                        EMAIL,
                        null,
                        Clearance.STANDARD,
                        ADMIN_USER_ID));

        assertEquals("invitation.user-scim-managed", failure.code());
    }

    @Test
    void anotherConnectionCannotAdoptTheSameApplicationUser() {
        register(connectionId, "entra-object-123");
        UUID secondConnectionId = ledger.createDisabledConnection(
                        ORGANIZATION_ID,
                        "replacement-workforce",
                        ProvisioningProviderProfile.OKTA,
                        true,
                        false)
                .id();

        assertThrows(
                ProvisioningConflictException.class,
                () -> register(secondConnectionId, "okta-object-456"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM scim_user_resources
                        WHERE organization_id = ? AND app_user_id = ?
                        """,
                        Integer.class,
                        ORGANIZATION_ID,
                        EXISTING_USER_ID));
    }

    private ProvisioningLedgerService.UserResourceRegistration register(
            UUID targetConnectionId, String externalId) {
        return ledger.registerUserResource(
                new ProvisioningLedgerService.UserResourceCommand(
                        ORGANIZATION_ID,
                        targetConnectionId,
                        externalId,
                        EMAIL,
                        EMAIL,
                        externalId,
                        "Directory Employee",
                        "Directory",
                        "Employee",
                        true));
    }

    private void insertUser(
            UUID id, String name, String email, Clearance clearance) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance,
                    created_at, updated_at, version, active)
                VALUES (?, ?, ?, ?, ?, now(), now(), 0, true)
                """,
                id,
                ORGANIZATION_ID,
                name,
                email,
                clearance.name());
    }
}
