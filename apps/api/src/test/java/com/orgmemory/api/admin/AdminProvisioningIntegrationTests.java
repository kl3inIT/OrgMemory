package com.orgmemory.api.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminProvisioningIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final String MODEL_ID = "test-model";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("a4000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("a4000000-0000-4000-8000-000000000002");
    private static final UUID EMPLOYEE_USER_ID =
            UUID.fromString("a4000000-0000-4000-8000-000000000003");
    private static final UUID OTHER_ORGANIZATION_ID =
            UUID.fromString("a5000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ADMIN_USER_ID =
            UUID.fromString("a5000000-0000-4000-8000-000000000002");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    RelationshipAuthorizationPort authorization;

    @BeforeEach
    void prepare() {
        jdbc.update(
                "DELETE FROM provisioning_credentials WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM provisioning_connections WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                """
                DELETE FROM external_identities
                WHERE app_user_id IN (?, ?, ?)
                """,
                ADMIN_USER_ID,
                EMPLOYEE_USER_ID,
                OTHER_ADMIN_USER_ID);
        jdbc.update(
                "DELETE FROM app_users WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM organizations WHERE id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);

        insertOrganization(ORGANIZATION_ID, "Provisioning tenant");
        insertOrganization(OTHER_ORGANIZATION_ID, "Other provisioning tenant");
        insertUser(ADMIN_USER_ID, ORGANIZATION_ID, "admin@provisioning.example", "STANDARD");
        insertUser(EMPLOYEE_USER_ID, ORGANIZATION_ID, "employee@provisioning.example", "STANDARD");
        insertUser(
                OTHER_ADMIN_USER_ID,
                OTHER_ORGANIZATION_ID,
                "admin@other-provisioning.example",
                "STANDARD");

        when(authorization.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            boolean allowed = ADMIN_USER_ID.toString().equals(query.principal().id())
                    || OTHER_ADMIN_USER_ID.toString().equals(query.principal().id());
            return allowed
                    ? AuthorizationDecision.allow(MODEL_ID)
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID);
        });
    }

    @Test
    void employeeCannotReadOrCreateProvisioningConnections() throws Exception {
        var employee = jwtFor(EMPLOYEE_USER_ID);

        mvc.perform(get("/api/admin/provisioning/connections").with(employee))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/provisioning/connections")
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"workforce","providerProfile":"MICROSOFT_ENTRA"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedGroupCredentialScopeIsARequestError() throws Exception {
        UUID connectionId = createConnection(ADMIN_USER_ID);

        mvc.perform(post(
                                "/api/admin/provisioning/connections/{connectionId}/credentials",
                                connectionId)
                        .with(jwtFor(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usersScope\":true,\"groupsScope\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void administratorManagesOneTimeCredentialLifecycleWithoutSecretDisclosure()
            throws Exception {
        UUID connectionId = createConnection(ADMIN_USER_ID);

        String issuedBody = mvc.perform(post(
                                "/api/admin/provisioning/connections/{connectionId}/credentials",
                                connectionId)
                        .with(jwtFor(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usersScope\":true,\"groupsScope\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode issued = JSON.readTree(issuedBody);
        UUID credentialId = UUID.fromString(issued.path("credentialId").asText());
        String token = issued.path("token").asText();
        String publicTokenId = issued.path("publicTokenId").asText();
        assertTrue(token.startsWith("omscim_"));

        String listed = mvc.perform(get(
                                "/api/admin/provisioning/connections/{connectionId}/credentials",
                                connectionId)
                        .with(jwtFor(ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicTokenId").value(publicTokenId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(listed.contains(token), "Credential metadata must not disclose the raw token");
        assertFalse(
                jdbc.queryForObject(
                        """
                        SELECT verifier_digest = ?
                        FROM provisioning_credentials WHERE id = ?
                        """,
                        Boolean.class,
                        token,
                        credentialId),
                "The stored verifier must not be the raw token");

        String rotatedBody = mvc.perform(post(
                                "/api/admin/provisioning/connections/{connectionId}"
                                        + "/credentials/{credentialId}/rotate",
                                connectionId,
                                credentialId)
                        .with(jwtFor(ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usersScope\":true,\"groupsScope\":false}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode rotated = JSON.readTree(rotatedBody);
        UUID rotatedCredentialId =
                UUID.fromString(rotated.path("credentialId").asText());
        assertNotEquals(token, rotated.path("token").asText());
        assertTrue(jdbc.queryForObject(
                """
                SELECT overlap_ends_at IS NOT NULL
                FROM provisioning_credentials WHERE id = ?
                """,
                Boolean.class,
                credentialId));

        mvc.perform(delete(
                                "/api/admin/provisioning/connections/{connectionId}"
                                        + "/credentials/{credentialId}",
                                connectionId,
                                rotatedCredentialId)
                        .with(jwtFor(ADMIN_USER_ID)))
                .andExpect(status().isNoContent());
        assertTrue(jdbc.queryForObject(
                """
                SELECT revoked_at IS NOT NULL
                FROM provisioning_credentials WHERE id = ?
                """,
                Boolean.class,
                rotatedCredentialId));
    }

    @Test
    void tenantComesFromCurrentActorNotTheConnectionPath() throws Exception {
        UUID connectionId = createConnection(ADMIN_USER_ID);

        mvc.perform(get("/api/admin/provisioning/connections")
                        .with(jwtFor(OTHER_ADMIN_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(post(
                                "/api/admin/provisioning/connections/{connectionId}/credentials",
                                connectionId)
                        .with(jwtFor(OTHER_ADMIN_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usersScope\":true,\"groupsScope\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("provisioning.resource-not-found"));
    }

    private UUID createConnection(UUID actorId) throws Exception {
        String response = mvc.perform(post("/api/admin/provisioning/connections")
                        .with(jwtFor(actorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alias":"workforce","providerProfile":"MICROSOFT_ENTRA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationalState").value("DISABLED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JSON.readTree(response).path("id").asText());
    }

    private void insertOrganization(UUID organizationId, String name) {
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                """,
                organizationId,
                name);
    }

    private void insertUser(
            UUID userId,
            UUID organizationId,
            String email,
            String role) {
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, clearance, active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, true, now(), now(), 0)
                """,
                userId,
                organizationId,
                email,
                email,
                role);
        jdbc.update(
                """
                INSERT INTO external_identities (
                    id, app_user_id, issuer, subject, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """,
                UUID.randomUUID(),
                userId,
                ISSUER,
                userId.toString());
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt()
                .jwt(token -> token
                        .claim("iss", ISSUER)
                        .claim("sub", userId.toString())
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
