package com.orgmemory.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationQuery;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.ExpansionNode;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipExpansionPort;
import com.orgmemory.core.authorization.RelationshipExpansionResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Proves the administration surface against a Slack-shaped ledger: only an organization
 * administrator reaches it, and confirming an observed identity makes the document that
 * identity was already granted retrievable on the very next query — with revocation
 * closing it again. The sealed ACL is the only thing deciding; OpenFGA is stubbed open so
 * a pass cannot come from the policy layer.
 */
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PermissionsAdminIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final String MODEL_ID = "test-model";
    private static final String SHA = "0".repeat(64);
    private static final String PROCESSING_PROFILE = "fixture.processing=v1\n";
    private static final String PROCESSING_PROFILE_SHA256 =
            "6746a26ed847d45b0a4735cb0b76698d6669bd008a14a472c5dea14daeb2ea37";

    private static final UUID ORG = UUID.fromString("c1000000-0000-4000-8000-000000000001");
    private static final UUID DEPT = UUID.fromString("c1000000-0000-4000-8000-000000000002");
    private static final UUID SECOND_DEPT = UUID.fromString("c1000000-0000-4000-8000-000000000006");
    private static final UUID ADMIN_USER = UUID.fromString("c1000000-0000-4000-8000-000000000003");
    private static final UUID AN_USER = UUID.fromString("c1000000-0000-4000-8000-000000000004");
    private static final UUID MEMBER_ADMIN = UUID.fromString("c1000000-0000-4000-8000-000000000005");

    private static final UUID PROFILE = UUID.fromString("c1000000-0000-4000-8000-00000000000a");
    private static final UUID BLOB = UUID.fromString("c1000000-0000-4000-8000-00000000000b");
    private static final UUID SPACE = UUID.fromString("c1000000-0000-4000-8000-00000000000c");
    private static final UUID RAW = UUID.fromString("c1000000-0000-4000-8000-00000000000d");
    private static final UUID SNAPSHOT = UUID.fromString("c1000000-0000-4000-8000-00000000000e");
    private static final UUID NORMALIZED = UUID.fromString("c1000000-0000-4000-8000-00000000000f");
    private static final UUID ASSET = UUID.fromString("c1000000-0000-4000-8000-000000000010");
    private static final UUID ASSET_VERSION = UUID.fromString("c1000000-0000-4000-8000-000000000015");
    private static final UUID OBJECT = UUID.fromString("c1000000-0000-4000-8000-000000000011");
    private static final UUID REVISION = UUID.fromString("c1000000-0000-4000-8000-000000000012");
    private static final UUID CHUNK = UUID.fromString("c1000000-0000-4000-8000-000000000013");
    private static final UUID PUBLICATION = UUID.fromString("c1000000-0000-4000-8000-000000000014");

    private static final UUID CHANNEL_PRINCIPAL = UUID.fromString("c1000000-0000-4000-8000-000000000021");
    private static final UUID AN_PRINCIPAL = UUID.fromString("c1000000-0000-4000-8000-000000000022");

    // A second tenant whose administrator is a full administrator of their own
    // organization, which is exactly what makes them a useful negative case here.
    private static final UUID OTHER_ORG = UUID.fromString("c2000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_DEPT = UUID.fromString("c2000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_ADMIN = UUID.fromString("c2000000-0000-4000-8000-000000000003");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CanonicalHybridKnowledgeSearch retrieval;

    @MockitoBean
    RelationshipAuthorizationPort entryAuthorization;

    @MockitoBean
    RelationshipAuthorizationSetPort setAuthorization;

    @MockitoBean
    RelationshipExpansionPort relationshipExpansion;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @BeforeEach
    void prepare() {
        Integer alreadySeeded = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Integer.class, ORG);
        if (alreadySeeded == null || alreadySeeded == 0) {
            seedLedger();
        }
        jdbc.update(
                "UPDATE app_users SET department_id = ?, clearance = 'STANDARD' WHERE organization_id = ?",
                DEPT,
                ORG);
        jdbc.update("DELETE FROM source_principal_mappings");
        jdbc.update("DELETE FROM source_connections");
        jdbc.update("DELETE FROM ai_route_overrides");
        jdbc.update("DELETE FROM ai_gateway_credentials");
        jdbc.update("DELETE FROM ai_gateway_profiles");
        stubPorts();
    }

    @Test
    void aiControlPlaneActorReferencesCannotCrossTenantBoundaries() {
        UUID gatewayProfileId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_gateway_profiles (
                    id, organization_id, gateway_key, display_name, preset,
                        category, protocol, supports_openai_reasoning_effort,
                        base_url, request_timeout_seconds, enabled,
                        runtime_revision, created_by_user_id,
                    updated_by_user_id, created_at, updated_at, version)
                VALUES (?, ?, 'tenant-safe', 'Tenant safe', 'OPENAI',
                    'DIRECT_PROVIDER', 'OPENAI_COMPATIBLE', false,
                    'https://api.openai.com/v1', 60, true, 1, ?, ?,
                    now(), now(), 0)
                """,
                gatewayProfileId,
                ORG,
                ADMIN_USER,
                ADMIN_USER);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO ai_gateway_credentials (
                            id, organization_id, gateway_profile_id,
                            cipher_text, key_version, set_by_user_id, set_at,
                            created_at, updated_at, version)
                        VALUES (?, ?, ?, 'ciphertext', 1, ?, now(),
                            now(), now(), 0)
                        """,
                        UUID.randomUUID(),
                        ORG,
                        gatewayProfileId,
                        OTHER_ADMIN));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO ai_route_overrides (
                            id, organization_id, workload, gateway_profile_id,
                            model_id, set_by_user_id, set_at, created_at,
                            updated_at, version)
                        VALUES (?, ?, 'ASSISTANT_CHAT', ?, 'model', ?, now(),
                            now(), now(), 0)
                        """,
                        UUID.randomUUID(),
                        ORG,
                        gatewayProfileId,
                        OTHER_ADMIN));

        jdbc.update(
                "DELETE FROM ai_gateway_profiles WHERE id = ?",
                gatewayProfileId);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO ai_gateway_profiles (
                            id, organization_id, gateway_key, display_name,
                            preset, category, protocol, base_url,
                            request_timeout_seconds, enabled, runtime_revision,
                            created_by_user_id, updated_by_user_id, created_at,
                            updated_at, version)
                        VALUES (?, ?, 'foreign-actor', 'Foreign actor',
                            'OPENAI', 'DIRECT_PROVIDER', 'OPENAI_COMPATIBLE',
                            'https://api.openai.com/v1', 60, true, 1, ?, ?,
                            now(), now(), 0)
                        """,
                        UUID.randomUUID(),
                        ORG,
                        OTHER_ADMIN,
                        OTHER_ADMIN));
    }

    @Test
    void nonAdministratorsAreRefusedEverywhere() throws Exception {
        var employee = jwtFor(AN_USER);

        mvc.perform(get("/api/admin/users").with(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/source-principals").with(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/source-connections").with(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/source-groups").with(employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ai/providers").with(employee)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}", ADMIN_USER)
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL)
                        .with(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appUserId\":\"" + AN_USER + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL).with(employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void effectiveContentAccessSeparatesRelationshipGrantFromCanonicalDenial() throws Exception {
        mvc.perform(post("/api/admin/access/explain")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "permission": "can_view",
                                  "resourceType": "knowledge_asset",
                                  "resourceId": "%s"
                                }
                                """.formatted(AN_USER, ASSET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationKind").value("CANONICAL_CONTENT"))
                .andExpect(jsonPath("$.state").value("DENIED"))
                .andExpect(jsonPath("$.reasonCode").value("CONTENT_POLICY_DENIED"))
                .andExpect(jsonPath("$.relationshipState").value("ALLOWED"))
                .andExpect(jsonPath("$.contentPolicyState").value("DENIED"))
                .andExpect(jsonPath("$.contentPolicyReasonCode")
                        .value("CANONICAL_RETRIEVAL_POLICY_DENIED"))
                .andExpect(jsonPath("$.path[0].objectType").value("knowledge_asset"))
                .andExpect(jsonPath("$.path[0].objectLabel").value("General channel digest"))
                .andExpect(jsonPath("$.path[1].objectType").value("knowledge_space"))
                .andExpect(jsonPath("$.path[1].objectLabel").value("Admin Test Space"))
                .andExpect(jsonPath("$.path[2].objectType").value("organizational_unit"))
                .andExpect(jsonPath("$.path[2].objectLabel").value("Operations"))
                .andExpect(jsonPath("$.resource.label").value("General channel digest"))
                .andExpect(jsonPath("$.resource.contextLabel").value("Admin Test Space"))
                .andExpect(jsonPath("$.resource.classification").value("INTERNAL"));
    }

    @Test
    void memberAdministrationDoesNotRevealAccessInspectionMetadata() throws Exception {
        mvc.perform(get("/api/admin/users").with(jwtFor(MEMBER_ADMIN)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/access/explain")
                        .with(jwtFor(MEMBER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "permission": "can_view",
                                  "resourceType": "knowledge_asset",
                                  "resourceId": "%s"
                                }
                                """.formatted(AN_USER, ASSET)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorOfAnotherOrganizationReachesNothingHere() throws Exception {
        var foreign = jwtFor(OTHER_ADMIN);

        // Reads are scoped to the actor's own organization, so this tenant's ledger is
        // simply absent rather than merely hidden behind a filter in the response.
        mvc.perform(get("/api/admin/users").with(foreign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + AN_USER + "')]").isEmpty());
        mvc.perform(get("/api/admin/source-principals").with(foreign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/admin/source-groups").with(foreign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Writes name a resource explicitly, so they must be refused rather than scoped.
        mvc.perform(patch("/api/admin/users/{id}", AN_USER)
                        .with(foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL)
                        .with(foreign)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appUserId\":\"" + AN_USER + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL).with(foreign))
                .andExpect(status().isNotFound());

        assertTrue(
                jdbc.queryForObject(
                        "SELECT active FROM app_users WHERE id = ?", Boolean.class, AN_USER),
                "A foreign administrator must not have been able to deactivate this tenant's user");
    }

    @Test
    void confirmingWithoutATargetUserIsARequestError() throws Exception {
        mvc.perform(put("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usersReportWhetherTheyCanSignInAtAll() throws Exception {
        mvc.perform(get("/api/admin/users").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + AN_USER + "')].signInLinked").value(true))
                .andExpect(jsonPath("$[?(@.id == '" + AN_USER + "')].clearance").value("STANDARD"));
    }

    @Test
    void anAdministratorCanRaiseClearanceWithoutChangingDepartment() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", AN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearance\":\"EXECUTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearance").value("EXECUTIVE"))
                .andExpect(jsonPath("$.departmentId").value(DEPT.toString()));

        assertEquals(
                "EXECUTIVE",
                jdbc.queryForObject(
                        "SELECT clearance FROM app_users WHERE id = ?",
                        String.class,
                        AN_USER));
    }

    @Test
    void anAdministratorCanAssignAndExplicitlyClearADepartment() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", AN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + SECOND_DEPT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(SECOND_DEPT.toString()));

        mvc.perform(patch("/api/admin/users/{id}", AN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":null}"))
                .andExpect(status().isOk());

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM app_users WHERE id = ? AND department_id IS NULL",
                        Integer.class,
                        AN_USER));
    }

    @Test
    void aCrossOrganizationDepartmentIsRejected() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", AN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + OTHER_DEPT + "\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(
                DEPT,
                jdbc.queryForObject(
                        "SELECT department_id FROM app_users WHERE id = ?",
                        UUID.class,
                        AN_USER));
    }

    @Test
    void meExposesTheCurrentDepartmentAndClearance() throws Exception {
        mvc.perform(get("/api/me").with(jwtFor(AN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(AN_USER.toString()))
                .andExpect(jsonPath("$.departmentId").value(DEPT.toString()))
                .andExpect(jsonPath("$.departmentName").value("Operations"))
                .andExpect(jsonPath("$.clearance").value("STANDARD"))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void organizationAdministratorsCanReadOnlyImplementedAiProviderPresets() throws Exception {
        mvc.perform(get("/api/admin/ai/providers").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.preset == 'OPENAI')].protocol")
                        .value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$[?(@.preset == 'ANTHROPIC')].protocol")
                        .value("ANTHROPIC_MESSAGES"))
                .andExpect(jsonPath("$[?(@.preset == 'NINE_ROUTER')].category")
                        .value("GATEWAY_ROUTER"));
    }

    @Test
    void confirmingAnIdentityOpensRetrievalAndRevokingClosesIt() throws Exception {
        assertFalse(sees(AN_USER), "An is unmapped, so the sealed channel grant resolves to nobody");

        mvc.perform(put("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appUserId\":\"" + AN_USER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapping.method").value("ADMIN_CONFIRMED"))
                .andExpect(jsonPath("$.mapping.appUserId").value(AN_USER.toString()));

        assertTrue(sees(AN_USER), "Confirming the identity must resolve the existing grant immediately");

        mvc.perform(delete("/api/admin/source-principals/{id}/mapping", AN_PRINCIPAL).with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapping").doesNotExist());

        assertFalse(sees(AN_USER), "Revoking must close access again");
        assertEquals(
                "REVOKED",
                jdbc.queryForObject(
                        "SELECT status FROM source_principal_mappings WHERE source_principal_id = ?",
                        String.class,
                        AN_PRINCIPAL),
                "Revocation closes the link without deleting the audit trail");
    }

    @Test
    void anAdministratorCannotChangeTheirOwnAccount() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", ADMIN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/admin/users/{id}", ADMIN_USER)
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + SECOND_DEPT + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identityTrustIsRecordedForTheWholeConnection() throws Exception {
        mvc.perform(put("/api/admin/source-connections/identity-trust")
                        .with(jwtFor(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystem":"slack","sourceConnectionKey":"T-workspace",
                                 "identityTrust":"SSO_VERIFIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityTrust").value("SSO_VERIFIED"))
                .andExpect(jsonPath("$.trustDecidedByUserId").value(ADMIN_USER.toString()));

        mvc.perform(get("/api/admin/source-connections").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceConnectionKey").value("T-workspace"))
                .andExpect(jsonPath("$[0].identityTrust").value("SSO_VERIFIED"))
                .andExpect(jsonPath("$[0].unmappedUserCount").value(1));
    }

    @Test
    void sourceGroupsReportTheirSealedMembership() throws Exception {
        mvc.perform(get("/api/admin/source-groups").with(jwtFor(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nativePrincipalId").value("C-channel"))
                .andExpect(jsonPath("$[0].membershipGeneration").value(1))
                .andExpect(jsonPath("$[0].members[0].nativePrincipalId").value("U-an"))
                .andExpect(jsonPath("$[0].members[0].appUserId").doesNotExist());
    }

    private boolean sees(UUID userId) {
        CurrentActor actor = new CurrentActor(userId, ORG, DEPT, "User", "an@admintest.example");
        return !retrieval.search(actor, "onboarding runbook", 10, "req-" + userId).evidence().isEmpty();
    }

    private void stubPorts() {
        // Administration permissions are scoped to the administrator. Retrieval permissions
        // remain open so the sealed source ACL is the only gate in the retrieval assertions.
        when(entryAuthorization.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            // The foreign administrator passes the gate too, so cross-tenant refusal has to
            // come from the ledger scoping rather than from the permission check.
            String permission = query.permission().value();
            boolean memberManagement = "can_manage_members".equals(permission);
            boolean protectedAdministration = "can_manage_sources".equals(permission)
                    || "can_manage_ai".equals(permission)
                    || "can_view_audit".equals(permission);
            boolean fullAdministrator = ADMIN_USER.toString().equals(query.principal().id())
                    || OTHER_ADMIN.toString().equals(query.principal().id());
            boolean allowed = (!memberManagement && !protectedAdministration)
                    || fullAdministrator
                    || (memberManagement && MEMBER_ADMIN.toString().equals(query.principal().id()));
            return allowed
                    ? AuthorizationDecision.allow(MODEL_ID)
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID);
        });
        when(setAuthorization.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(ORG, "knowledge_asset", ASSET)), MODEL_ID));
        when(setAuthorization.batchCheck(any())).thenAnswer(invocation -> {
            BatchAuthorizationQuery query = invocation.getArgument(0);
            return BatchAuthorizationResult.resolved(
                    query.resources().stream().collect(Collectors.toMap(
                            resource -> resource, resource -> AuthorizationDecision.allow(MODEL_ID))),
                    MODEL_ID);
        });
        when(relationshipExpansion.expand(any())).thenAnswer(invocation -> {
            var query = invocation.getArgument(
                    0, com.orgmemory.core.authorization.RelationshipExpansionQuery.class);
            String object = query.resource().openFgaObject();
            if (object.equals("knowledge_asset:" + ASSET)) {
                return RelationshipExpansionResult.resolved(
                        new ExpansionNode.TupleToUserset(
                                object + "#can_view",
                                "knowledge_space",
                                List.of("knowledge_space:" + SPACE + "#viewer")),
                        MODEL_ID);
            }
            if (object.equals("knowledge_space:" + SPACE)) {
                return RelationshipExpansionResult.resolved(
                        new ExpansionNode.Direct(
                                object + "#viewer",
                                List.of("organizational_unit:" + DEPT + "#member")),
                        MODEL_ID);
            }
            if (object.equals("organizational_unit:" + DEPT)) {
                return RelationshipExpansionResult.resolved(
                        new ExpansionNode.Direct(
                                object + "#member",
                                List.of("user:" + AN_USER)),
                        MODEL_ID);
            }
            return RelationshipExpansionResult.indeterminate("NOT_IN_FIXTURE", MODEL_ID);
        });
        // Lexical-only retrieval keeps the proof independent of embedding dimensions.
        when(queryEmbeddings.embed(any(), any())).thenReturn(Optional.empty());
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt()
                .jwt(token -> token
                        .claim("iss", ISSUER)
                        .claim("sub", userId.toString())
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private void seedLedger() {
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Admin Test Org', now(), now(), 0)", ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Operations', now(), now(), 0)", DEPT, ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Finance', now(), now(), 0)", SECOND_DEPT, ORG);
        insertUser(ADMIN_USER, ORG, DEPT, "admin@admintest.example", "STANDARD");
        insertUser(AN_USER, ORG, DEPT, "an@admintest.example", "STANDARD");
        insertUser(MEMBER_ADMIN, ORG, DEPT, "member-admin@admintest.example", "STANDARD");
        linkIdentity(ADMIN_USER);
        linkIdentity(AN_USER);
        linkIdentity(MEMBER_ADMIN);

        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Other Tenant', now(), now(), 0)", OTHER_ORG);
        jdbc.update("INSERT INTO departments (id, organization_id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, 'Other Operations', now(), now(), 0)", OTHER_DEPT, OTHER_ORG);
        insertUser(OTHER_ADMIN, OTHER_ORG, OTHER_DEPT, "admin@othertenant.example", "STANDARD");
        linkIdentity(OTHER_ADMIN);

        jdbc.update("""
                INSERT INTO embedding_profiles (
                    id, organization_id, profile_key, provider, model, dimensions, distance_metric, created_at)
                VALUES (?, ?, 'admintest/profile/3', 'test', 'test-embed', 3, 'COSINE', now())
                """, PROFILE, ORG);
        jdbc.update("""
                INSERT INTO evidence_blobs (
                    id, organization_id, object_key, media_type, content_length, content_sha256,
                    scan_status, created_at, updated_at, version)
                VALUES (?, ?, 'blobs/admintest-1', 'text/plain', 42, ?, 'BASIC_VALIDATED', now(), now(), 0)
                """, BLOB, ORG, SHA);
        jdbc.update("""
                INSERT INTO knowledge_spaces (
                    id, organization_id, department_id, audience_mode, audience_version,
                    space_key, name, active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'DEPARTMENT', 1,
                    'admintest-space', 'Admin Test Space', true, now(), now(), 0)
                """, SPACE, ORG, DEPT);

        jdbc.update("""
                INSERT INTO raw_source_objects (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    source_version, object_type, title, raw_content, payload_sha256, classification,
                    declared_access, status, created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-general-msg', 'v1', 'message', 'General channel digest',
                    ?, ?, 'INTERNAL', 'ALL_EMPLOYEES', 'NORMALIZED', now(), now(), 0)
                """, RAW, ORG, BODY, SHA);

        jdbc.update("""
                INSERT INTO source_acl_snapshots (
                    id, organization_id, raw_source_object_id, acl_generation, capture_status,
                    default_gate, acl_sha256, captured_at, valid_until)
                VALUES (?, ?, ?, 1, 'COMPLETE', 'DENY', ?, now(), now() + interval '23 hours')
                """, SNAPSHOT, ORG, RAW, SHA);

        insertPrincipal(CHANNEL_PRINCIPAL, "C-channel", "SOURCE_GROUP", "General");
        insertPrincipal(AN_PRINCIPAL, "U-an", "SOURCE_USER", "An Nguyen");

        jdbc.update("""
                INSERT INTO source_acl_entries (
                    id, organization_id, source_acl_snapshot_id, principal_type, principal_key, gate, created_at)
                VALUES (?, ?, ?, 'SOURCE_GROUP', ?, 'ALLOW', now())
                """, UUID.randomUUID(), ORG, SNAPSHOT, CHANNEL_PRINCIPAL.toString());
        insertMembership();
        jdbc.update("""
                INSERT INTO source_acl_snapshot_seals (
                    source_acl_snapshot_id, organization_id, entry_count, entries_sha256, sealed_at)
                VALUES (?, ?, 1, ?, now())
                """, SNAPSHOT, ORG, SHA);
        jdbc.update("""
                INSERT INTO source_acl_heads (
                    id, organization_id, source_system, source_connection_key, external_object_id,
                    current_raw_source_object_id, current_snapshot_id, acl_generation,
                    created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', 'C-general-msg', ?, ?, 1, now(), now(), 0)
                """, UUID.randomUUID(), ORG, RAW, SNAPSHOT);

        jdbc.update("""
                INSERT INTO normalized_records (
                    id, organization_id, raw_source_object_id, source_acl_snapshot_id, normalizer_version,
                    title, normalized_content, language, classification, declared_access, content_sha256,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'norm-v1', 'General channel digest', ?, 'en',
                    'INTERNAL', 'ALL_EMPLOYEES', ?, 'PROMOTED', now(), now(), 0)
                """, NORMALIZED, ORG, RAW, SNAPSHOT, BODY, SHA);
        jdbc.update("""
                INSERT INTO source_objects (
                    id, organization_id, created_by_user_id, knowledge_space_id, acl_authority, source_system,
                    source_connection_key, external_object_id, title, classification, declared_access,
                    status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'SOURCE', 'slack', 'T-workspace', 'C-general-msg', 'General channel digest',
                    'INTERNAL', 'ALL_EMPLOYEES', 'ACTIVE', now(), now(), 0)
                """, OBJECT, ORG, ADMIN_USER, SPACE);
        jdbc.update("""
                INSERT INTO knowledge_assets (
                    id, organization_id, knowledge_space_id, source_object_id, current_version_id,
                    archived_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, NULL, NULL, now(), now(), 0)
                """, ASSET, ORG, SPACE, OBJECT);
        jdbc.update("""
                INSERT INTO source_revisions (
                    id, organization_id, source_object_id, knowledge_space_id, evidence_blob_id,
                    revision_number, file_name, media_type, content_length, content_sha256, classification,
                    declared_access, created_by_user_id, status, pipeline_version, parser_version,
                    chunker_version, embedding_profile_id, embedding_dimensions, raw_source_object_id,
                    normalized_record_id, knowledge_asset_id, processing_profile,
                    processing_profile_sha256, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 1, 'digest.txt', 'text/plain', 42, ?, 'INTERNAL', 'ALL_EMPLOYEES',
                    ?, 'READY', 'pipe-v1', 'parse-v1', 'chunk-v1', ?, 3, ?, ?, ?, ?, ?, now(), now(), now(), 0)
                """, REVISION, ORG, OBJECT, SPACE, BLOB, SHA, ADMIN_USER, PROFILE, RAW, NORMALIZED, ASSET,
                PROCESSING_PROFILE, PROCESSING_PROFILE_SHA256);
        jdbc.update("""
                INSERT INTO knowledge_asset_versions (
                    id, organization_id, raw_source_object_id, normalized_record_id,
                    source_acl_snapshot_id, knowledge_space_id, title, content, language,
                    classification, declared_access, content_sha256, orgmemory_gate, status,
                    activated_at, created_at, updated_at, version, knowledge_asset_id,
                    version_number, source_revision_id)
                VALUES (?, ?, ?, ?, ?, ?, 'General channel digest', ?, 'en', 'INTERNAL',
                    'ALL_EMPLOYEES', ?, 'ALLOW', 'ACTIVE', now(), now(), now(), 0, ?, 1, ?)
                """,
                ASSET_VERSION,
                ORG,
                RAW,
                NORMALIZED,
                SNAPSHOT,
                SPACE,
                BODY,
                SHA,
                ASSET,
                REVISION);
        jdbc.update(
                "UPDATE source_revisions SET knowledge_asset_version_id = ? WHERE id = ?",
                ASSET_VERSION,
                REVISION);
        jdbc.update(
                """
                UPDATE source_objects
                SET current_revision_id = ?, latest_revision_id = ?, updated_at = now()
                WHERE id = ?
                """,
                REVISION,
                REVISION,
                OBJECT);
        jdbc.update(
                "UPDATE knowledge_assets SET current_version_id = ?, updated_at = now() WHERE id = ?",
                ASSET_VERSION,
                ASSET);
        jdbc.update("""
                INSERT INTO knowledge_chunks (
                    id, organization_id, source_object_id, source_revision_id, knowledge_asset_id,
                    knowledge_asset_version_id,
                    chunk_index, content, content_sha256, embedding, embedding_profile_id,
                    embedding_dimensions, pipeline_version, projection_generation, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, '[0.1,0.2,0.3]'::vector, ?, 3,
                    'pipe-v1', 1, true, now())
                """, CHUNK, ORG, OBJECT, REVISION, ASSET, ASSET_VERSION, BODY, SHA, PROFILE);
        jdbc.update("""
                INSERT INTO knowledge_asset_publication_outbox (
                    id, organization_id, source_revision_id, source_object_id, knowledge_asset_id,
                    knowledge_asset_version_id,
                    knowledge_space_id, owner_user_id, projection_generation, embedding_profile_id,
                    embedding_dimensions, pipeline_version, status, attempt_count, authorization_model_id,
                    applied_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 3, 'pipe-v1', 'APPLIED', 1, ?,
                    now(), now(), now(), 0)
                """,
                PUBLICATION,
                ORG,
                REVISION,
                OBJECT,
                ASSET,
                ASSET_VERSION,
                SPACE,
                ADMIN_USER,
                PROFILE,
                MODEL_ID);
    }

    private static final String BODY =
            "The quarterly onboarding runbook lives in the general channel and covers laptop setup, "
                    + "VPN access, and the first-week checklist.";

    private void insertUser(UUID id, UUID organizationId, UUID departmentId, String email, String clearance) {
        jdbc.update("""
                INSERT INTO app_users (
                    id, organization_id, department_id, name, email, clearance, active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, true, now(), now(), 0)
                """, id, organizationId, departmentId, email, email, clearance);
    }

    private void linkIdentity(UUID userId) {
        jdbc.update("""
                INSERT INTO external_identities (id, app_user_id, issuer, subject, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """, UUID.randomUUID(), userId, ISSUER, userId.toString());
    }

    private void insertPrincipal(UUID id, String nativePrincipalId, String kind, String displayName) {
        jdbc.update("""
                INSERT INTO source_principals (
                    id, organization_id, source_system, source_connection_key, native_principal_id, kind,
                    observed_display_name, sso_verified, last_seen_at, created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', ?, ?, ?, false, now(), now(), now(), 0)
                """, id, ORG, nativePrincipalId, kind, displayName);
    }

    private void insertMembership() {
        UUID run = UUID.randomUUID();
        UUID membershipSnapshot = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO source_membership_sync_runs (
                    id, organization_id, source_system, source_connection_key, captured_at,
                    created_at, updated_at, version)
                VALUES (?, ?, 'slack', 'T-workspace', now(), now(), now(), 0)
                """, run, ORG);
        jdbc.update("""
                INSERT INTO source_group_membership_snapshots (
                    id, organization_id, sync_run_id, group_principal_id,
                    membership_generation, capture_status, captured_at,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, 'COMPLETE', now(), now(), now(), 0)
                """, membershipSnapshot, ORG, run, CHANNEL_PRINCIPAL);
        jdbc.update("""
                INSERT INTO source_group_membership_members (
                    id, organization_id, membership_snapshot_id,
                    member_principal_id, member_principal_kind, created_at)
                VALUES (?, ?, ?, ?, 'SOURCE_USER', now())
                """, UUID.randomUUID(), ORG, membershipSnapshot, AN_PRINCIPAL);
        jdbc.update("""
                INSERT INTO source_group_membership_snapshot_seals (
                    membership_snapshot_id, organization_id, group_principal_id,
                    membership_generation, member_count, members_sha256, sealed_at)
                VALUES (?, ?, ?, 1, 1, ?, now())
                """, membershipSnapshot, ORG, CHANNEL_PRINCIPAL, SHA);
        jdbc.update("""
                INSERT INTO source_group_membership_heads (
                    id, organization_id, group_principal_id, current_snapshot_id,
                    membership_generation, activated_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), now(), 0)
                """, UUID.randomUUID(), ORG, CHANNEL_PRINCIPAL, membershipSnapshot);
    }
}
