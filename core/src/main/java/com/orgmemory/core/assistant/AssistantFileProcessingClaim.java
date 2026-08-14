package com.orgmemory.core.assistant;

import java.util.Optional;
import java.util.UUID;

public record AssistantFileProcessingClaim(
        UUID fileId,
        UUID organizationId,
        UUID actorUserId,
        String fileName,
        String mediaType,
        long contentLength,
        String contentSha256,
        String objectKey,
        long processingGeneration,
        AssistantFileProcessingProfile requestedProfile,
        Optional<AssistantFileProcessingProfile> resolvedProfile) {
    public AssistantFileProcessingClaim {
        resolvedProfile = resolvedProfile == null ? Optional.empty() : resolvedProfile;
    }
}
