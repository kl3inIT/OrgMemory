package com.orgmemory.api.scim;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import com.orgmemory.core.identityprovisioning.ProvisioningProviderProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScimMachineSecurityIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("a1000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_ORGANIZATION_ID =
            UUID.fromString("a2000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ADMIN_USER_ID =
            UUID.fromString("a2000000-0000-4000-8000-000000000002");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProvisioningLedgerService ledger;

    @Autowired
    ScimCredentialAdministrationService credentials;

    @Autowired
    ScimTokenCodec tokenCodec;

    private UUID connectionId;

    @BeforeEach
    void seedTenantAndDisabledConnection() {
        jdbc.update(
                "DELETE FROM provisioning_credentials WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM provisioning_connections WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM app_users WHERE organization_id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                "DELETE FROM organizations WHERE id IN (?, ?)",
                ORGANIZATION_ID,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'SCIM security tenant', now(), now(), 0)
                """,
                ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, role,
                    created_at, updated_at, version, active)
                VALUES (?, ?, 'SCIM administrator', 'scim-admin@example.test',
                    'ADMIN', now(), now(), 0, true)
                """,
                ADMIN_USER_ID,
                ORGANIZATION_ID);
        connectionId = ledger.createDisabledConnection(
                        ORGANIZATION_ID,
                        "workforce",
                        ProvisioningProviderProfile.GENERIC_SCIM,
                        true,
                        false)
                .id();
    }

    @Test
    void authenticatedDiscoveryIsTruthfulAndUsesScimMediaType() throws Exception {
        String token = issueUsersToken().token();

        mvc.perform(get("/scim/v2/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("X-Request-ID", "test-request-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(ScimErrorWriter.MEDIA_TYPE))
                .andExpect(header().string("X-Request-ID", "test-request-1"))
                .andExpect(jsonPath("$.documentationUri")
                        .value("https://docs.kl3in.tech/docs/admins/identity-permissions"))
                .andExpect(jsonPath("$.patch.supported").value(false))
                .andExpect(jsonPath("$.filter.supported").value(false))
                .andExpect(jsonPath("$.bulk.supported").value(false));
        Instant firstUsedAt = jdbc.queryForObject(
                """
                SELECT last_used_at
                FROM provisioning_credentials
                WHERE public_token_id = ?
                """,
                Instant.class,
                tokenCodec.parse(token).publicId());
        mvc.perform(get("/scim/v2/ResourceTypes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Resources", empty()))
                .andExpect(jsonPath("$.totalResults").value(0));
        mvc.perform(get("/scim/v2/Schemas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Resources", hasSize(0)));

        assertEquals(firstUsedAt, jdbc.queryForObject(
                """
                SELECT last_used_at
                FROM provisioning_credentials
                WHERE public_token_id = ?
                """,
                Instant.class,
                tokenCodec.parse(token).publicId()));
    }

    @Test
    void missingMalformedOidcAndBrowserSessionCredentialsCannotEnterScimChain()
            throws Exception {
        mvc.perform(get("/scim/v2/ServiceProviderConfig"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Invalid or missing credential")));
        mvc.perform(get("/scim/v2/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/scim/v2/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer header.payload.signature"))
                .andExpect(status().isUnauthorized());

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "browser-user", null, java.util.List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        mvc.perform(get("/scim/v2/ServiceProviderConfig").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedRequestIsRejectedBeforeBearerAuthentication() throws Exception {
        mvc.perform(post("/scim/v2/Users")
                        .content(new byte[256 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(ScimErrorWriter.MEDIA_TYPE))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void scimCredentialCannotAuthenticateProductApi() throws Exception {
        mvc.perform(get("/api/health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(issueUsersToken().token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void discoveryDoesNotRevealWhichTenantIssuedTheCredential() throws Exception {
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, 'Other SCIM security tenant', now(), now(), 0)
                """,
                OTHER_ORGANIZATION_ID);
        jdbc.update(
                """
                INSERT INTO app_users (
                    id, organization_id, name, email, role,
                    created_at, updated_at, version, active)
                VALUES (?, ?, 'Other SCIM administrator', 'other-scim-admin@example.test',
                    'ADMIN', now(), now(), 0, true)
                """,
                OTHER_ADMIN_USER_ID,
                OTHER_ORGANIZATION_ID);
        UUID otherConnectionId = ledger.createDisabledConnection(
                        OTHER_ORGANIZATION_ID,
                        "other-workforce",
                        ProvisioningProviderProfile.OKTA,
                        true,
                        false)
                .id();
        String firstToken = issueUsersToken().token();
        String otherToken = credentials.issue(
                        OTHER_ORGANIZATION_ID,
                        otherConnectionId,
                        OTHER_ADMIN_USER_ID,
                        true,
                        false)
                .token();

        String firstDiscovery = discoveryBody(firstToken);
        String otherDiscovery = discoveryBody(otherToken);

        assertEquals(
                firstDiscovery,
                otherDiscovery,
                "Discovery must describe server capability without exposing tenant state");
    }

    @Test
    void scopeIsEnforcedBeforeAnUnimplementedResourceRoute() throws Exception {
        String groupsOnly = issueDirect(false, true).rawToken();
        mvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(groupsOnly)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Insufficient scope")));

        mvc.perform(get("/scim/v2/Users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(issueUsersToken().token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredRevokedAndEndedOverlapCredentialsAreGenericUnauthorized()
            throws Exception {
        var expired = issueUsersToken();
        jdbc.update(
                "UPDATE provisioning_credentials SET expires_at = now() - interval '1 second' WHERE id = ?",
                expired.credentialId());
        expectUnauthorized(expired.token());

        var revoked = issueUsersToken();
        ledger.revokeCredential(
                ORGANIZATION_ID, connectionId, revoked.credentialId(), ADMIN_USER_ID);
        expectUnauthorized(revoked.token());

        var overlapEnded = issueUsersToken();
        jdbc.update(
                "UPDATE provisioning_credentials SET overlap_ends_at = now() - interval '1 second' WHERE id = ?",
                overlapEnded.credentialId());
        expectUnauthorized(overlapEnded.token());
    }

    @Test
    void rotationAllowsBoundedOverlapThenImmediateRevocation() throws Exception {
        var old = issueUsersToken();
        var replacement = credentials.rotate(
                ORGANIZATION_ID,
                connectionId,
                old.credentialId(),
                ADMIN_USER_ID,
                true,
                false);

        expectDiscovery(old.token(), 200);
        expectDiscovery(replacement.token(), 200);

        jdbc.update(
                "UPDATE provisioning_credentials SET overlap_ends_at = now() - interval '1 second' WHERE id = ?",
                old.credentialId());
        expectUnauthorized(old.token());

        ledger.revokeCredential(
                ORGANIZATION_ID,
                connectionId,
                replacement.credentialId(),
                ADMIN_USER_ID);
        expectUnauthorized(replacement.token());

        ListRow stored = jdbc.queryForObject(
                """
                SELECT public_token_id, verifier_digest
                FROM provisioning_credentials
                WHERE id = ?
                """,
                (result, row) -> new ListRow(
                        result.getString("public_token_id"),
                        result.getString("verifier_digest")),
                replacement.credentialId());
        assertEquals(replacement.publicTokenId(), stored.publicId());
        assertFalse(replacement.token().contains(stored.verifierDigest()));
        assertFalse(stored.verifierDigest().contains(replacement.token()));
    }

    @Test
    void concurrentRotationCreatesOneReplacementAndRevocationWinsImmediately()
            throws Exception {
        var old = issueUsersToken();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var attempts = List.of(
                executor.submit(() -> rotateAfter(ready, start, old.credentialId())),
                executor.submit(() -> rotateAfter(ready, start, old.credentialId())));
        List<ScimCredentialAdministrationService.IssuedCredential> successful =
                new ArrayList<>();
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (var attempt : attempts) {
                try {
                    successful.add(attempt.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException expectedConflict) {
                    assertTrue(
                            expectedConflict.getCause() instanceof IllegalStateException,
                            "The losing rotation must observe that the credential already rotated");
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, successful.size());
        assertEquals(
                2,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM provisioning_credentials
                        WHERE organization_id = ? AND connection_id = ?
                        """,
                        Integer.class,
                        ORGANIZATION_ID,
                        connectionId));

        var replacement = successful.getFirst();
        ledger.revokeCredential(
                ORGANIZATION_ID,
                connectionId,
                replacement.credentialId(),
                ADMIN_USER_ID);
        expectUnauthorized(replacement.token());
    }

    private ScimCredentialAdministrationService.IssuedCredential rotateAfter(
            CountDownLatch ready, CountDownLatch start, UUID credentialId)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent rotation did not start");
        }
        return credentials.rotate(
                ORGANIZATION_ID,
                connectionId,
                credentialId,
                ADMIN_USER_ID,
                true,
                false);
    }

    private ScimCredentialAdministrationService.IssuedCredential issueUsersToken() {
        return credentials.issue(
                ORGANIZATION_ID, connectionId, ADMIN_USER_ID, true, false);
    }

    private ScimTokenCodec.IssuedToken issueDirect(
            boolean usersScope, boolean groupsScope) {
        var issued = tokenCodec.issue();
        ledger.storeCredentialVerifier(new ProvisioningLedgerService.CredentialVerifierCommand(
                ORGANIZATION_ID,
                connectionId,
                issued.publicId(),
                issued.verifierDigest(),
                issued.keyVersion(),
                usersScope,
                groupsScope,
                Instant.now().plusSeconds(3600),
                ADMIN_USER_ID));
        return issued;
    }

    private void expectUnauthorized(String token) throws Exception {
        expectDiscovery(token, 401);
    }

    private void expectDiscovery(String token, int expectedStatus) throws Exception {
        mvc.perform(get("/scim/v2/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(expectedStatus));
    }

    private String discoveryBody(String token) throws Exception {
        return mvc.perform(get("/scim/v2/ResourceTypes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record ListRow(String publicId, String verifierDigest) {
    }
}
