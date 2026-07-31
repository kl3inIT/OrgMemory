package com.orgmemory.core.assetregistry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetView(
        UUID id,
        AssetType type,
        String namespace,
        String slug,
        UUID knowledgeSpaceId,
        AssetPortfolioState portfolioState,
        boolean authorizationReady,
        Draft draft,
        List<Revision> revisions,
        List<Review> reviews,
        List<Release> releases,
        OwnershipHealth ownershipHealth,
        List<RoleAssignment> roleAssignments) {

    public AssetView {
        revisions = List.copyOf(revisions);
        reviews = List.copyOf(reviews);
        releases = List.copyOf(releases);
        roleAssignments = List.copyOf(roleAssignments);
    }

    public record Draft(
            UUID id,
            long lockVersion,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            UUID editedByUserId,
            Instant updatedAt) {
    }

    public record Revision(
            UUID id,
            long sequence,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            String digest,
            String changeNote,
            UUID createdByUserId,
            Instant createdAt) {
    }

    public record Review(
            UUID id,
            UUID revisionId,
            String revisionDigest,
            AssetReviewState state,
            String policyVersion,
            UUID requestedByUserId,
            Instant createdAt,
            Instant resolvedAt,
            List<Decision> decisions) {

        public Review {
            decisions = List.copyOf(decisions);
        }
    }

    public record Decision(
            UUID reviewerUserId,
            AssetReviewDecisionType decision,
            String comment,
            Instant decidedAt) {
    }

    public record Release(
            UUID id,
            UUID revisionId,
            long sequence,
            String versionLabel,
            AssetPublicationMode publicationMode,
            String title,
            String summary,
            String classification,
            String schemaVersion,
            String payload,
            String digest,
            UUID releasedByUserId,
            Instant releasedAt,
            AssetAvailability availability,
            List<AvailabilityEvent> availabilityHistory) {

        public Release {
            availabilityHistory = List.copyOf(availabilityHistory);
        }
    }

    public record AvailabilityEvent(
            AssetAvailability availability,
            String reason,
            UUID changedByUserId,
            Instant effectiveAt) {
    }

    public record RoleAssignment(
            UUID id,
            String principalType,
            String principalId,
            AssetRole role,
            Instant validFrom,
            Instant validUntil,
            UUID assignedByUserId,
            Instant projectedAt) {
    }

    public record OwnershipHealth(
            boolean ownerPresent,
            boolean backupOwnerPresent,
            boolean orphaned,
            boolean continuityAtRisk) {
    }
}
