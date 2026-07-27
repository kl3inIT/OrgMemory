package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assistant.AssistantAssetToolService;
import com.orgmemory.core.assetregistry.AssetAuthorizationConvergenceService;
import com.orgmemory.core.assetregistry.AssetAvailability;
import com.orgmemory.core.assetregistry.AssetDeliveryService;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.CapabilityPackDefinition;
import com.orgmemory.core.assetregistry.PackAssignmentStatus;
import com.orgmemory.core.assetregistry.PackJourney;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.PromptEvaluationResult;
import com.orgmemory.core.assetregistry.PromptRunResult;
import com.orgmemory.core.assetregistry.SkillPackageStoragePort;
import com.orgmemory.core.assetregistry.SkillRegistryService;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.assetregistry.WorkInstructionView;
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
import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.knowledge.KnowledgeCatalogItem;
import com.orgmemory.core.knowledge.KnowledgeCatalogService;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
    private static final UUID SUPPORT_AGENT_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID BACKUP_OWNER_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID SPACE_ID =
            UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final UUID GOLDEN_KNOWLEDGE_ASSET_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID GOLDEN_KNOWLEDGE_VERSION_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000007");
    private static final String MODEL_ID = "asset-model-1";
    private static final AiRoute PROMPT_ROUTE =
            new AiRoute("test-gateway", "test-model");

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
    private static final CurrentActor SUPPORT_AGENT = new CurrentActor(
            SUPPORT_AGENT_ID,
            ORGANIZATION_ID,
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "An Pham",
            "an@example.test");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    AssetRegistryService assets;

    @Autowired
    SkillRegistryService skills;

    @Autowired
    AssetDeliveryService delivery;

    @Autowired
    AssetAuthorizationConvergenceService convergence;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PromptExecutionService prompts;

    @Autowired
    WorkInstructionService instructions;

    @Autowired
    CapabilityPackService packs;

    @Autowired
    AssistantAssetToolService assistantTools;

    @MockitoBean
    RelationshipAuthorizationPort authorization;

    @MockitoBean
    RelationshipAuthorizationSetPort authorizationSets;

    @MockitoBean
    RelationshipTupleWritePort tupleWrites;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @MockitoBean
    PermissionAwareKnowledgeSearch knowledgeSearch;

    @MockitoBean
    KnowledgeCatalogService knowledgeCatalog;

    @MockitoBean
    ChatModelPort chat;

    @MockitoBean
    SkillPackageStoragePort skillStorage;

    @BeforeEach
    void prepare() {
        clearAssetRegistry();
        when(authorization.check(any())).thenReturn(AuthorizationDecision.allow(MODEL_ID));
        when(authorizationSets.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.resolved(List.of(), MODEL_ID));
        when(tupleWrites.write(any())).thenReturn(
                RelationshipTupleWriteResult.applied(MODEL_ID));
        when(chat.stream(
                        eq(AiWorkload.PROMPT_EXECUTION),
                        eq(PROMPT_ROUTE),
                        any(ChatGenerationRequest.class)))
                .thenReturn(Flux.just("{\"category\":\"access\"}"));
        when(skillStorage.put(any(), any())).thenAnswer(invocation -> {
            SkillPackageStoragePort.SkillPackageWriteRequest request =
                    invocation.getArgument(0);
            return new SkillPackageStoragePort.StoredSkillPackage(
                    "assets/skills/"
                            + request.organizationId()
                            + "/"
                            + request.packageId()
                            + ".zip",
                    request.contentLength(),
                    "application/zip",
                    request.expectedSha256());
        });
        when(knowledgeSearch.search(any(), any(), any(), any()))
                .thenReturn(new SecureKnowledgeSearchResult(
                        "asset-registry-empty-grounding", List.of()));
        when(knowledgeCatalog.findExactVisible(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PromptRouteTestConfiguration {

        @Bean
        @Primary
        AiRouteResolver promptTestRouteResolver() {
            return workload -> PROMPT_ROUTE;
        }
    }

    @Test
    void recommendationsAreActorScopedAndPinExactUsableReleases() {
        AssetView published = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "assistant-recommendation",
                promptPayloadWithEvaluation(),
                "1.0.0");
        AssetView.Release release = published.releases().getFirst();
        assets.updateDraft(
                AUTHOR,
                published.id(),
                published.draft().lockVersion(),
                new AssetDraftInput(
                        "Unreleased shadow metadata",
                        "unreleased-shadow-query",
                        "INTERNAL",
                        "1",
                        promptPayloadWithEvaluation()));
        when(authorizationSets.listAuthorizedResources(any()))
                .thenAnswer(invocation -> {
                    AuthorizedResourceQuery query = invocation.getArgument(0);
                    if (query.principal().equals(AUTHOR.principal())) {
                        return AuthorizedResourceSetResult.resolved(
                                List.of(ResourceRef.of(
                                        ORGANIZATION_ID,
                                        "asset",
                                        published.id())),
                                MODEL_ID);
                    }
                    return AuthorizedResourceSetResult.resolved(List.of(), MODEL_ID);
                });

        var authorResult = assistantTools.recommend(
                AUTHOR, "support", AssetType.PROMPT_TEMPLATE);
        var reviewerResult = assistantTools.recommend(
                REVIEWER, "support", AssetType.PROMPT_TEMPLATE);
        var unreleasedDraftResult = assistantTools.recommend(
                AUTHOR, "unreleased-shadow-query", AssetType.PROMPT_TEMPLATE);

        assertEquals(1, authorResult.recommendations().size());
        assertEquals(release.id(), authorResult.recommendations().getFirst().releaseId());
        assertEquals(release.digest(), authorResult.recommendations().getFirst().releaseDigest());
        assertEquals(
                "Asset assistant-recommendation",
                authorResult.recommendations().getFirst().title());
        assertTrue(reviewerResult.recommendations().isEmpty());
        assertTrue(unreleasedDraftResult.recommendations().isEmpty());
        assertEquals(
                3,
                jdbc.queryForObject(
                        "select count(*) from assistant_asset_traces",
                        Integer.class));
        String releaseRefs = jdbc.queryForObject(
                """
                select release_refs::text
                from assistant_asset_traces
                where id = ?
                """,
                String.class,
                authorResult.traceId());
        assertTrue(releaseRefs.contains(release.id().toString()));
        assertFalse(releaseRefs.contains("Asset assistant-recommendation"));
    }

    @Test
    void deliveryReturnsOnlyImmutableReleaseDataAndKeepsDeniedIdsOpaque() {
        AssetView published = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "mcp-delivery",
                promptPayloadWithoutVariables(),
                "1.0.0");
        AssetView.Release release = published.releases().getFirst();
        String draftPayload = promptPayloadWithoutVariables().replace(
                "Draft an approved response.",
                "Draft an unreleased replacement response.");
        assets.updateDraft(
                AUTHOR,
                published.id(),
                published.draft().lockVersion(),
                new AssetDraftInput(
                        "Unreleased delivery shadow",
                        "This must never reach MCP",
                        "CONFIDENTIAL",
                        "1",
                        draftPayload));

        var delivered = delivery.get(AUTHOR, published.id());

        assertEquals(published.id(), delivered.assetId());
        assertEquals(release.id(), delivered.releaseId());
        assertEquals(published.type(), delivered.type());
        assertEquals(published.namespace(), delivered.namespace());
        assertEquals(published.slug(), delivered.slug());
        assertEquals(release.versionLabel(), delivered.versionLabel());
        assertEquals(release.title(), delivered.title());
        assertEquals(release.summary(), delivered.summary());
        assertEquals(release.classification(), delivered.classification());
        assertEquals(release.schemaVersion(), delivered.schemaVersion());
        assertEquals(release.payload(), delivered.payload());
        assertEquals(release.digest(), delivered.digest());
        assertEquals(release.availability(), delivered.availability());
        assertEquals(
                release.releasedAt().toEpochMilli(),
                delivered.releasedAt().toEpochMilli());
        assertNotEquals(draftPayload, delivered.payload());
        when(authorization.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            return query.principal().equals(REVIEWER.principal())
                    ? AuthorizationDecision.deny("RELATIONSHIP_DENIED", MODEL_ID)
                    : AuthorizationDecision.allow(MODEL_ID);
        });
        assertThrows(
                AssetNotFoundException.class,
                () -> delivery.get(REVIEWER, published.id()));
        assertThrows(
                AssetNotFoundException.class,
                () -> delivery.getRelease(
                        REVIEWER, published.id(), release.id()));
        assertThrows(
                AssetNotFoundException.class,
                () -> prompts.render(
                        REVIEWER,
                        published.id(),
                        release.id(),
                Map.of()));
    }

    @Test
    void latestDeliveryFallsBackToTheNewestReleaseThatIsStillUsable() {
        AssetView first = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "mcp-delivery-fallback",
                promptPayloadWithoutVariables(),
                "1.0.0");
        AssetView.Release firstRelease = first.releases().getFirst();
        String replacementPayload = promptPayloadWithoutVariables().replace(
                "Draft an approved response.",
                "Draft a replacement response.");
        AssetView changed = assets.updateDraft(
                AUTHOR,
                first.id(),
                first.draft().lockVersion(),
                new AssetDraftInput(
                        "Replacement release",
                        "A later release that will be withdrawn",
                        "INTERNAL",
                        "1",
                        replacementPayload));
        AssetView submitted = assets.submit(
                AUTHOR, first.id(), "Publish replacement");
        approve(first.id(), submitted);
        AssetView second = assets.publish(
                AUTHOR,
                first.id(),
                submitted.revisions().getFirst().id(),
                "2.0.0");
        AssetView.Release secondRelease = second.releases().getFirst();
        assets.withdraw(
                AUTHOR,
                first.id(),
                secondRelease.id(),
                "Withdraw replacement");

        var delivered = delivery.get(AUTHOR, changed.id());

        assertEquals(firstRelease.id(), delivered.releaseId());
        assertEquals(firstRelease.digest(), delivered.digest());
        assertEquals(firstRelease.payload(), delivered.payload());
        assertEquals(AssetAvailability.AVAILABLE, delivered.availability());
    }

    @Test
    void promptRunPinsReleaseAndRouteWithoutPersistingSensitiveVariables() {
        AssetView published = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "prompt-run",
                promptPayloadWithEvaluation(),
                "1.0.0");
        AssetView.Release release = published.releases().getFirst();

        PromptRunResult result = prompts.run(
                AUTHOR,
                published.id(),
                release.id(),
                Map.of("ticket_text", "SECRET customer account detail"),
                null,
                "prompt-run-test");

        assertEquals(release.digest(), result.releaseDigest());
        assertEquals("test-model", result.modelRoute().modelId());
        Map<String, Object> stored = jdbc.queryForMap(
                """
                select release_digest, gateway_id, model_id,
                       input_shape_digest, citation_refs::text as citations,
                       sanitized_outcome::text as outcome
                from prompt_runs
                where id = ?
                """,
                result.runId());
        assertEquals(release.digest(), stored.get("release_digest"));
        assertEquals("test-gateway", stored.get("gateway_id"));
        assertEquals("test-model", stored.get("model_id"));
        assertFalse(stored.toString().contains("SECRET"));
        assertFalse(stored.toString().contains("customer account detail"));

        PromptEvaluationResult evaluation = prompts.evaluate(
                AUTHOR, published.id(), release.id());
        assertTrue(evaluation.passed());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from prompt_evaluation_runs where release_id = ?",
                        Integer.class,
                        release.id()));

        assets.withdraw(
                AUTHOR, published.id(), release.id(), "Superseded test release");
        assertThrows(
                AssetUnavailableException.class,
                () -> prompts.run(
                        AUTHOR,
                        published.id(),
                        release.id(),
                        Map.of("ticket_text", "New ticket"),
                        null,
                        "withdrawn-run"));
    }

    @Test
    void workInstructionAcknowledgementAndPackProgressAreIdempotentAndPinned() {
        AssetView prompt = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "pack-prompt",
                promptPayloadWithoutVariables(),
                "1.0.0");
        AssetView instruction = createApprovedRelease(
                AssetType.WORK_INSTRUCTION,
                "pack-instruction",
                workInstructionPayload(),
                "1.0.0");
        AssetView.Release promptRelease = prompt.releases().getFirst();
        AssetView.Release instructionRelease = instruction.releases().getFirst();

        WorkInstructionView firstAcknowledgement = instructions.acknowledge(
                AUTHOR, instruction.id(), instructionRelease.id());
        WorkInstructionView secondAcknowledgement = instructions.acknowledge(
                AUTHOR, instruction.id(), instructionRelease.id());
        assertTrue(firstAcknowledgement.acknowledged());
        assertEquals(
                firstAcknowledgement.acknowledgedAt(),
                secondAcknowledgement.acknowledgedAt());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from work_instruction_acknowledgements where release_id = ?",
                        Integer.class,
                        instructionRelease.id()));

        AssetView pack = createApprovedRelease(
                AssetType.CAPABILITY_PACK,
                "l1-onboarding",
                packPayload(
                        prompt.id(),
                        promptRelease.id(),
                        instruction.id(),
                        instructionRelease.id()),
                "1.0.0");
        AssetView.Release packRelease = pack.releases().getFirst();
        CapabilityPackDefinition definition =
                packs.describe(AUTHOR, pack.id(), packRelease.id());
        assertEquals(2, definition.items().size());
        assertFalse(definition.accessGap());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from pack_assignments where pack_release_id = ?",
                        Integer.class,
                        packRelease.id()));
        PackJourney first = packs.start(AUTHOR, pack.id(), packRelease.id());
        PackJourney resumed = packs.start(AUTHOR, pack.id(), packRelease.id());
        assertEquals(first.assignmentId(), resumed.assignmentId());
        assertFalse(first.accessGap());

        packs.setItemCompleted(
                AUTHOR, pack.id(), packRelease.id(), "prompt", true);
        PackJourney completed = packs.setItemCompleted(
                AUTHOR, pack.id(), packRelease.id(), "instruction", true);
        assertEquals(PackAssignmentStatus.COMPLETED, completed.status());
        assertEquals(2, completed.completedAccessibleItems());
        assertEquals(promptRelease.id(), completed.items().getFirst().pinnedVersionId());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "select count(*) from pack_progress where assignment_id = ?",
                        Integer.class,
                        completed.assignmentId()));

        assets.updateDraft(
                AUTHOR,
                prompt.id(),
                prompt.draft().lockVersion(),
                new AssetDraftInput(
                        "Asset pack-prompt",
                        "Replacement Prompt",
                        "INTERNAL",
                        "1",
                        promptPayloadWithoutVariables().replace(
                                "Draft an approved response.",
                                "Draft a revised approved response.")));
        AssetView replacementSubmission = assets.submit(
                AUTHOR, prompt.id(), "Replacement Prompt");
        approve(prompt.id(), replacementSubmission);
        AssetView replacement = assets.publish(
                AUTHOR,
                prompt.id(),
                replacementSubmission.revisions().getFirst().id(),
                "2.0.0");
        assertNotEquals(
                promptRelease.id(), replacement.releases().getFirst().id());
        PackJourney unchanged = packs.get(
                AUTHOR, pack.id(), packRelease.id());
        assertEquals(
                promptRelease.id(),
                unchanged.items().getFirst().pinnedVersionId());
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
                "UPDATE asset_authorization_outbox "
                        + "SET next_attempt_at = now() - interval '1 second' "
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
        assertTrue(changed.draft().payload().contains(
                "\\\"priority\\\":\\\"changed\\\""));
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
                        """
                        {
                          "purpose": "Respond to a support ticket safely",
                          "audience": "L1 support",
                          "prerequisites": ["Read the escalation policy"],
                          "completionOutcome": "The customer receives an approved response",
                          "responsibleRole": "L1 support agent",
                          "steps": [{
                            "key": "respond",
                            "title": "Draft the response",
                            "instruction": "Use only verified customer and policy facts.",
                            "expectedResult": "A clear draft response",
                            "check": "No unsupported promise is present",
                            "escalation": "Escalate policy exceptions",
                            "prohibitedActions": ["Disclose internal-only data"],
                            "relatedAssetIds": [],
                            "relatedKnowledgeVersionIds": []
                          }]
                        }
                        """));
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

    @Test
    void skillImportPinsTheValidatedBlobToRevisionAndRelease() throws Exception {
        byte[] archive = skillArchive(
                "support-triage",
                "Triage a support ticket using the approved company workflow.");
        AssetView created = skills.importPackage(
                AUTHOR,
                "support",
                SPACE_ID,
                KnowledgeClassification.INTERNAL,
                archive.length,
                new ByteArrayInputStream(archive));
        assertEquals(AssetType.SKILL, created.type());
        assertEquals("support-triage", created.slug());
        assertFalse(created.draft().payload().contains("assets/skills/"));
        grantWorkflowRoles(created.id());

        AssetView submitted =
                assets.submit(AUTHOR, created.id(), "Import support triage Skill");
        approve(created.id(), submitted);
        AssetView published = assets.publish(
                AUTHOR,
                created.id(),
                submitted.revisions().getFirst().id(),
                "1.0.0");

        List<Map<String, Object>> references = jdbc.queryForList(
                """
                SELECT owner_kind, reference_kind, reference_value, digest,
                       media_type, content_length
                FROM asset_payload_references
                WHERE organization_id = ?
                ORDER BY owner_kind DESC
                """,
                ORGANIZATION_ID);
        assertEquals(3, references.size());
        assertEquals("REVISION", references.getFirst().get("owner_kind"));
        assertEquals("RELEASE", references.get(1).get("owner_kind"));
        assertEquals("DRAFT", references.getLast().get("owner_kind"));
        assertEquals("BLOB", references.getFirst().get("reference_kind"));
        assertEquals(
                references.getFirst().get("reference_value"),
                references.get(1).get("reference_value"));
        assertEquals(
                references.getFirst().get("digest"),
                references.get(1).get("digest"));
        assertEquals(
                references.getFirst().get("digest"),
                references.getLast().get("digest"));
        assertEquals(
                references.getFirst().get("content_length"),
                references.getLast().get("content_length"));
        assertEquals("application/zip", references.getFirst().get("media_type"));
        assertEquals(1, published.releases().size());

        AssetConflictException failure = assertThrows(
                AssetConflictException.class,
                () -> assets.forkRelease(
                        AUTHOR,
                        created.id(),
                        published.releases().getFirst().id(),
                        "support",
                        "support-triage-copy",
                        SPACE_ID));
        assertEquals("Skill releases cannot be forked yet.", failure.getMessage());
    }

    @Test
    void goldenPocTransfersAReleasedSupportCapabilityToASecondUser()
            throws IOException {
        List<MockTicket> tickets = JSON.readValue(
                goldenFixture("mock-tickets.json"),
                new TypeReference<>() {
                });
        assertEquals(8, tickets.size());
        when(knowledgeCatalog.findExactVisible(
                        any(),
                        eq(GOLDEN_KNOWLEDGE_ASSET_ID),
                        eq(GOLDEN_KNOWLEDGE_VERSION_ID)))
                .thenReturn(Optional.of(new KnowledgeCatalogItem(
                        GOLDEN_KNOWLEDGE_ASSET_ID,
                        GOLDEN_KNOWLEDGE_VERSION_ID,
                        1,
                        SPACE_ID,
                        "Support SLA and escalation",
                        "en",
                        KnowledgeClassification.INTERNAL,
                        "b".repeat(64))));

        when(knowledgeSearch.search(any(), any(), any(), any()))
                .thenAnswer(invocation -> new SecureKnowledgeSearchResult(
                        invocation.getArgument(3) == null
                                ? "golden-grounding"
                                : invocation.getArgument(3),
                        List.of(goldenKnowledgeEvidence())));
        when(chat.stream(
                        eq(AiWorkload.PROMPT_EXECUTION),
                        eq(PROMPT_ROUTE),
                        any(ChatGenerationRequest.class)))
                .thenAnswer(invocation -> {
                    ChatGenerationRequest request = invocation.getArgument(2);
                    MockTicket ticket = tickets.stream()
                            .filter(candidate ->
                                    request.userPrompt().contains(candidate.id()))
                            .findFirst()
                            .orElseThrow();
                    return Flux.just(JSON.writeValueAsString(Map.of(
                            "category", ticket.category(),
                            "slaTier", ticket.slaTier(),
                            "escalate", ticket.escalate(),
                            "accountableTeam", ticket.accountableTeam(),
                            "response", "Use approved policy and cite support.sla-and-escalation@1")));
                });

        AssetView prompt = createApprovedRelease(
                AssetType.PROMPT_TEMPLATE,
                "triage-customer-ticket",
                goldenFixture("prompt-template.json"),
                "1.0.0");
        AssetView instruction = createApprovedRelease(
                AssetType.WORK_INSTRUCTION,
                "classify-and-respond",
                goldenFixture("work-instruction.json"),
                "1.0.0");
        AssetView.Release promptRelease = prompt.releases().getFirst();
        AssetView.Release instructionRelease =
                instruction.releases().getFirst();
        String packPayload = goldenFixture("capability-pack-template.json")
                .replace("${WORK_INSTRUCTION_ASSET_ID}", instruction.id().toString())
                .replace("${WORK_INSTRUCTION_RELEASE_ID}", instructionRelease.id().toString())
                .replace("${PROMPT_ASSET_ID}", prompt.id().toString())
                .replace("${PROMPT_RELEASE_ID}", promptRelease.id().toString())
                .replace(
                        "${KNOWLEDGE_ASSET_ID}",
                        GOLDEN_KNOWLEDGE_ASSET_ID.toString())
                .replace(
                        "${KNOWLEDGE_VERSION_ID}",
                        GOLDEN_KNOWLEDGE_VERSION_ID.toString());
        AssetView pack = createApprovedRelease(
                AssetType.CAPABILITY_PACK,
                "l1-onboarding",
                packPayload,
                "1.0.0");
        AssetView.Release packRelease = pack.releases().getFirst();

        assertTrue(pack.ownershipHealth().ownerPresent());
        assertFalse(pack.ownershipHealth().backupOwnerPresent());
        assertTrue(pack.ownershipHealth().continuityAtRisk());
        assets.assignRole(
                AUTHOR,
                pack.id(),
                "user",
                SUPPORT_AGENT_ID.toString(),
                AssetRole.OWNER);
        AssetView handedOver = assets.assignRole(
                AUTHOR,
                pack.id(),
                "user",
                BACKUP_OWNER_ID.toString(),
                AssetRole.BACKUP_OWNER);
        assertTrue(handedOver.ownershipHealth().ownerPresent());
        assertTrue(handedOver.ownershipHealth().backupOwnerPresent());
        assertFalse(handedOver.ownershipHealth().orphaned());
        assertFalse(handedOver.ownershipHealth().continuityAtRisk());

        for (UUID assetId : List.of(prompt.id(), instruction.id(), pack.id())) {
            if (!assetId.equals(pack.id())) {
                AssetView covered = assets.assignRole(
                        AUTHOR,
                        assetId,
                        "user",
                        BACKUP_OWNER_ID.toString(),
                        AssetRole.BACKUP_OWNER);
                assertFalse(covered.ownershipHealth().continuityAtRisk());
            }
            assets.assignRole(
                    AUTHOR,
                    assetId,
                    "user",
                    SUPPORT_AGENT_ID.toString(),
                    AssetRole.VIEWER);
        }
        List<ResourceRef> supportResources = List.of(
                ResourceRef.of(ORGANIZATION_ID, "asset", prompt.id()),
                ResourceRef.of(ORGANIZATION_ID, "asset", instruction.id()),
                ResourceRef.of(ORGANIZATION_ID, "asset", pack.id()));
        when(authorizationSets.listAuthorizedResources(any()))
                .thenAnswer(invocation -> {
                    AuthorizedResourceQuery query = invocation.getArgument(0);
                    return AuthorizedResourceSetResult.resolved(
                            query.principal().equals(SUPPORT_AGENT.principal())
                                    ? supportResources
                                    : List.of(),
                            MODEL_ID);
                });

        var discovery = assistantTools.recommend(
                SUPPORT_AGENT, "onboarding", AssetType.CAPABILITY_PACK);
        assertEquals(1, discovery.recommendations().size());
        assertEquals(
                packRelease.id(),
                discovery.recommendations().getFirst().releaseId());

        PromptEvaluationResult evaluation = prompts.evaluate(
                SUPPORT_AGENT, prompt.id(), promptRelease.id());
        assertTrue(evaluation.passed());
        assertEquals(8, evaluation.passedCases());
        PromptRunResult firstCorrectTask = prompts.run(
                SUPPORT_AGENT,
                prompt.id(),
                promptRelease.id(),
                Map.of(
                        "ticket_text",
                        tickets.getFirst().id() + ": " + tickets.getFirst().text()),
                "support SLA escalation",
                "golden-poc-first-correct-task");
        assertTrue(firstCorrectTask.output().contains("\"category\":\"billing\""));
        assertTrue(firstCorrectTask.output().contains("\"accountableTeam\":\"NONE\""));
        assertEquals(1, firstCorrectTask.citations().size());
        PromptRunResult.PromptCitation citation =
                firstCorrectTask.citations().getFirst();
        assertEquals(
                UUID.fromString("90000000-0000-0000-0000-000000000001"),
                citation.chunkId());
        assertEquals(GOLDEN_KNOWLEDGE_ASSET_ID, citation.knowledgeAssetId());
        assertEquals(
                UUID.fromString("90000000-0000-0000-0000-000000000004"),
                citation.sourceRevisionId());
        assertEquals("SLA and escalation", citation.title());
        assertEquals("Response tiers", citation.heading());
        assertTrue(ticketPassesRubric(tickets.getFirst(), firstCorrectTask));
        assertFalse(ticketPassesRubric(
                tickets.getFirst(),
                new PromptRunResult(
                        UUID.randomUUID(),
                        prompt.id(),
                        promptRelease.id(),
                        promptRelease.digest(),
                        PROMPT_ROUTE,
                        """
                        {"category":"billing","slaTier":"P0","escalate":true,\
                        "accountableTeam":"INCIDENT_RESPONSE","response":"unsupported"}
                        """,
                        List.of(),
                        1)));

        Map<String, Object> storedGoldenRun = jdbc.queryForMap(
                """
                select citation_refs::text as citations,
                       sanitized_outcome::text as outcome
                from prompt_runs
                where id = ?
                """,
                firstCorrectTask.runId());
        assertTrue(storedGoldenRun.get("citations").toString()
                .contains(GOLDEN_KNOWLEDGE_ASSET_ID.toString()));
        assertFalse(storedGoldenRun.toString().contains(tickets.getFirst().text()));
        assertFalse(storedGoldenRun.toString().contains(firstCorrectTask.output()));

        WorkInstructionView acknowledged = instructions.acknowledge(
                SUPPORT_AGENT, instruction.id(), instructionRelease.id());
        assertTrue(acknowledged.acknowledged());
        PackJourney journey = packs.start(
                SUPPORT_AGENT, pack.id(), packRelease.id());
        for (PackJourney.Item item : journey.items()) {
            journey = packs.setItemCompleted(
                    SUPPORT_AGENT,
                    pack.id(),
                    packRelease.id(),
                    item.key(),
                    true);
        }
        assertEquals(PackAssignmentStatus.COMPLETED, journey.status());
        assertEquals(3, journey.completedAccessibleItems());

        AssetView changedPrompt = assets.updateDraft(
                AUTHOR,
                prompt.id(),
                prompt.draft().lockVersion(),
                new AssetDraftInput(
                        "Asset triage-customer-ticket",
                        "Replacement Prompt",
                        "INTERNAL",
                        "1",
                        goldenFixture("prompt-template.json").replace(
                                "Using only approved support policy",
                                "Using the revised approved support policy")));
        assertNotEquals(prompt.draft().lockVersion(), changedPrompt.draft().lockVersion());
        AssetView replacementSubmission = assets.submit(
                AUTHOR, prompt.id(), "Revise support wording");
        approve(prompt.id(), replacementSubmission);
        AssetView replacement = assets.publish(
                AUTHOR,
                prompt.id(),
                replacementSubmission.revisions().getFirst().id(),
                "2.0.0");
        assertNotEquals(
                promptRelease.id(), replacement.releases().getFirst().id());
        assertEquals(
                promptRelease.id(),
                packs.get(SUPPORT_AGENT, pack.id(), packRelease.id())
                        .items()
                        .stream()
                        .filter(item -> item.key().equals("prompt"))
                        .findFirst()
                        .orElseThrow()
                        .pinnedVersionId());

        assets.withdraw(
                AUTHOR,
                prompt.id(),
                promptRelease.id(),
                "Replaced by the approved 2.0.0 release");
        assertThrows(
                AssetUnavailableException.class,
                () -> prompts.run(
                        SUPPORT_AGENT,
                        prompt.id(),
                        promptRelease.id(),
                        Map.of(
                                "ticket_text",
                                tickets.getFirst().id()
                                        + ": "
                                        + tickets.getFirst().text()),
                        "support SLA escalation",
                        "golden-poc-withdrawn-release"));

        assertEquals(
                9,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from prompt_runs
                        where actor_user_id = ? and status = 'SUCCEEDED'
                        """,
                        Integer.class,
                        SUPPORT_AGENT_ID));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from pack_assignments
                        where actor_user_id = ? and status = 'COMPLETED'
                        """,
                        Integer.class,
                        SUPPORT_AGENT_ID));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        select count(*)
                        from asset_audit_events
                        where asset_id = ? and event_type = 'RELEASE_WITHDRAWN'
                        """,
                        Integer.class,
                        prompt.id()));
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

    private AssetView createApprovedRelease(
            AssetType type,
            String slug,
            String payload,
            String versionLabel) {
        AssetView created = assets.create(
                AUTHOR,
                type,
                "support",
                slug,
                SPACE_ID,
                new AssetDraftInput(
                        "Asset " + slug,
                        "Integration fixture " + slug,
                        "INTERNAL",
                        "1",
                        payload));
        grantWorkflowRoles(created.id());
        AssetView submitted = assets.submit(
                AUTHOR, created.id(), "Publish " + slug);
        approve(created.id(), submitted);
        return assets.publish(
                AUTHOR,
                created.id(),
                submitted.revisions().getFirst().id(),
                versionLabel);
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
        String escapedPayload = payload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return new AssetDraftInput(
                "Triage customer ticket",
                "Classify and route an L1 support ticket",
                "INTERNAL",
                "1",
                """
                {
                  "objective": "Classify and route an L1 support ticket",
                  "audience": "L1 support",
                  "useWhen": ["A new customer ticket arrives"],
                  "doNotUseWhen": ["The ticket contains a legal threat"],
                  "textTemplate": "%s",
                  "messages": [],
                  "variables": [],
                  "outputContract": {},
                  "dataPolicy": {
                    "retainRawVariables": false,
                    "retainRawOutput": false
                  },
                  "compatibility": ["chat"],
                  "knowledgeRequirements": [],
                  "evaluationCases": [],
                  "knownLimitations": "Integration fixture"
                }
                """.formatted(escapedPayload));
    }

    private static byte[] skillArchive(String name, String description)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry skill = new ZipEntry(name + "/SKILL.md");
            zip.putNextEntry(skill);
            zip.write("""
                    ---
                    name: %s
                    description: %s
                    metadata:
                      owner: support-operations
                    ---
                    # Support triage
                    """.formatted(name, description).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static String promptPayloadWithEvaluation() {
        return """
                {
                  "objective": "Classify a support ticket",
                  "audience": "L1 support",
                  "useWhen": ["A new ticket arrives"],
                  "doNotUseWhen": ["A legal threat is present"],
                  "textTemplate": "Classify: {{ticket_text}}",
                  "messages": [],
                  "variables": [{
                    "name": "ticket_text",
                    "type": "STRING",
                    "required": true,
                    "defaultValue": null,
                    "sensitive": true,
                    "pattern": "",
                    "allowedValues": []
                  }],
                  "outputContract": {"type":"object","required":["category"]},
                  "dataPolicy": {
                    "retainRawVariables": false,
                    "retainRawOutput": false
                  },
                  "compatibility": ["chat"],
                  "knowledgeRequirements": [],
                  "evaluationCases": [{
                    "name": "access ticket",
                    "variables": {"ticket_text":"Cannot log in"},
                    "expectedContains": ["access"],
                    "forbiddenContains": ["secret"]
                  }],
                  "knownLimitations": ""
                }
                """;
    }

    private static String promptPayloadWithoutVariables() {
        return """
                {
                  "objective": "Provide a support response",
                  "audience": "L1 support",
                  "useWhen": ["A ticket was classified"],
                  "doNotUseWhen": ["Legal review is required"],
                  "textTemplate": "Draft an approved response.",
                  "messages": [],
                  "variables": [],
                  "outputContract": {},
                  "dataPolicy": {
                    "retainRawVariables": false,
                    "retainRawOutput": false
                  },
                  "compatibility": ["chat"],
                  "knowledgeRequirements": [],
                  "evaluationCases": [],
                  "knownLimitations": ""
                }
                """;
    }

    private static String workInstructionPayload() {
        return """
                {
                  "purpose": "Respond to one support ticket",
                  "audience": "L1 support",
                  "prerequisites": ["Ticket is assigned"],
                  "completionOutcome": "Customer receives a safe response",
                  "responsibleRole": "L1 support agent",
                  "steps": [{
                    "key": "respond",
                    "title": "Respond",
                    "instruction": "Use verified facts only.",
                    "expectedResult": "A response draft",
                    "check": "No unsupported promise",
                    "escalation": "Escalate exceptions",
                    "prohibitedActions": ["Disclose internal data"],
                    "relatedAssetIds": [],
                    "relatedKnowledgeVersionIds": []
                  }]
                }
                """;
    }

    private static String packPayload(
            UUID promptAssetId,
            UUID promptReleaseId,
            UUID instructionAssetId,
            UUID instructionReleaseId) {
        return """
                {
                  "purpose": "ROLE_ONBOARDING",
                  "audience": "L1 support",
                  "prerequisites": ["Active support account"],
                  "expectedOutcome": "Agent can complete first ticket",
                  "items": [
                    {
                      "key": "prompt",
                      "required": true,
                      "kind": "REGISTRY_RELEASE",
                      "assetId": "%s",
                      "releaseId": "%s",
                      "knowledgeAssetId": null,
                      "knowledgeVersionId": null
                    },
                    {
                      "key": "instruction",
                      "required": true,
                      "kind": "REGISTRY_RELEASE",
                      "assetId": "%s",
                      "releaseId": "%s",
                      "knowledgeAssetId": null,
                      "knowledgeVersionId": null
                    }
                  ],
                  "completionCriteria": ["Required items complete"],
                  "reviewDate": "2026-12-31",
                  "owner": "Support operations"
                }
                """.formatted(
                promptAssetId,
                promptReleaseId,
                instructionAssetId,
                instructionReleaseId);
    }

    private static RetrievedKnowledgeEvidence goldenKnowledgeEvidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.fromString("90000000-0000-0000-0000-000000000001"),
                UUID.fromString("90000000-0000-0000-0000-000000000002"),
                UUID.fromString("90000000-0000-0000-0000-000000000003"),
                UUID.fromString("90000000-0000-0000-0000-000000000004"),
                "SLA and escalation",
                "P0 is 15 minutes. P1 is 1 hour. P2 is 4 business hours.",
                "fixture://support.sla-and-escalation@1",
                null,
                null,
                "Response tiers",
                1.0,
                1.0,
                1.0,
                UUID.fromString("90000000-0000-0000-0000-000000000005"),
                UUID.fromString("90000000-0000-0000-0000-000000000005"),
                MODEL_ID,
                UUID.fromString("90000000-0000-0000-0000-000000000006"),
                1);
    }

    private static String goldenFixture(String name) throws IOException {
        String resource = "/golden/asset-registry/" + name;
        try (var stream =
                AssetRegistryIntegrationTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing golden fixture: " + resource);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static boolean ticketPassesRubric(
            MockTicket ticket, PromptRunResult result) throws IOException {
        Map<String, Object> output = JSON.readValue(
                result.output(),
                new TypeReference<>() {
                });
        return ticket.category().equals(output.get("category"))
                && ticket.slaTier().equals(output.get("slaTier"))
                && Boolean.valueOf(ticket.escalate()).equals(output.get("escalate"))
                && ticket.accountableTeam().equals(output.get("accountableTeam"))
                && output.get("response").toString()
                        .contains("support.sla-and-escalation@1")
                && ticket.allowedCitations()
                        .contains("support.sla-and-escalation@1")
                && result.citations().stream().anyMatch(citation ->
                        GOLDEN_KNOWLEDGE_ASSET_ID.equals(citation.knowledgeAssetId()));
    }

    private record MockTicket(
            String id,
            String scenario,
            String text,
            String category,
            String slaTier,
            boolean escalate,
            String accountableTeam,
            List<String> allowedCitations) {
    }

    private void clearAssetRegistry() {
        jdbc.execute("""
                TRUNCATE TABLE
                    assistant_asset_feedback,
                    assistant_asset_traces,
                    prompt_evaluation_runs,
                    prompt_runs,
                    pack_progress,
                    pack_assignments,
                    work_instruction_acknowledgements,
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
