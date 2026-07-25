package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.AssetAuthorizationConvergenceService;
import com.orgmemory.core.assetregistry.AssetAvailability;
import com.orgmemory.core.assetregistry.AssetConflictException;
import com.orgmemory.core.assetregistry.AssetDraftInput;
import com.orgmemory.core.assetregistry.AssetNotFoundException;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetReviewDecisionType;
import com.orgmemory.core.assetregistry.AssetRole;
import com.orgmemory.core.assetregistry.AssetType;
import com.orgmemory.core.assetregistry.AssetUnavailableException;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Sql("/db/test-foundation.sql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssetRegistryIntegrationTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEPARTMENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AUTHOR_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID REVIEWER_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SPACE_ID =
            UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final String MODEL_ID = "asset-model-1";

    private static final CurrentActor AUTHOR = new CurrentActor(
            AUTHOR_ID,
            ORGANIZATION_ID,
            DEPARTMENT_ID,
            "Linh Nguyen",
            "linh@example.test");
    private static final CurrentActor REVIEWER = new CurrentActor(
            REVIEWER_ID,
            ORGANIZATION_ID,
            DEPARTMENT_ID,
            "Minh Tran",
            "minh@example.test");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssetRegistryService assets;

    @Autowired
    AssetAuthorizationConvergenceService convergence;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    RelationshipAuthorizationPort authorization;

    @MockitoBean
    RelationshipAuthorizationSetPort authorizationSets;

    @MockitoBean
    RelationshipTupleWritePort tupleWrites;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @BeforeEach
    void prepare() {
        clearAssetRegistry();
        when(authorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(List.of(), MODEL_ID));
        when(tupleWrites.write(any())).thenReturn(
                RelationshipTupleWriteResult.applied(MODEL_ID));
    }

    @Test
    void approvedRevisionPublishesExactBytesEvenWhenTheDraftChangesLater() {
        AssetView created = create("support-triage");
        grantWorkflowRoles(created.id());

        AssetView submitted = assets.submit(AUTHOR, created.id(), "Initial support flow");
        AssetView.Revision revision = submitted.revisions().getFirst();
        AssetView.Review review = submitted.reviews().getFirst();
        AssetView changedDraft = assets.updateDraft(
                AUTHOR,
                created.id(),
                submitted.draft().lockVersion(),
                input("{\"task\":\"triage\",\"priority\":\"urgent\"}"));
        assertNotEquals(revision.payload(), changedDraft.draft().payload());

        AssetView approved = assets.decide(
                REVIEWER,
                created.id(),
                review.id(),
                AssetReviewDecisionType.APPROVE,
                "Approved for L1 support");
        assertEquals("APPROVED", approved.reviews().getFirst().state().name());
        AssetView published = assets.publish(
                AUTHOR, created.id(), revision.id(), "1.0.0");

        AssetView.Release release = published.releases().getFirst();
        assertEquals(revision.digest(), release.digest());
        assertEquals(revision.payload(), release.payload());
        assertEquals(AssetAvailability.AVAILABLE, release.availability());
        assertEquals("ACTIVE", published.portfolioState().name());
        assertThrows(
                AssetConflictException.class,
                () -> assets.publish(AUTHOR, created.id(), revision.id(), "1.0.1"));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE asset_releases SET payload = ? WHERE id = ?",
                        "{\"task\":\"tampered\"}",
                        release.id()));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE asset_revisions SET digest = ? WHERE id = ?",
                        "b".repeat(64),
                        revision.id()));
    }

    @Test
    void staleDraftVersionAndSelfApprovalAreRejected() {
        AssetView created = create("stale-draft");
        long originalVersion = created.draft().lockVersion();
        assets.updateDraft(
                AUTHOR,
                created.id(),
                originalVersion,
                input("{\"task\":\"triage\",\"priority\":\"normal\"}"));

        assertThrows(
                AssetConflictException.class,
                () -> assets.updateDraft(
                        AUTHOR,
                        created.id(),
                        originalVersion,
                        input("{\"task\":\"triage\",\"priority\":\"late\"}")));

        AssetView submitted = assets.submit(AUTHOR, created.id(), "Review me");
        assertThrows(
                AssetConflictException.class,
                () -> assets.decide(
                        AUTHOR,
                        created.id(),
                        submitted.reviews().getFirst().id(),
                        AssetReviewDecisionType.APPROVE,
                        "Self approval must fail"));
    }

    @Test
    void failedAuthorizationProjectionRemainsRetryableAndInvisibleUntilConverged() {
        when(tupleWrites.write(any())).thenReturn(
                RelationshipTupleWriteResult.indeterminate("TEMPORARY", MODEL_ID));

        assertThrows(AssetUnavailableException.class, () -> create("retryable-projection"));
        UUID assetId = jdbc.queryForObject(
                "SELECT id FROM assets WHERE slug = 'retryable-projection'",
                UUID.class);
        assertFalse(jdbc.queryForObject(
                "SELECT authorization_ready FROM assets WHERE id = ?",
                Boolean.class,
                assetId));
        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM asset_authorization_outbox "
                        + "WHERE asset_id = ? AND status = 'PENDING'",
                Integer.class,
                assetId));
        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM asset_authorization_outbox "
                        + "WHERE asset_id = ? AND claim_token IS NULL "
                        + "AND next_attempt_at > created_at",
                Integer.class,
                assetId));

        when(tupleWrites.write(any())).thenReturn(
                RelationshipTupleWriteResult.applied(MODEL_ID));
        jdbc.update(
                "UPDATE asset_authorization_outbox SET next_attempt_at = now() "
                        + "WHERE asset_id = ?",
                assetId);
        var report = convergence.reconcile(50);

        assertEquals(1, report.applied());
        assertTrue(jdbc.queryForObject(
                "SELECT authorization_ready FROM assets WHERE id = ?",
                Boolean.class,
                assetId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM asset_authorization_outbox "
                        + "WHERE asset_id = ? AND status = 'PENDING'",
                Integer.class,
                assetId));
    }

    @Test
    void unauthorizedAndCrossTenantIdsAreOpaqueWhileListIntersectsCanonicalRows() {
        AssetView created = create("opaque-denial");
        when(authorization.check(any())).thenReturn(
                AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID));
        assertThrows(
                AssetNotFoundException.class,
                () -> assets.get(REVIEWER, created.id()));
        assertThrows(
                AssetNotFoundException.class,
                () -> assets.updateDraft(
                        REVIEWER,
                        created.id(),
                        created.draft().lockVersion(),
                        input("{\"task\":\"denied\"}")));
        assertThrows(
                AssetNotFoundException.class,
                () -> assets.decide(
                        REVIEWER,
                        created.id(),
                        UUID.randomUUID(),
                        AssetReviewDecisionType.APPROVE,
                        "Denied before review metadata is resolved"));
        assertThrows(
                AssetNotFoundException.class,
                () -> assets.publish(
                        REVIEWER,
                        created.id(),
                        UUID.randomUUID(),
                        "1.0.0"));

        CurrentActor otherTenant = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Other Tenant",
                "other@example.test");
        assertThrows(
                AssetNotFoundException.class,
                () -> assets.get(otherTenant, created.id()));

        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                ORGANIZATION_ID, "asset", created.id())),
                        MODEL_ID));
        assertEquals(List.of(created.id()), assets.list(AUTHOR).stream()
                .map(summary -> summary.id())
                .toList());

        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(
                                UUID.randomUUID(), "asset", created.id())),
                        MODEL_ID));
        assertThrows(AssetUnavailableException.class, () -> assets.list(AUTHOR));
    }

    @Test
    void deprecationThenWithdrawalIsAppendOnlyAndRetiresTheOnlyRelease() {
        AssetView created = create("withdrawal-flow");
        grantWorkflowRoles(created.id());
        AssetView submitted = assets.submit(AUTHOR, created.id(), "Initial release");
        AssetView.Revision revision = submitted.revisions().getFirst();
        AssetView.Review review = submitted.reviews().getFirst();
        assets.decide(
                REVIEWER,
                created.id(),
                review.id(),
                AssetReviewDecisionType.APPROVE,
                "Approved");
        AssetView published = assets.publish(
                AUTHOR, created.id(), revision.id(), "1.0.0");
        UUID releaseId = published.releases().getFirst().id();

        AssetView deprecated = assets.deprecate(
                AUTHOR, created.id(), releaseId, "Use the replacement when available");
        assertEquals(AssetAvailability.DEPRECATED, deprecated.releases().getFirst().availability());
        AssetView withdrawn = assets.withdraw(
                AUTHOR, created.id(), releaseId, "Unsafe guidance");

        assertEquals(AssetAvailability.WITHDRAWN, withdrawn.releases().getFirst().availability());
        assertEquals(3, withdrawn.releases().getFirst().availabilityHistory().size());
        assertEquals("RETIRED", withdrawn.portfolioState().name());
        assertThrows(
                AssetConflictException.class,
                () -> assets.deprecate(
                        AUTHOR, created.id(), releaseId, "Cannot reopen"));
    }

    @Test
    void releaseLabelCannotBeReusedForDifferentApprovedContent() {
        AssetView created = create("fixed-coordinate");
        grantWorkflowRoles(created.id());
        AssetView firstSubmission =
                assets.submit(AUTHOR, created.id(), "First content");
        approve(created.id(), firstSubmission);
        assets.publish(
                AUTHOR,
                created.id(),
                firstSubmission.revisions().getFirst().id(),
                "1.0.0");

        AssetView changed = assets.updateDraft(
                AUTHOR,
                created.id(),
                firstSubmission.draft().lockVersion(),
                input("{\"task\":\"triage\",\"priority\":\"changed\"}"));
        AssetView secondSubmission =
                assets.submit(AUTHOR, created.id(), "Changed content");
        approve(created.id(), secondSubmission);

        assertThrows(
                AssetConflictException.class,
                () -> assets.publish(
                        AUTHOR,
                        created.id(),
                        secondSubmission.revisions().getFirst().id(),
                        "1.0.0"));
        assertNotEquals(
                firstSubmission.revisions().getFirst().digest(),
                secondSubmission.revisions().getFirst().digest());
        assertEquals(
                "{\"priority\":\"changed\",\"task\":\"triage\"}",
                changed.draft().payload());
    }

    @Test
    void catalogSearchFiltersOnlyTheAuthorizedCanonicalIntersection() {
        AssetView prompt = create("triage-search");
        AssetView instruction = assets.create(
                AUTHOR,
                AssetType.WORK_INSTRUCTION,
                "support",
                "respond-search",
                SPACE_ID,
                new AssetDraftInput(
                        "Respond safely",
                        "Follow the escalation procedure",
                        "INTERNAL",
                        "1",
                        "{\"steps\":[]}"));
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(
                        List.of(
                                ResourceRef.of(ORGANIZATION_ID, "asset", prompt.id()),
                                ResourceRef.of(ORGANIZATION_ID, "asset", instruction.id())),
                        MODEL_ID));

        assertEquals(
                List.of(prompt.id()),
                assets.search(AUTHOR, "customer ticket", AssetType.PROMPT_TEMPLATE)
                        .stream()
                        .map(summary -> summary.id())
                        .toList());
        assertEquals(
                List.of(instruction.id()),
                assets.search(AUTHOR, "escalation", null).stream()
                        .map(summary -> summary.id())
                        .toList());
        assertTrue(assets.search(AUTHOR, "not authorized text", null).isEmpty());
    }

    @Test
    void payloadReferencesCannotPointAcrossTenantBoundaries() {
        AssetView created = create("tenant-bound-reference");
        AssetView submitted =
                assets.submit(AUTHOR, created.id(), "Create immutable owner");
        UUID revisionId = submitted.revisions().getFirst().id();
        UUID otherOrganizationId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organizations "
                        + "(id, name, created_at, updated_at, version) "
                        + "VALUES (?, ?, now(), now(), 0)",
                otherOrganizationId,
                "Other organization");

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "INSERT INTO asset_payload_references "
                                + "(id, organization_id, owner_kind, revision_id, "
                                + "reference_kind, reference_value, created_at, "
                                + "updated_at, version) "
                                + "VALUES (?, ?, 'REVISION', ?, 'INLINE', ?, "
                                + "now(), now(), 0)",
                        UUID.randomUUID(),
                        otherOrganizationId,
                        revisionId,
                        "inline://payload"));
    }

    private AssetView create(String slug) {
        return assets.create(
                AUTHOR,
                AssetType.PROMPT_TEMPLATE,
                "support",
                slug,
                SPACE_ID,
                input("{\"task\":\"triage\",\"priority\":\"high\"}"));
    }

    private void grantWorkflowRoles(UUID assetId) {
        assets.assignRole(
                AUTHOR,
                assetId,
                "user",
                REVIEWER_ID.toString(),
                AssetRole.REVIEWER);
        assets.assignRole(
                AUTHOR,
                assetId,
                "user",
                AUTHOR_ID.toString(),
                AssetRole.PUBLISHER);
    }

    private void approve(UUID assetId, AssetView submitted) {
        assets.decide(
                REVIEWER,
                assetId,
                submitted.reviews().getFirst().id(),
                AssetReviewDecisionType.APPROVE,
                "Approved");
    }

    private static AssetDraftInput input(String payload) {
        return new AssetDraftInput(
                "Triage customer ticket",
                "Classify and route an L1 support ticket",
                "INTERNAL",
                "1",
                payload);
    }

    private void clearAssetRegistry() {
        jdbc.execute("""
                TRUNCATE TABLE
                    asset_audit_events,
                    asset_payload_references,
                    asset_relations,
                    asset_release_availability_events,
                    asset_releases,
                    asset_review_decisions,
                    asset_review_cases,
                    asset_revisions,
                    asset_drafts,
                    asset_authorization_outbox,
                    asset_role_assignments,
                    assets
                CASCADE
                """);
    }
}
