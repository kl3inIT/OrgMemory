package com.orgmemory.api.knowledge;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.knowledge.AclCaptureStatus;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.knowledge.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.NormalizeRawSourceCommand;
import com.orgmemory.core.knowledge.PromoteNormalizedRecordCommand;
import com.orgmemory.core.knowledge.RegisterRawSourceCommand;
import com.orgmemory.core.knowledge.RotateSourceAclCommand;
import com.orgmemory.core.knowledge.SourceAclEntryCommand;
import com.orgmemory.core.knowledge.SourcePrincipalType;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeRetrievalIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATIONS_DEPARTMENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID MINH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PUBLIC_ASSET_ID = UUID.fromString("24000000-0000-4000-8000-000000000001");
    private static final UUID PUBLIC_RAW_SOURCE_ID = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID SOURCE_DENIED_RAW_SOURCE_ID =
            UUID.fromString("21000000-0000-4000-8000-000000000005");
    private static final UUID SALES_ASSET_ID = UUID.fromString("24000000-0000-4000-8000-000000000002");
    private static final UUID MARKETING_ASSET_ID = UUID.fromString("24000000-0000-4000-8000-000000000003");
    private static final UUID RESTRICTED_ASSET_ID = UUID.fromString("24000000-0000-4000-8000-000000000004");
    private static final UUID SOURCE_DENIED_ASSET_ID = UUID.fromString("24000000-0000-4000-8000-000000000005");
    private static final UUID PUBLIC_SNAPSHOT_ID = UUID.fromString("22000000-0000-4000-8000-000000000001");
    private static final UUID SOURCE_DENIED_SNAPSHOT_ID =
            UUID.fromString("22000000-0000-4000-8000-000000000005");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AppUserRepository users;

    @Autowired
    ExternalIdentityRepository identities;

    @Autowired
    KnowledgeIngestionService ingestion;

    @MockitoBean
    RelationshipAuthorizationPort authorizationPort;

    @BeforeEach
    void allowTheOpenFgaSearchBoundaryForRelationalPolicyRegressionTests() {
        when(authorizationPort.check(any())).thenReturn(AuthorizationDecision.allow("test-model"));
    }

    @Test
    void listAndKeywordSearchReturnOnlySqlAuthorizedAssets() throws Exception {
        mvc.perform(get("/api/knowledge-assets")
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        PUBLIC_ASSET_ID.toString(), SALES_ASSET_ID.toString())));

        mvc.perform(get("/api/knowledge-assets")
                        .param("q", "restructuring scenarios")
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/knowledge-assets")
                        .param("q", "source-denied")
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void detailAllowsVisibleContentAndUsesGenericNotFoundForEveryDenial() throws Exception {
        mvc.perform(get("/api/knowledge-assets/{assetId}", SALES_ASSET_ID)
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SALES_ASSET_ID.toString()))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString(
                        "confidential discovery questions")));

        mvc.perform(get("/api/knowledge-assets/{assetId}", MARKETING_ASSET_ID)
                        .with(linhJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
        mvc.perform(get("/api/knowledge-assets/{assetId}", UUID.randomUUID())
                        .with(linhJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
        mvc.perform(get("/api/knowledge-assets/{assetId}", SOURCE_DENIED_ASSET_ID)
                        .with(linhJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));

        var deniedAudit = jdbc.queryForMap(
                """
                SELECT decision, reason_code
                FROM permission_audit_events
                WHERE actor_user_id = ? AND resource_id = ?
                ORDER BY occurred_at DESC
                LIMIT 1
                """,
                LINH_ID,
                MARKETING_ASSET_ID.toString());
        assertEquals("DENY", deniedAudit.get("decision"));
        assertEquals("SOURCE_PERMISSION_UNKNOWN", deniedAudit.get("reason_code"));
    }

    @Test
    void controlPlaneAdminIsNotExecutiveButBusinessExecutiveCanReadRestricted() throws Exception {
        bind("david-knowledge-subject", users.findByEmailIgnoreCase("david@example.com").orElseThrow());
        mvc.perform(get("/api/knowledge-assets/{assetId}", RESTRICTED_ASSET_ID)
                        .with(jwtFor("david-knowledge-subject", "david@example.com", "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Knowledge access profile is incomplete"));

        String executiveEmail = "executive-%s@example.com".formatted(UUID.randomUUID());
        AppUser executive = users.save(new AppUser(
                ORGANIZATION_ID,
                OPERATIONS_DEPARTMENT_ID,
                "Business Executive",
                executiveEmail,
                UserRole.EXECUTIVE));
        String executiveSubject = "business-executive-%s".formatted(UUID.randomUUID());
        bind(executiveSubject, executive);
        RequestPostProcessor executiveJwt = jwtFor(executiveSubject, executiveEmail, "ROLE_VIEWER");

        mvc.perform(get("/api/knowledge-assets/{assetId}", RESTRICTED_ASSET_ID)
                        .with(executiveJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("RESTRICTED"));
        mvc.perform(get("/api/knowledge-assets")
                        .with(executiveJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        PUBLIC_ASSET_ID.toString(),
                        RESTRICTED_ASSET_ID.toString(),
                        SOURCE_DENIED_ASSET_ID.toString())));
        mvc.perform(get("/api/knowledge-assets")
                        .param("q", "confidential discovery questions")
                        .with(executiveJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void nullDepartmentExecutiveCannotUseRoleToBypassConfidentialDepartmentRequirement() throws Exception {
        String executiveEmail = "departmentless-executive-%s@example.com".formatted(UUID.randomUUID());
        AppUser executive = users.save(new AppUser(
                ORGANIZATION_ID,
                null,
                "Departmentless Executive",
                executiveEmail,
                UserRole.EXECUTIVE));
        String executiveSubject = "departmentless-executive-%s".formatted(UUID.randomUUID());
        bind(executiveSubject, executive);
        RequestPostProcessor executiveJwt = jwtFor(executiveSubject, executiveEmail, "ROLE_VIEWER");

        mvc.perform(get("/api/knowledge-assets")
                        .param("q", "confidential discovery questions")
                        .with(executiveJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/knowledge-assets/{assetId}", SALES_ASSET_ID)
                        .with(executiveJwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
    }

    @Test
    void aclHeadRotationRevokesAssetBeforeKeywordAndContentRetrieval() throws Exception {
        var revoked = ingestion.rotateSourceAcl(new RotateSourceAclCommand(
                ORGANIZATION_ID,
                PUBLIC_RAW_SOURCE_ID,
                AclCaptureStatus.UNKNOWN,
                AccessGate.UNKNOWN,
                null,
                java.util.List.of(),
                PUBLIC_SNAPSHOT_ID));
        try {
            mvc.perform(get("/api/knowledge-assets")
                            .param("q", "employee handbook")
                            .with(linhJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/knowledge-assets/{assetId}", PUBLIC_ASSET_ID)
                            .with(linhJwt()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
        } finally {
            ingestion.rotateSourceAcl(new RotateSourceAclCommand(
                    ORGANIZATION_ID,
                    PUBLIC_RAW_SOURCE_ID,
                    AclCaptureStatus.COMPLETE,
                    AccessGate.UNKNOWN,
                    Instant.now().plus(Duration.ofHours(1)),
                    java.util.List.of(new SourceAclEntryCommand(
                            SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                            ORGANIZATION_ID.toString(),
                            AccessGate.ALLOW)),
                    revoked.sourceAclSnapshotId()));
        }

        mvc.perform(get("/api/knowledge-assets/{assetId}", PUBLIC_ASSET_ID)
                        .with(linhJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void widerCurrentAclCannotOverrideTheIngestionSnapshotDeny() throws Exception {
        ingestion.rotateSourceAcl(new RotateSourceAclCommand(
                ORGANIZATION_ID,
                SOURCE_DENIED_RAW_SOURCE_ID,
                AclCaptureStatus.COMPLETE,
                AccessGate.UNKNOWN,
                Instant.now().plus(Duration.ofHours(1)),
                java.util.List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                        ORGANIZATION_ID.toString(),
                        AccessGate.ALLOW)),
                SOURCE_DENIED_SNAPSHOT_ID));

        mvc.perform(get("/api/knowledge-assets")
                        .param("q", "source-denied")
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/knowledge-assets/{assetId}", SOURCE_DENIED_ASSET_ID)
                        .with(linhJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
    }

    @Test
    void refreshedHeadKeepsAssetAvailableAfterHistoricalSnapshotExpiresWithoutWideningIt() throws Exception {
        String externalObjectId = "historical-acl-%s".formatted(UUID.randomUUID());
        Instant historicalExpiry = Instant.now().plusSeconds(5);
        var raw = ingestion.registerRawSource(new RegisterRawSourceCommand(
                ORGANIZATION_ID,
                OPERATIONS_DEPARTMENT_ID,
                "TEST",
                "historical-acl",
                externalObjectId,
                "v1",
                "DOCUMENT",
                "Historical ACL ceiling",
                "The ingestion snapshot remains a permission ceiling after its refresh TTL.",
                "https://source.example.test/" + externalObjectId,
                Instant.now(),
                KnowledgeClassification.PUBLIC,
                DeclaredAccessScope.ALL,
                AclCaptureStatus.COMPLETE,
                AccessGate.DENY,
                historicalExpiry,
                java.util.List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_USER,
                        LINH_ID.toString(),
                        AccessGate.ALLOW))));
        var normalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                raw.rawSourceObjectId(),
                "historical-acl-v1",
                "Historical ACL ceiling",
                "The ingestion snapshot remains a permission ceiling after its refresh TTL.",
                "en"));
        var asset = ingestion.promote(new PromoteNormalizedRecordCommand(
                ORGANIZATION_ID,
                normalized.normalizedRecordId(),
                AccessGate.ALLOW));
        jdbc.update(
                "UPDATE knowledge_assets SET status = 'ACTIVE', activated_at = now() WHERE id = ?",
                asset.knowledgeAssetId());
        ingestion.rotateSourceAcl(new RotateSourceAclCommand(
                ORGANIZATION_ID,
                raw.rawSourceObjectId(),
                AclCaptureStatus.COMPLETE,
                AccessGate.UNKNOWN,
                Instant.now().plus(Duration.ofHours(1)),
                java.util.List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                        ORGANIZATION_ID.toString(),
                        AccessGate.ALLOW)),
                raw.sourceAclSnapshotId()));

        try {
            long waitMillis = Math.max(
                    0,
                    Duration.between(Instant.now(), historicalExpiry.plusMillis(250)).toMillis());
            Thread.sleep(waitMillis);

            mvc.perform(get("/api/knowledge-assets/{assetId}", asset.knowledgeAssetId())
                            .with(linhJwt()))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/knowledge-assets/{assetId}", asset.knowledgeAssetId())
                            .with(jwtFor(MINH_ID.toString(), "minh@example.com", "ROLE_VIEWER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Knowledge asset not found"));
        } finally {
            ingestion.retire(ORGANIZATION_ID, asset.knowledgeAssetId());
        }
    }

    @Test
    void searchAuditsQueryAndEveryReturnedSourceWithoutRawQuery() throws Exception {
        MvcResult result = mvc.perform(get("/api/knowledge-assets")
                        .param("q", "handbook")
                        .with(linhJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(PUBLIC_ASSET_ID.toString()))
                .andReturn();
        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(requestId);

        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM permission_audit_events WHERE request_id = ?",
                        Long.class,
                        requestId));
        var auditRow = jdbc.queryForMap(
                """
                SELECT query_fingerprint, ingestion_acl_snapshot_id, current_acl_snapshot_id
                FROM permission_audit_events
                WHERE request_id = ? AND resource_type = 'KNOWLEDGE_SEARCH'
                """,
                requestId);
        String fingerprint = (String) auditRow.get("query_fingerprint");
        assertNotNull(fingerprint);
        assertEquals(64, fingerprint.length());

        var sourceAudit = jdbc.queryForMap(
                """
                SELECT ingestion_acl_snapshot_id, current_acl_snapshot_id
                FROM permission_audit_events
                WHERE request_id = ? AND resource_type = 'KNOWLEDGE_ASSET'
                """,
                requestId);
        assertEquals(PUBLIC_SNAPSHOT_ID, sourceAudit.get("ingestion_acl_snapshot_id"));
        assertNotNull(sourceAudit.get("current_acl_snapshot_id"));
    }

    private static RequestPostProcessor linhJwt() {
        return jwtFor(LINH_ID.toString(), "linh@example.com", "ROLE_VIEWER");
    }

    private void bind(String subject, AppUser user) {
        identities.save(new ExternalIdentity(user.getId(), ISSUER, subject));
    }

    private static RequestPostProcessor jwtFor(String subject, String email, String authority) {
        return jwt()
                .jwt(jwt -> jwt
                        .claim("iss", ISSUER)
                        .claim("sub", subject)
                        .claim("email", email)
                        .claim("email_verified", true))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
