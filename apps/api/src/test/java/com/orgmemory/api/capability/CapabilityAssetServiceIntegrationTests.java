package com.orgmemory.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.core.capability.ApprovalAction;
import com.orgmemory.core.capability.AssetApprovalEventRepository;
import com.orgmemory.core.capability.AssetStatus;
import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.AssetVersion;
import com.orgmemory.core.capability.AssetVisibility;
import com.orgmemory.core.capability.CapabilityAsset;
import com.orgmemory.core.capability.CapabilityAssetService;
import com.orgmemory.core.capability.CreateCapabilityAssetCommand;
import com.orgmemory.core.capability.RiskLevel;
import com.orgmemory.core.capability.UsageEventType;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CapabilityAssetServiceIntegrationTests {

    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SALES_DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BACKUP_OWNER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SALES_TEAM_ASSET_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID REVIEW_ASSET_ID = UUID.fromString("aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa");
    private static final CurrentActor CONTRIBUTOR = new CurrentActor(
            OWNER_ID,
            ORGANIZATION_ID,
            SALES_DEPARTMENT_ID,
            "Linh Nguyen",
            "linh@example.com");
    private static final CurrentActor REVIEWER = new CurrentActor(
            BACKUP_OWNER_ID,
            ORGANIZATION_ID,
            SALES_DEPARTMENT_ID,
            "Minh Tran",
            "minh@example.com");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    CapabilityAssetService assets;

    @Autowired
    MockMvc mvc;

    @Autowired
    ExternalIdentityRepository identities;

    @Autowired
    AssetApprovalEventRepository approvalEvents;

    @MockitoBean
    RelationshipAuthorizationPort authorizationPort;

    @BeforeEach
    void authorizeByRelationshipInsteadOfJwtRole() {
        reset(authorizationPort);
        when(authorizationPort.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            String user = query.principal().id();
            String permission = query.permission().value();
            boolean reviewer = BACKUP_OWNER_ID.toString().equals(user);
            boolean contributor = OWNER_ID.toString().equals(user);
            boolean allowed = switch (permission) {
                case "can_create_capability_asset", "can_view_capability_registry", "can_view",
                        "can_edit", "can_search_knowledge" -> contributor || reviewer;
                case "can_review" -> reviewer;
                default -> false;
            };
            return allowed
                    ? AuthorizationDecision.allow("test-model")
                    : AuthorizationDecision.deny("RELATIONSHIP_DENIED", "test-model");
        });
    }

    @Test
    void createAssetCreatesVersionAndCanTrackUsage() {
        CapabilityAsset asset = assets.create(CONTRIBUTOR, new CreateCapabilityAssetCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "Proposal outline generator",
                "Creates a first-pass B2B proposal outline from discovery notes.",
                AssetType.PROMPT_TEMPLATE,
                "Proposal drafting",
                "Sales operations",
                "Claude",
                "sales, proposal",
                OWNER_ID,
                BACKUP_OWNER_ID,
                OWNER_ID,
                AssetVisibility.TEAM,
                RiskLevel.MEDIUM,
                "Create a proposal outline from {{notes}}.",
                "[{\"name\":\"Paste discovery notes\"},{\"name\":\"Generate outline\"}]",
                "{\"notes\":\"string\"}",
                "{\"outline\":\"string\"}",
                "Discovery call notes",
                "Executive summary, needs, proposal sections"));

        assertEquals(AssetStatus.DRAFT, asset.getStatus());
        List<AssetVersion> versions = assets.versions(CONTRIBUTOR, asset.getId());
        assertEquals(1, versions.size());
        assertEquals(1, versions.getFirst().getVersionNumber());

        assertEquals(1, assets.recordUsage(CONTRIBUTOR, asset.getId(), UsageEventType.USED, "{}"));
        assertFalse(assets.search(CONTRIBUTOR, null, AssetType.PROMPT_TEMPLATE, "proposal").isEmpty());
    }

    @Test
    void reviewWorkflowMovesDraftToApproved() {
        CapabilityAsset asset = assets.create(CONTRIBUTOR, new CreateCapabilityAssetCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "Meeting summary asset",
                "Summarizes customer meeting notes into decisions and next steps.",
                AssetType.WORKFLOW_AUTOMATION,
                "Meeting follow-up",
                "Customer success operations",
                "ChatGPT",
                "meeting, summary",
                OWNER_ID,
                BACKUP_OWNER_ID,
                OWNER_ID,
                AssetVisibility.TEAM,
                RiskLevel.LOW,
                "Summarize {{transcript}} into decisions and next steps.",
                null,
                null,
                null,
                "Transcript",
                "Decisions and next steps"));

        assertEquals(AssetStatus.IN_REVIEW, assets.submitForReview(CONTRIBUTOR, asset.getId(), "Ready").getStatus());
        assertThrows(OrgMemoryAccessDeniedException.class,
                () -> assets.approve(CONTRIBUTOR, asset.getId(), "Self approval is forbidden"));
        assertEquals(AssetStatus.APPROVED, assets.approve(REVIEWER, asset.getId(), "Approved").getStatus());
    }

    @Test
    void createUsesLinkedActorInsteadOfClientSuppliedIdentity() throws Exception {
        mvc.perform(post("/api/assets")
                        .with(jwtFor(OWNER_ID.toString(), "spoofed@example.com", true, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "99999999-0000-4000-8000-000000000000",
                                  "departmentId": "%s",
                                  "title": "Secure sales follow-up",
                                  "summary": "Creates a governed follow-up from verified sales notes.",
                                  "assetType": "PROMPT_TEMPLATE",
                                  "ownerUserId": "%s",
                                  "createdByUserId": "%s",
                                  "visibility": "TEAM",
                                  "riskLevel": "LOW",
                                  "promptTemplate": "Draft a follow-up for {{account}}."
                                }
                                """.formatted(SALES_DEPARTMENT_ID, OWNER_ID, BACKUP_OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.createdByUserId").value(OWNER_ID.toString()));

        assertTrue(identities.findByIssuerAndSubject(ISSUER, OWNER_ID.toString()).isPresent());
    }

    @Test
    void contributorCannotApproveButReviewerCanAndReviewerIdIsDerived() throws Exception {
        mvc.perform(patch("/api/assets/{assetId}/approve", REVIEW_ASSET_ID)
                        .with(jwtFor(OWNER_ID.toString(), "linh@example.com", true, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerUserId\":\"%s\",\"comment\":\"approve\"}".formatted(BACKUP_OWNER_ID)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/assets/{assetId}/approve", REVIEW_ASSET_ID)
                        .with(jwtFor(BACKUP_OWNER_ID.toString(), "minh@example.com", true, "ROLE_UNRELATED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerUserId\":\"%s\",\"comment\":\"approved\"}".formatted(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        var event = approvalEvents.findByAssetIdOrderByCreatedAtDesc(REVIEW_ASSET_ID).getFirst();
        assertEquals(ApprovalAction.APPROVED, event.getAction());
        assertEquals(BACKUP_OWNER_ID, event.getReviewerUserId());
    }

    @Test
    void anUnlinkedJwtCannotCreateOrReadAssetsEvenWithAnAdminRole() throws Exception {
        mvc.perform(post("/api/assets")
                        .with(jwtFor("unlinked-priya", "priya@example.com", true, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Viewer draft",
                                  "summary": "This must not be created.",
                                  "assetType": "PROMPT_TEMPLATE",
                                  "visibility": "TEAM",
                                  "riskLevel": "LOW"
                                }
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/assets/{assetId}", SALES_TEAM_ASSET_ID)
                        .with(jwtFor("unlinked-priya", "priya@example.com", true, "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unverifiedEmailCannotBootstrapAnIdentityLink() throws Exception {
        mvc.perform(get("/api/me")
                        .with(jwtFor("unverified-priya", "priya@example.com", false, "ROLE_VIEWER")))
                .andExpect(status().isForbidden());

        assertTrue(identities.findByIssuerAndSubject(ISSUER, "unverified-priya").isEmpty());
    }

    @Test
    @Transactional
    void externalIdentityLinkIsIdempotent() {
        String subject = "idempotent-linh-subject";
        int firstInsert = identities.linkIfAbsent(
                UUID.randomUUID(), OWNER_ID, "https://idp.example.test", subject);
        int secondInsert = identities.linkIfAbsent(
                UUID.randomUUID(), OWNER_ID, "https://idp.example.test", subject);

        assertEquals(1, firstInsert);
        assertEquals(0, secondInsert);
        assertEquals(OWNER_ID,
                identities.findByIssuerAndSubject("https://idp.example.test", subject).orElseThrow().getAppUserId());
    }

    private static RequestPostProcessor jwtFor(String subject, String email, boolean emailVerified, String authority) {
        return jwt()
                .jwt(jwt -> jwt
                        .claim("iss", ISSUER)
                        .claim("sub", subject)
                        .claim("email", email)
                        .claim("email_verified", emailVerified))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
