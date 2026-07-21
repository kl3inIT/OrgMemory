package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceQueryService {

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;
    private final EmbeddingProfileRegistry embeddingProfiles;

    SourceQueryService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions,
            EmbeddingProfileRegistry embeddingProfiles) {
        this.sources = sources;
        this.revisions = revisions;
        this.embeddingProfiles = embeddingProfiles;
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listOwn(CurrentActor actor) {
        return sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId())
                .stream()
                .map(source -> {
                    SourceRevision revision = revisions.findById(source.getCurrentRevisionId()).orElseThrow();
                    EmbeddingProfileRef profile = revision.getEmbeddingProfileId() == null
                            ? null
                            : embeddingProfiles.get(actor.organizationId(), revision.getEmbeddingProfileId());
                    return summary(source, revision, profile);
                })
                .toList();
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            EmbeddingProfileRef embeddingProfile) {
        return new SourceSummary(
                source.getId(),
                source.getTitle(),
                source.getSourceType(),
                revision.getStatus(),
                source.getClassification(),
                revision.getFileName(),
                revision.getMediaType(),
                revision.getContentLength(),
                revision.getFailureCode(),
                revision.getFailureMessage(),
                embeddingProfile == null ? null : embeddingProfile.profileKey(),
                embeddingProfile == null ? null : embeddingProfile.provider(),
                embeddingProfile == null ? null : embeddingProfile.model(),
                revision.getEmbeddingDimensions(),
                source.getCreatedAt(),
                revision.getUpdatedAt());
    }
}
