package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.acl.AclAuthority;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SourceRevisionServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID REVISION_ID = UUID.randomUUID();
    private static final UUID BLOB_ID = UUID.randomUUID();
    private static final UUID RAW_ID = UUID.randomUUID();
    private static final UUID NORMALIZED_ID = UUID.randomUUID();
    private static final UUID ACL_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final String SOURCE_SYSTEM = "slack";
    private static final String CONNECTION_KEY = "workspace-1";
    private static final String EXTERNAL_OBJECT_ID = "channel-1";

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final EvidenceBlobRepository blobs = mock(EvidenceBlobRepository.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceGraphIndexPort graphIndexJobs = mock(SourceGraphIndexPort.class);
    private final SourceRevisionService service =
            new SourceRevisionService(sources, blobs, revisions, graphIndexJobs);

    @Test
    void revisionPhasesKeepIndependentTransactionBoundaries() throws Exception {
        assertEquals(
                Propagation.REQUIRES_NEW,
                transactionOn(
                                "findExisting",
                                UUID.class,
                                String.class,
                                String.class,
                                String.class,
                                String.class)
                        .propagation());
        assertEquals(
                Propagation.REQUIRES_NEW,
                transactionOn("stage", StageSourceRevisionCommand.class).propagation());
        assertEquals(
                Propagation.REQUIRES_NEW,
                transactionOn("complete", CompleteSourceRevisionCommand.class).propagation());
    }

    @Test
    void exposesAnExistingRevisionAsImmutableDraftFacts() {
        SourceObject source = mock(SourceObject.class);
        SourceRevision revision = mock(SourceRevision.class);
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        EXTERNAL_OBJECT_ID))
                .thenReturn(Optional.of(source));
        when(source.getId()).thenReturn(SOURCE_ID);
        when(revisions.findBySourceObjectIdAndContentSha256(SOURCE_ID, "sha256"))
                .thenReturn(Optional.of(revision));
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getRevisionNumber()).thenReturn(2L);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);

        SourceRevisionDraftRef result = service.findExisting(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        EXTERNAL_OBJECT_ID,
                        "sha256")
                .orElseThrow();

        assertEquals(new SourceRevisionDraftRef(SOURCE_ID, REVISION_ID, 2L, true), result);
    }

    @Test
    void stagesEvidenceAndTheNextRevisionTogether() {
        StoredObject stored = new StoredObject(
                new ObjectKey("organizations/test/source.txt"),
                12L,
                "text/plain",
                "sha256",
                "etag",
                "v1");
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID,
                        SOURCE_SYSTEM,
                        CONNECTION_KEY,
                        EXTERNAL_OBJECT_ID))
                .thenReturn(Optional.empty());
        when(sources.saveAndFlush(any(SourceObject.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(blobs.saveAndFlush(any(EvidenceBlob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.saveAndFlush(any(SourceRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.maximumRevisionNumber(SOURCE_ID)).thenReturn(0L);

        SourceRevisionDraftRef result = service.stage(new StageSourceRevisionCommand(
                ORGANIZATION_ID,
                SPACE_ID,
                ACTOR_ID,
                AclAuthority.SOURCE,
                SOURCE_SYSTEM,
                CONNECTION_KEY,
                EXTERNAL_OBJECT_ID,
                "Onboarding",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                SOURCE_ID,
                REVISION_ID,
                BLOB_ID,
                stored));

        assertEquals(SOURCE_ID, result.sourceObjectId());
        assertEquals(REVISION_ID, result.sourceRevisionId());
        assertEquals(1L, result.revisionNumber());
        assertFalse(result.published());
        verify(sources, times(2)).saveAndFlush(any(SourceObject.class));
        verify(blobs).saveAndFlush(any(EvidenceBlob.class));
        verify(revisions).saveAndFlush(any(SourceRevision.class));
    }

    @Test
    void completionPublishesTheRevisionAndSchedulesGraphAtomically() {
        SourceObject source = mock(SourceObject.class);
        SourceRevision revision = mock(SourceRevision.class);
        when(revisions.findById(REVISION_ID)).thenReturn(Optional.of(revision));
        when(sources.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(revision.getId()).thenReturn(REVISION_ID);
        when(revision.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        var processingProfile = DocumentProcessingProfileSnapshot.from("connector-profile");
        var embeddingProfile = new SourceEmbeddingProfileRef(UUID.randomUUID(), 1536);
        var raw = new RawSourceRef(RAW_ID, ACL_ID, RawSourceStatus.NORMALIZED);
        var normalized = new NormalizedRecordRef(
                NORMALIZED_ID,
                RAW_ID,
                ACL_ID,
                NormalizedRecordStatus.PROMOTED,
                null);
        var asset = new SourceKnowledgeAssetRef(
                ASSET_ID, VERSION_ID, NORMALIZED_ID, RAW_ID, ACL_ID);

        service.complete(new CompleteSourceRevisionCommand(
                new SourceRevisionDraftRef(SOURCE_ID, REVISION_ID, 1L, false),
                "pipeline-v1",
                "parser-v1",
                "chunker-v1",
                processingProfile,
                embeddingProfile,
                raw,
                normalized,
                asset));

        verify(revision).ready(
                org.mockito.ArgumentMatchers.eq("pipeline-v1"),
                org.mockito.ArgumentMatchers.eq("parser-v1"),
                org.mockito.ArgumentMatchers.eq("chunker-v1"),
                org.mockito.ArgumentMatchers.eq(processingProfile),
                org.mockito.ArgumentMatchers.eq(embeddingProfile),
                org.mockito.ArgumentMatchers.eq(raw),
                org.mockito.ArgumentMatchers.eq(normalized),
                org.mockito.ArgumentMatchers.eq(asset),
                any(Instant.class));
        verify(source).publishRevision(REVISION_ID);
        verify(revisions).saveAndFlush(revision);
        verify(graphIndexJobs).enqueue(
                org.mockito.ArgumentMatchers.eq(ORGANIZATION_ID),
                org.mockito.ArgumentMatchers.eq(REVISION_ID),
                org.mockito.ArgumentMatchers.eq(ASSET_ID),
                org.mockito.ArgumentMatchers.eq(VERSION_ID),
                any(Instant.class));
        verify(sources).save(source);
    }

    private static Transactional transactionOn(
            String methodName, Class<?>... parameterTypes) throws Exception {
        return SourceRevisionService.class
                .getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
    }
}
