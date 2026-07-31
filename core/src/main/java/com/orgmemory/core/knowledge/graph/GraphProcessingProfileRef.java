package com.orgmemory.core.knowledge.graph;

import com.orgmemory.graphrag.processing.GraphProcessingProfile;
import java.util.Objects;
import java.util.UUID;

public record GraphProcessingProfileRef(
        UUID id,
        String canonicalSha256,
        GraphProcessingProfile profile) {

    public GraphProcessingProfileRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profile, "profile");
        if (!profile.canonicalSha256().equals(canonicalSha256)) {
            throw new IllegalArgumentException(
                    "graph processing profile hash does not match the profile");
        }
    }
}
