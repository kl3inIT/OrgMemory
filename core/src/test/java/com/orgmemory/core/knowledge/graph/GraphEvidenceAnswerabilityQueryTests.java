package com.orgmemory.core.knowledge.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;
import com.orgmemory.graphrag.processing.GraphProcessingProfile;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphEvidenceAnswerabilityQueryTests {

    @Test
    void sourceReadyStillIndexesUntilTheExactGraphGenerationIsPublished() {
        GraphProcessingProfileResolver resolver = mock(GraphProcessingProfileResolver.class);
        GraphProcessingProfile desired = mock(GraphProcessingProfile.class);
        GraphProcessingProfileRepository profiles = mock(GraphProcessingProfileRepository.class);
        PersistedGraphProcessingProfile persisted = mock(PersistedGraphProcessingProfile.class);
        GraphProcessingProfileRef profile = mock(GraphProcessingProfileRef.class);
        GraphIndexJobRepository jobs = mock(GraphIndexJobRepository.class);
        GraphIndexJob job = mock(GraphIndexJob.class);
        ProjectionPublicationStore publications = mock(ProjectionPublicationStore.class);
        UUID profileId = UUID.randomUUID();
        UUID assetVersionId = UUID.randomUUID();
        when(desired.canonicalSha256()).thenReturn("a".repeat(64));
        when(resolver.current()).thenReturn(desired);
        when(profiles.findByCanonicalSha256("a".repeat(64)))
                .thenReturn(Optional.of(persisted));
        when(persisted.toRef()).thenReturn(profile);
        when(profile.id()).thenReturn(profileId);
        when(jobs.findByKnowledgeAssetVersionIdAndGraphProcessingProfileId(
                        assetVersionId, profileId))
                .thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(GraphIndexJobStatus.SUCCEEDED);
        when(job.getProjectionGeneration()).thenReturn(7L);
        GraphEvidenceAnswerabilityQuery query = new GraphEvidenceAnswerabilityQuery(
                resolver, profiles, jobs, publications);

        GovernedEvidenceRef source = readySource(assetVersionId);
        assertEquals(
                GraphEvidenceAnswerability.State.INDEXING,
                query.evaluate(source).state());

        ProjectionSnapshot exact = mock(ProjectionSnapshot.class);
        ProjectionSnapshot current = mock(ProjectionSnapshot.class);
        when(current.generation()).thenReturn(7L);
        when(publications.published(anyNamespace(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(Optional.of(exact));
        when(publications.current(anyNamespace())).thenReturn(Optional.of(current));

        assertEquals(
                GraphEvidenceAnswerability.State.READY,
                query.evaluate(source).state());
    }

    private static com.orgmemory.graphrag.storage.ProjectionNamespace anyNamespace() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static GovernedEvidenceRef readySource(UUID assetVersionId) {
        return new GovernedEvidenceRef(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                GovernedEvidenceRef.ProcessingState.READY,
                true,
                true,
                true,
                UUID.randomUUID(),
                assetVersionId,
                "Policy",
                "policy.pdf",
                null);
    }
}
