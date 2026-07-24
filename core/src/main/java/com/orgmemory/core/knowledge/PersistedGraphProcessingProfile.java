package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.processing.GraphProcessingProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graph_processing_profiles")
class PersistedGraphProcessingProfile {

    @Id
    private UUID id;

    @Column(name = "canonical_sha256", nullable = false, length = 64, updatable = false)
    private String canonicalSha256;

    @Column(name = "canonical_form", nullable = false, columnDefinition = "text", updatable = false)
    private String canonicalForm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PersistedGraphProcessingProfile() {
    }

    GraphProcessingProfileRef toRef() {
        GraphProcessingProfile profile =
                GraphProcessingProfile.restore(canonicalForm, canonicalSha256);
        return new GraphProcessingProfileRef(id, canonicalSha256, profile);
    }
}
