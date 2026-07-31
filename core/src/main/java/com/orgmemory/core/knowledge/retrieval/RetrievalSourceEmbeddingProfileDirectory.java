package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileDirectory;
import com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileView;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Adapts retrieval profile metadata to the source-list projection. */
@Service
class RetrievalSourceEmbeddingProfileDirectory implements SourceEmbeddingProfileDirectory {

    private final EmbeddingProfileRegistry profiles;

    RetrievalSourceEmbeddingProfileDirectory(EmbeddingProfileRegistry profiles) {
        this.profiles = profiles;
    }

    @Override
    public SourceEmbeddingProfileView get(UUID organizationId, UUID profileId) {
        EmbeddingProfileRef profile = profiles.get(organizationId, profileId);
        return new SourceEmbeddingProfileView(
                profile.id(),
                profile.profileKey(),
                profile.provider(),
                profile.model(),
                profile.dimensions());
    }
}
