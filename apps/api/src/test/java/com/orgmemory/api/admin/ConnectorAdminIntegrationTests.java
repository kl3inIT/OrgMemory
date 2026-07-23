package com.orgmemory.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.knowledge.ConnectorCredentialProbeRegistry;
import com.orgmemory.core.knowledge.ConnectorCredentialProbeResult;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.shared.secret.SecretValue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/**
 * Proves the connection administration surface: only an organization administrator reaches it,
 * a token given to it is stored encrypted and never comes back, and the crawl settings an
 * administrator saves are the ones the worker will later read.
 *
 * <p>Slack itself is stubbed. What is being proved here is the boundary — who may configure a
 * connection and what leaves the building — and a real workspace would make that proof depend
 * on a network rather than on the code under test.
 */
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConnectorAdminIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final String MODEL_ID = "test-model";
    private static final String WORKSPACE = "T0SLACKTEST";
    private static final String BOT_TOKEN = "xoxb-not-a-real-token-0123456789";

    private static final UUID ORG = UUID.fromString("d1000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("d1000000-0000-4000-8000-000000000002");
    private static final UUID ADMIN_USER = UUID.fromString("d1000000-0000-4000-8000-000000000003");
    private static final UUID AN_USER = UUID.fromString("d1000000-0000-4000-8000-000000000004");
    private static final UUID SPACE = UUID.fromString("d1000000-0000-4000-8000-000000000005");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * The registry rather than Slack's probe. Which adapter answers for a source system is the
     * registry's decision and is proved in its own suite; what this suite is about is that the
     * controller hands the credential over and shapes what comes back, for any source.
     */
    @MockitoBean
    ConnectorCredentialProbeRegistry probes;

    @MockitoBean
    RelationshipAuthorizationPort entryAuthorization;

    @MockitoBean
    RelationshipAuthorizationSetPort setAuthorization;

    @BeforeEach
    void prepare() {
        Integer alreadySeeded = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Integer.class, ORG);
        if (alreadySeeded == null || alreadySeeded == 0) {
            seed();
        }
        jdbc.update("DELETE FROM source_connection_credentials");
        jdbc.update("DELETE FROM source_connections");
        when(entryAuthorization.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            boolean allowed = !"can_manage_members".equals(query.permission().value())
                    || ADMIN_USER.toString().equals(query.principal().id());
            return allowed
                    ? AuthorizationDecision.allow(MODEL_ID)
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID);
        });
        when(probes.probe(any(), any())).thenReturn(
                ConnectorCredentialProbeResult.usable(WORKSPACE, "Slack Test", "orgmemory"));
    }

    @Test
    void nonAdministratorsAreRefusedEverywhere() throws Exception {
        var employee = jwtFor(AN_USER);

        mvc.perform(get("/api/admin/connectors/slack").with(employee))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/connectors/slack/{key}", WORKSPACE)
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"crawlEnabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/connectors/slack/{key}/credential", WORKSPACE)
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/connectors/slack/{key}/credential", WORKSPACE).with(employee))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/connectors/slack/test")
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/connectors/slack/{key}/test", WORKSPACE).with(employee))
                .andExpect(status().isForbidden());

        assertEquals(
                0,
                (int) jdbc.queryForObject("SELECT count(*) FROM source_connections", Integer.class),
                "A refused request must not have created the connection it named");
    }

    @Test
    void reportsOnlyTheSourcesThisDeploymentCanActuallyIngest() throws Exception {
        mvc.perform(get("/api/admin/connectors/sources").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sourceSystem == 'slack')].displayName").value("Slack"));
    }

    @Test
    void refusesASourceNoAdapterInstalled() throws Exception {
        // The path is the source system, so an uninstalled one has to be refused rather than
        // treated as an empty list — otherwise a typo reads as "you have no connections".
        mvc.perform(get("/api/admin/connectors/teams").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/connectors/teams/{key}/credential", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(
                0,
                (int) jdbc.queryForObject("SELECT count(*) FROM source_connections", Integer.class),
                "a refused source must not have created a connection row");
    }

    /**
     * The question a configuration screen cannot answer. A connection reads as enabled, holds a
     * credential and points at a Space, and still indexes nothing because the token was revoked
     * — which shows up here as an attempt and nowhere else.
     */
    @Test
    void reportsWhyAConnectionThatLooksHealthyIsProducingNothing() throws Exception {
        jdbc.update("""
                INSERT INTO connector_crawl_attempts (
                    id, organization_id, source_system, source_connection_key, crawl_cursor,
                    outcome, objects_materialized, objects_rotated, objects_rematerialized,
                    objects_retired, objects_failed, error_code, error_message, attempted_at,
                    created_at, updated_at, version)
                VALUES (gen_random_uuid(), ?, 'slack', ?, NULL, 'UNAVAILABLE', 0, 0, 0, 0, 0,
                        'token_revoked', 'Slack refused auth.test: token_revoked',
                        now(), now(), now(), 0)
                """, ORG, WORKSPACE);

        mvc.perform(get("/api/admin/connectors/slack/{key}/activity", WORKSPACE).with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectsTotal").value(0))
                .andExpect(jsonPath("$.recentAttempts[0].outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.recentAttempts[0].errorCode").value("token_revoked"))
                .andExpect(jsonPath("$.lastCrawlAt").doesNotExist());
    }

    @Test
    void aCrawlIsConfiguredAndReadBack() throws Exception {
        mvc.perform(put("/api/admin/connectors/slack/{key}", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crawlEnabled":true,"knowledgeSpaceId":"%s","actorUserId":"%s",
                                 "sourceConfig":{"channels":["general","engineering"],
                                                 "maxThreadsPerChannel":50},
                                 "contentCrawlIntervalSeconds":900}
                                """.formatted(SPACE, ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crawlEnabled").value(true))
                .andExpect(jsonPath("$.sourceConfig.channels[1]").value("engineering"))
                .andExpect(jsonPath("$.contentCrawlIntervalSeconds").value(900))
                .andExpect(jsonPath("$.credentialSet").value(false));

        mvc.perform(get("/api/admin/connectors/slack").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceConnectionKey").value(WORKSPACE))
                .andExpect(jsonPath("$[0].sourceConfig.maxThreadsPerChannel").value(50))
                .andExpect(jsonPath("$[0].configuredByUserId").value(ADMIN_USER.toString()));
    }

    @Test
    void enablingACrawlWithNowhereToPublishIsRefused() throws Exception {
        mvc.perform(put("/api/admin/connectors/slack/{key}", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"crawlEnabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStoredTokenIsEncryptedAndNeverComesBack() throws Exception {
        mvc.perform(put("/api/admin/connectors/slack/{key}/credential", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        String stored = jdbc.queryForObject(
                "SELECT cipher_text FROM source_connection_credentials", String.class);
        assertNotNull(stored);
        assertFalse(stored.contains(BOT_TOKEN), "The token must not be readable in the row that holds it");

        String listed = mvc.perform(get("/api/admin/connectors/slack").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialSet").value(true))
                .andExpect(jsonPath("$[0].credentialSetByUserId").value(ADMIN_USER.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(listed.contains(BOT_TOKEN), "No administration response may carry the token back");

        String configured = mvc.perform(put("/api/admin/connectors/slack/{key}", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"crawlEnabled\":false}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(configured.contains(BOT_TOKEN), "Reconfiguring must not echo the credential either");

        mvc.perform(delete("/api/admin/connectors/slack/{key}/credential", WORKSPACE).with(jwtFor(ADMIN_USER)))
                .andExpect(status().isNoContent());
        assertEquals(
                0,
                (int) jdbc.queryForObject(
                        "SELECT count(*) FROM source_connection_credentials", Integer.class),
                "Forgetting a credential removes the ciphertext rather than blanking it");
    }

    @Test
    void testingATokenReportsTheWorkspaceItAuthenticatedAs() throws Exception {
        String response = mvc.perform(post("/api/admin/connectors/slack/test")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.connectionKey").value(WORKSPACE))
                .andExpect(jsonPath("$.canReadContent").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(response.contains(BOT_TOKEN), "A probe result must describe the token, not repeat it");

        ArgumentCaptor<SecretValue> submitted = ArgumentCaptor.forClass(SecretValue.class);
        verify(probes).probe(eq("slack"), submitted.capture());
        assertEquals(BOT_TOKEN, submitted.getValue().expose(), "Slack must be asked about the token that was sent");
    }

    @Test
    void testingAStoredTokenRoundTripsItThroughEncryption() throws Exception {
        mvc.perform(put("/api/admin/connectors/slack/{key}/credential", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/admin/connectors/slack/{key}/test", WORKSPACE).with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionKey").value(WORKSPACE));

        ArgumentCaptor<SecretValue> resolved = ArgumentCaptor.forClass(SecretValue.class);
        verify(probes).probe(eq("slack"), resolved.capture());
        assertEquals(
                BOT_TOKEN,
                resolved.getValue().expose(),
                "What comes out of the cipher must be what the administrator put in");
    }

    @Test
    void testingAConnectionWithNothingStoredSaysSoRatherThanFailing() throws Exception {
        mvc.perform(post("/api/admin/connectors/slack/{key}/test", WORKSPACE).with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.errorCode").value("no_credential"));

        verifyNoInteractions(probes);
    }

    @Test
    void everyMutationLeavesAnAuditEvent() throws Exception {
        mvc.perform(put("/api/admin/connectors/slack/{key}/credential", WORKSPACE)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + BOT_TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        List<String> reasons = jdbc.queryForList("""
                SELECT reason_code FROM permission_audit_events
                WHERE resource_type = 'SOURCE_CONNECTION' AND resource_id = ?
                """, String.class, "slack/" + WORKSPACE);
        assertTrue(reasons.contains("CREDENTIAL_SET"), "Setting a credential must be recorded: " + reasons);

        String recorded = String.join("|", jdbc.queryForList("""
                SELECT coalesce(reason_code, '') || ' ' || coalesce(resource_id, '')
                FROM permission_audit_events WHERE resource_type = 'SOURCE_CONNECTION'
                """, String.class));
        assertFalse(recorded.contains(BOT_TOKEN), "The audit trail records that a token was set, not the token");
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt()
                .jwt(token -> token
                        .claim("iss", ISSUER)
                        .claim("sub", userId.toString())
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void seed() {
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Connector Test Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Platform', now(), now(), 0)", DEPT, ORG);
        insertUser(ADMIN_USER, "admin@connectortest.example", "ADMIN");
        insertUser(AN_USER, "an@connectortest.example", "EMPLOYEE");
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'connector-test-space', 'Connector Test Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);
    }

    private void insertUser(UUID id, String email, String role) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, role, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                """, id, ORG, DEPT, email, email, role);
        jdbc.update("""
                INSERT INTO external_identities (id, app_user_id, issuer, subject, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """, UUID.randomUUID(), id, ISSUER, id.toString());
    }
}
