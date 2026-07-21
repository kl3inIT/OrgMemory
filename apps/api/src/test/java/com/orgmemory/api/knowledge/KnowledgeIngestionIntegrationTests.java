package com.orgmemory.api.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.AclCaptureStatus;
import com.orgmemory.core.knowledge.KnowledgeAssetRef;
import com.orgmemory.core.knowledge.KnowledgeIngestionConflictException;
import com.orgmemory.core.knowledge.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.NormalizationIssue;
import com.orgmemory.core.knowledge.NormalizeRawSourceCommand;
import com.orgmemory.core.knowledge.NormalizedRecordRef;
import com.orgmemory.core.knowledge.NormalizedRecordStatus;
import com.orgmemory.core.knowledge.PromoteNormalizedRecordCommand;
import com.orgmemory.core.knowledge.RawSourceRef;
import com.orgmemory.core.knowledge.RegisterRawSourceCommand;
import com.orgmemory.core.knowledge.RotateSourceAclCommand;
import com.orgmemory.core.knowledge.SourceAclEntryCommand;
import com.orgmemory.core.knowledge.SourceAclRotationRef;
import com.orgmemory.core.knowledge.SourcePrincipalType;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class KnowledgeIngestionIntegrationTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SALES_DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @Autowired
    KnowledgeIngestionService ingestion;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ingestionIsIdempotentAndPromotionCopiesSecurityLineage() {
        RegisterRawSourceCommand register = completeCommand(
                "integration-doc",
                "Source body for the integration document.",
                KnowledgeClassification.CONFIDENTIAL,
                DeclaredAccessScope.OWN_DEPARTMENT);

        RawSourceRef firstRaw = ingestion.registerRawSource(register);
        RawSourceRef repeatedRaw = ingestion.registerRawSource(register);
        NormalizedRecordRef firstNormalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                firstRaw.rawSourceObjectId(),
                "normalizer-v1",
                "Integration document",
                "Normalized content for the integration document.",
                "en"));
        NormalizedRecordRef repeatedNormalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                firstRaw.rawSourceObjectId(),
                "normalizer-v1",
                "Integration document",
                "Normalized content for the integration document.",
                "en"));
        KnowledgeAssetRef firstAsset = ingestion.promote(new PromoteNormalizedRecordCommand(
                ORGANIZATION_ID, firstNormalized.normalizedRecordId(), AccessGate.ALLOW));
        KnowledgeAssetRef repeatedAsset = ingestion.promote(new PromoteNormalizedRecordCommand(
                ORGANIZATION_ID, firstNormalized.normalizedRecordId(), AccessGate.ALLOW));

        assertEquals(firstRaw.rawSourceObjectId(), repeatedRaw.rawSourceObjectId());
        assertEquals(firstRaw.sourceAclSnapshotId(), repeatedRaw.sourceAclSnapshotId());
        assertEquals(firstNormalized.normalizedRecordId(), repeatedNormalized.normalizedRecordId());
        assertEquals(firstAsset.knowledgeAssetId(), repeatedAsset.knowledgeAssetId());
        assertEquals(firstRaw.rawSourceObjectId(), firstAsset.rawSourceObjectId());
        assertEquals(firstRaw.sourceAclSnapshotId(), firstAsset.sourceAclSnapshotId());

        var row = jdbc.queryForMap(
                """
                SELECT classification, declared_access, department_id, source_acl_snapshot_id
                FROM knowledge_assets
                WHERE id = ?
                """,
                firstAsset.knowledgeAssetId());
        assertEquals("CONFIDENTIAL", row.get("classification"));
        assertEquals("OWN_DEPARTMENT", row.get("declared_access"));
        assertEquals(SALES_DEPARTMENT_ID, row.get("department_id"));
        assertEquals(firstRaw.sourceAclSnapshotId(), row.get("source_acl_snapshot_id"));
    }

    @Test
    void sameSourceRevisionWithDifferentContentIsRejected() {
        RegisterRawSourceCommand first = completeCommand(
                "conflict-doc",
                "Original content.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);
        ingestion.registerRawSource(first);
        RegisterRawSourceCommand changed = new RegisterRawSourceCommand(
                first.organizationId(),
                first.departmentId(),
                first.sourceSystem(),
                first.sourceConnectionKey(),
                first.externalObjectId(),
                first.sourceVersion(),
                first.objectType(),
                first.title(),
                "Changed content under the same immutable revision.",
                first.sourceUri(),
                first.sourceModifiedAt(),
                first.classification(),
                first.declaredAccess(),
                first.aclCaptureStatus(),
                first.defaultGate(),
                first.aclValidUntil(),
                first.aclEntries());

        assertThrows(KnowledgeIngestionConflictException.class, () -> ingestion.registerRawSource(changed));
    }

    @Test
    void incompleteAclAndClassificationMismatchAreQuarantined() {
        RegisterRawSourceCommand unknownAcl = new RegisterRawSourceCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "TEST",
                "integration",
                "unknown-acl-doc",
                "v1",
                "DOCUMENT",
                "Unknown ACL document",
                "Content cannot be promoted while source ACL capture is unknown.",
                null,
                null,
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                AclCaptureStatus.UNKNOWN,
                AccessGate.UNKNOWN,
                null,
                List.of());
        RawSourceRef unknownRaw = ingestion.registerRawSource(unknownAcl);
        NormalizedRecordRef unknownNormalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                unknownRaw.rawSourceObjectId(),
                "normalizer-v1",
                "Unknown ACL document",
                "Normalized but not security-ready.",
                "en"));

        RegisterRawSourceCommand mismatch = completeCommand(
                "mismatched-classification-doc",
                "Restricted source with an invalid declared access scope.",
                KnowledgeClassification.RESTRICTED,
                DeclaredAccessScope.ALL_EMPLOYEES);
        RawSourceRef mismatchRaw = ingestion.registerRawSource(mismatch);
        NormalizedRecordRef mismatchNormalized = ingestion.normalize(new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                mismatchRaw.rawSourceObjectId(),
                "normalizer-v1",
                "Mismatched source",
                "This normalized output must remain quarantined.",
                "en"));

        assertEquals(NormalizedRecordStatus.QUARANTINED, unknownNormalized.status());
        assertEquals(NormalizationIssue.ACL_NOT_COMPLETE, unknownNormalized.issue());
        assertEquals(NormalizedRecordStatus.QUARANTINED, mismatchNormalized.status());
        assertEquals(NormalizationIssue.DECLARED_ACCESS_MISMATCH, mismatchNormalized.issue());
        assertThrows(
                IllegalStateException.class,
                () -> ingestion.promote(new PromoteNormalizedRecordCommand(
                        ORGANIZATION_ID, unknownNormalized.normalizedRecordId(), AccessGate.ALLOW)));
    }

    @Test
    void rotatingSourceAclAppendsEvidenceAdvancesHeadAndIsIdempotent() {
        RegisterRawSourceCommand register = completeCommand(
                "rotate-acl-doc",
                "Source body whose ACL will be rotated.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);
        RawSourceRef raw = ingestion.registerRawSource(register);
        Map<String, Object> originalSnapshot = snapshotEvidence(raw.sourceAclSnapshotId());

        RotateSourceAclCommand rotation = new RotateSourceAclCommand(
                ORGANIZATION_ID,
                raw.rawSourceObjectId(),
                AclCaptureStatus.COMPLETE,
                AccessGate.DENY,
                Instant.now().plus(Duration.ofHours(1)),
                List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                        ORGANIZATION_ID.toString(),
                        AccessGate.ALLOW)),
                raw.sourceAclSnapshotId());

        SourceAclRotationRef rotated = ingestion.rotateSourceAcl(rotation);
        SourceAclRotationRef retried = ingestion.rotateSourceAcl(rotation);
        RotateSourceAclCommand staleRotation = new RotateSourceAclCommand(
                ORGANIZATION_ID,
                raw.rawSourceObjectId(),
                register.aclCaptureStatus(),
                register.defaultGate(),
                register.aclValidUntil(),
                register.aclEntries(),
                raw.sourceAclSnapshotId());

        assertNotEquals(raw.sourceAclSnapshotId(), rotated.sourceAclSnapshotId());
        assertEquals(rotated, retried);
        assertThrows(
                KnowledgeIngestionConflictException.class,
                () -> ingestion.rotateSourceAcl(staleRotation));
        assertEquals(2L, rotated.aclGeneration());
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM source_acl_snapshots WHERE raw_source_object_id = ?",
                        Long.class,
                        raw.rawSourceObjectId()));
        Map<String, Object> head = jdbc.queryForMap(
                """
                SELECT current_raw_source_object_id, current_snapshot_id, acl_generation
                FROM source_acl_heads
                WHERE organization_id = ?
                  AND source_system = ?
                  AND source_connection_key = ?
                  AND external_object_id = ?
                """,
                ORGANIZATION_ID,
                register.sourceSystem(),
                register.sourceConnectionKey(),
                register.externalObjectId());
        assertEquals(raw.rawSourceObjectId(), head.get("current_raw_source_object_id"));
        assertEquals(rotated.sourceAclSnapshotId(), head.get("current_snapshot_id"));
        assertEquals(2L, ((Number) head.get("acl_generation")).longValue());
        assertEquals(originalSnapshot, snapshotEvidence(raw.sourceAclSnapshotId()));
    }

    @Test
    void databaseRejectsMutationOfSourceAclEvidence() {
        RawSourceRef raw = ingestion.registerRawSource(completeCommand(
                "immutable-acl-doc",
                "Source body with immutable ACL evidence.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES));
        UUID entryId = jdbc.queryForObject(
                "SELECT id FROM source_acl_entries WHERE source_acl_snapshot_id = ?",
                UUID.class,
                raw.sourceAclSnapshotId());

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE source_acl_snapshots SET valid_until = valid_until + interval '1 day' WHERE id = ?",
                        raw.sourceAclSnapshotId()));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM source_acl_snapshots WHERE id = ?", raw.sourceAclSnapshotId()));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE source_acl_entries SET gate = 'DENY' WHERE id = ?",
                        entryId));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM source_acl_entries WHERE id = ?", entryId));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO source_acl_entries (
                            id, organization_id, source_acl_snapshot_id,
                            principal_type, principal_key, gate, created_at
                        ) VALUES (?, ?, ?, 'ORGMEMORY_USER', ?, 'ALLOW', now())
                        """,
                        UUID.randomUUID(),
                        ORGANIZATION_ID,
                        raw.sourceAclSnapshotId(),
                        UUID.randomUUID().toString()));
        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "UPDATE source_acl_snapshot_seals SET entry_count = 0 WHERE source_acl_snapshot_id = ?",
                        raw.sourceAclSnapshotId()));
    }

    @Test
    void completeAclRejectsUnmappedSourceGroup() {
        RegisterRawSourceCommand base = completeCommand(
                "unmapped-source-group-doc",
                "Source body with an unresolved group principal.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);
        RegisterRawSourceCommand withSourceGroup = new RegisterRawSourceCommand(
                base.organizationId(),
                base.departmentId(),
                base.sourceSystem(),
                base.sourceConnectionKey(),
                base.externalObjectId(),
                base.sourceVersion(),
                base.objectType(),
                base.title(),
                base.rawContent(),
                base.sourceUri(),
                base.sourceModifiedAt(),
                base.classification(),
                base.declaredAccess(),
                AclCaptureStatus.COMPLETE,
                AccessGate.ALLOW,
                base.aclValidUntil(),
                List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.SOURCE_GROUP,
                        "source-contractors",
                        AccessGate.DENY)));

        assertThrows(IllegalArgumentException.class, () -> ingestion.registerRawSource(withSourceGroup));
    }

    @Test
    void sourceUriDropsQueryAndFragmentBeforePersistence() {
        RegisterRawSourceCommand base = completeCommand(
                "canonical-source-uri-doc",
                "Source body with a credential-free citation URL.",
                KnowledgeClassification.PUBLIC,
                DeclaredAccessScope.ALL);
        RegisterRawSourceCommand withSensitiveUri = new RegisterRawSourceCommand(
                base.organizationId(),
                base.departmentId(),
                base.sourceSystem(),
                base.sourceConnectionKey(),
                base.externalObjectId(),
                base.sourceVersion(),
                base.objectType(),
                base.title(),
                base.rawContent(),
                "https://source.example.test/docs/canonical?token=secret#private-section",
                base.sourceModifiedAt(),
                base.classification(),
                base.declaredAccess(),
                base.aclCaptureStatus(),
                base.defaultGate(),
                base.aclValidUntil(),
                base.aclEntries());

        RawSourceRef raw = ingestion.registerRawSource(withSensitiveUri);

        assertEquals(
                "https://source.example.test/docs/canonical",
                jdbc.queryForObject(
                        "SELECT source_uri FROM raw_source_objects WHERE id = ?",
                        String.class,
                        raw.rawSourceObjectId()));

        RegisterRawSourceCommand unsafeScheme = withSourceUri(
                completeCommand(
                        "unsafe-source-uri-doc",
                        "Source body with an unsafe citation scheme.",
                        KnowledgeClassification.PUBLIC,
                        DeclaredAccessScope.ALL),
                "javascript:alert('unsafe')");
        assertThrows(IllegalArgumentException.class, () -> ingestion.registerRawSource(unsafeScheme));
    }

    @Test
    void sourceRevisionAndAclRotationRejectStaleExpectedHeads() {
        RegisterRawSourceCommand firstCommand = completeCommand(
                "ordered-source-doc",
                "First source revision.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);
        RawSourceRef first = ingestion.registerRawSource(firstCommand);
        RegisterRawSourceCommand secondCommand = withSourceVersion(
                firstCommand,
                "v2",
                "Second source revision.",
                first.sourceAclSnapshotId());

        RawSourceRef second = ingestion.registerRawSource(secondCommand);
        RawSourceRef secondRetry = ingestion.registerRawSource(secondCommand);
        RegisterRawSourceCommand staleThird = withSourceVersion(
                firstCommand,
                "v3",
                "Delayed source revision.",
                first.sourceAclSnapshotId());

        assertEquals(second, secondRetry);
        assertThrows(
                KnowledgeIngestionConflictException.class,
                () -> ingestion.registerRawSource(staleThird));
    }

    @Test
    void completeAclRejectsValidityBeyondRefreshWindow() {
        RegisterRawSourceCommand base = completeCommand(
                "overlong-acl-ttl-doc",
                "Source body with an overlong ACL validity window.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);
        RegisterRawSourceCommand overlong = new RegisterRawSourceCommand(
                base.organizationId(),
                base.departmentId(),
                base.sourceSystem(),
                base.sourceConnectionKey(),
                base.externalObjectId(),
                base.sourceVersion(),
                base.objectType(),
                base.title(),
                base.rawContent(),
                base.sourceUri(),
                base.sourceModifiedAt(),
                base.classification(),
                base.declaredAccess(),
                base.aclCaptureStatus(),
                base.defaultGate(),
                Instant.now().plus(Duration.ofHours(25)),
                base.aclEntries());

        assertThrows(IllegalArgumentException.class, () -> ingestion.registerRawSource(overlong));
    }

    @Test
    void concurrentRetriesConvergeOnOneRawNormalizationAndAsset() throws Exception {
        RegisterRawSourceCommand register = completeCommand(
                "concurrent-retry-doc",
                "Source body delivered by concurrent importer retries.",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES);

        List<RawSourceRef> rawRefs = runConcurrently(() -> ingestion.registerRawSource(register));
        assertEquals(rawRefs.getFirst(), rawRefs.getLast());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM raw_source_objects WHERE external_object_id = ?",
                        Long.class,
                        register.externalObjectId()));

        NormalizeRawSourceCommand normalize = new NormalizeRawSourceCommand(
                ORGANIZATION_ID,
                rawRefs.getFirst().rawSourceObjectId(),
                "concurrent-normalizer-v1",
                "Concurrent retry document",
                "Normalized exactly once despite concurrent retries.",
                "en");
        List<NormalizedRecordRef> normalizedRefs = runConcurrently(() -> ingestion.normalize(normalize));
        assertEquals(normalizedRefs.getFirst(), normalizedRefs.getLast());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM normalized_records WHERE raw_source_object_id = ?",
                        Long.class,
                        rawRefs.getFirst().rawSourceObjectId()));

        PromoteNormalizedRecordCommand promote = new PromoteNormalizedRecordCommand(
                ORGANIZATION_ID,
                normalizedRefs.getFirst().normalizedRecordId(),
                AccessGate.ALLOW);
        List<KnowledgeAssetRef> assetRefs = runConcurrently(() -> ingestion.promote(promote));
        assertEquals(assetRefs.getFirst(), assetRefs.getLast());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_assets WHERE normalized_record_id = ?",
                        Long.class,
                        normalizedRefs.getFirst().normalizedRecordId()));
    }

    private Map<String, Object> snapshotEvidence(UUID snapshotId) {
        return jdbc.queryForMap(
                """
                SELECT acl_generation, capture_status, default_gate, acl_sha256, captured_at, valid_until
                FROM source_acl_snapshots
                WHERE id = ?
                """,
                snapshotId);
    }

    private static <T> List<T> runConcurrently(Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            Callable<T> synchronizedAction = () -> {
                assertTrue(barrier.await(10, TimeUnit.SECONDS) >= 0);
                return action.call();
            };
            var first = executor.submit(synchronizedAction);
            var second = executor.submit(synchronizedAction);
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static RegisterRawSourceCommand withSourceUri(RegisterRawSourceCommand base, String sourceUri) {
        return new RegisterRawSourceCommand(
                base.organizationId(),
                base.departmentId(),
                base.sourceSystem(),
                base.sourceConnectionKey(),
                base.externalObjectId(),
                base.sourceVersion(),
                base.objectType(),
                base.title(),
                base.rawContent(),
                sourceUri,
                base.sourceModifiedAt(),
                base.classification(),
                base.declaredAccess(),
                base.aclCaptureStatus(),
                base.defaultGate(),
                base.aclValidUntil(),
                base.aclEntries());
    }

    private static RegisterRawSourceCommand withSourceVersion(
            RegisterRawSourceCommand base,
            String sourceVersion,
            String rawContent,
            UUID expectedCurrentSnapshotId) {
        return new RegisterRawSourceCommand(
                base.organizationId(),
                base.departmentId(),
                base.sourceSystem(),
                base.sourceConnectionKey(),
                base.externalObjectId(),
                sourceVersion,
                base.objectType(),
                base.title(),
                rawContent,
                base.sourceUri(),
                base.sourceModifiedAt(),
                base.classification(),
                base.declaredAccess(),
                base.aclCaptureStatus(),
                base.defaultGate(),
                base.aclValidUntil(),
                base.aclEntries(),
                expectedCurrentSnapshotId);
    }

    private static RegisterRawSourceCommand completeCommand(
            String externalObjectId,
            String content,
            KnowledgeClassification classification,
            DeclaredAccessScope access) {
        return new RegisterRawSourceCommand(
                ORGANIZATION_ID,
                SALES_DEPARTMENT_ID,
                "TEST",
                "integration",
                externalObjectId,
                "v1",
                "DOCUMENT",
                "Integration source",
                content,
                "https://source.example.test/" + externalObjectId,
                Instant.parse("2026-07-10T00:00:00Z"),
                classification,
                access,
                AclCaptureStatus.COMPLETE,
                AccessGate.UNKNOWN,
                Instant.now().plus(Duration.ofHours(1)),
                List.of(new SourceAclEntryCommand(
                        SourcePrincipalType.ORGMEMORY_ORGANIZATION,
                        ORGANIZATION_ID.toString(),
                        AccessGate.ALLOW)));
    }
}
