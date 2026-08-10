package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces;
import com.orgmemory.core.knowledge.evidence.GovernedEvidenceRef;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proves that the exact bound revision is visible to the active GraphRAG profile. */
@Service
public class GraphEvidenceAnswerabilityQuery {

    private final GraphProcessingProfileResolver profileResolver;
    private final GraphProcessingProfileRepository profiles;
    private final GraphIndexJobRepository jobs;
    private final ProjectionPublicationStore publications;

    GraphEvidenceAnswerabilityQuery(
            GraphProcessingProfileResolver profileResolver,
            GraphProcessingProfileRepository profiles,
            GraphIndexJobRepository jobs,
            ProjectionPublicationStore publications) {
        this.profileResolver = profileResolver;
        this.profiles = profiles;
        this.jobs = jobs;
        this.publications = publications;
    }

    @Transactional(readOnly = true)
    public GraphEvidenceAnswerability evaluate(GovernedEvidenceRef source) {
        Objects.requireNonNull(source, "source");
        if (!source.readyAndCurrent()) {
            return GraphEvidenceAnswerability.indexing();
        }
        var desiredProfile = profileResolver.current();
        var profile = profiles.findByCanonicalSha256(desiredProfile.canonicalSha256())
                .map(PersistedGraphProcessingProfile::toRef)
                .orElse(null);
        if (profile == null) {
            return GraphEvidenceAnswerability.indexing();
        }
        GraphIndexJob job = jobs.findByKnowledgeAssetVersionIdAndGraphProcessingProfileId(
                        source.knowledgeAssetVersionId(), profile.id())
                .orElse(null);
        if (job == null) {
            return GraphEvidenceAnswerability.indexing();
        }
        if (job.getStatus() == GraphIndexJobStatus.FAILED
                || job.getStatus() == GraphIndexJobStatus.CANCELLED
                || job.getStatus() == GraphIndexJobStatus.SUPERSEDED) {
            return GraphEvidenceAnswerability.failed(job.getLastErrorCode());
        }
        if (job.getStatus() != GraphIndexJobStatus.SUCCEEDED) {
            return GraphEvidenceAnswerability.indexing();
        }
        var namespace = KnowledgeProjectionNamespaces.forSpace(
                source.organizationId(), source.knowledgeSpaceId());
        var exact = publications.published(namespace, job.getProjectionGeneration());
        var current = publications.current(namespace);
        if (exact.isEmpty()
                || current.isEmpty()
                || current.orElseThrow().generation() < job.getProjectionGeneration()) {
            return GraphEvidenceAnswerability.indexing();
        }
        return GraphEvidenceAnswerability.ready();
    }
}
