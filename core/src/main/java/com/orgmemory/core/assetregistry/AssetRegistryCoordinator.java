package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTarget;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTargetQuery;
import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioCommand;
import com.orgmemory.core.assetregistry.api.AssetRegistrationCommand;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetRoleCommand;
import com.orgmemory.core.assetregistry.api.AssetRoleQuery;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.authorization.PrincipalRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssetRegistryCoordinator {

    static final String REVIEW_POLICY_VERSION = "asset-review-v1";

    private final AssetRegistrationCommand registrations;
    private final AssetIdentityQuery identities;
    private final AssetAuthorizationTargetQuery targets;
    private final AssetRoleCommand roleCommands;
    private final AssetRoleQuery roleQueries;
    private final AssetPortfolioCommand portfolios;
    private final AssetCatalogReadModelRepository catalog;
    private final AssetDraftRepository drafts;
    private final AssetRevisionRepository revisions;
    private final AssetReviewCaseRepository reviews;
    private final AssetReviewDecisionRepository decisions;
    private final AssetReleaseRepository releases;
    private final AssetReleaseAvailabilityRepository availability;
    private final AssetAuditEventRepository audit;
    private final AssetPayloadReferenceRepository payloadReferences;
    private final SkillPackageSupersessionRepository packageSupersessions;
    private final AssetTypeProfileRegistry profiles;
    private final SkillPackageSpecReader skillPackages;
    private final AssetPayloadDigester digester;

    AssetRegistryCoordinator(
            AssetRegistrationCommand registrations,
            AssetIdentityQuery identities,
            AssetAuthorizationTargetQuery targets,
            AssetRoleCommand roleCommands,
            AssetRoleQuery roleQueries,
            AssetPortfolioCommand portfolios,
            AssetCatalogReadModelRepository catalog,
            AssetDraftRepository drafts,
            AssetRevisionRepository revisions,
            AssetReviewCaseRepository reviews,
            AssetReviewDecisionRepository decisions,
            AssetReleaseRepository releases,
            AssetReleaseAvailabilityRepository availability,
            AssetAuditEventRepository audit,
            AssetPayloadReferenceRepository payloadReferences,
            SkillPackageSupersessionRepository packageSupersessions,
            AssetTypeProfileRegistry profiles,
            SkillPackageSpecReader skillPackages,
            AssetPayloadDigester digester) {
        this.registrations = registrations;
        this.identities = identities;
        this.targets = targets;
        this.roleCommands = roleCommands;
        this.roleQueries = roleQueries;
        this.portfolios = portfolios;
        this.catalog = catalog;
        this.drafts = drafts;
        this.revisions = revisions;
        this.reviews = reviews;
        this.decisions = decisions;
        this.releases = releases;
        this.availability = availability;
        this.audit = audit;
        this.payloadReferences = payloadReferences;
        this.packageSupersessions = packageSupersessions;
        this.profiles = profiles;
        this.skillPackages = skillPackages;
        this.digester = digester;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID create(
            CurrentActor actor,
            AssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftInput input) {
        return create(
                actor, type, namespace, slug, knowledgeSpaceId, input, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID createSkill(
            CurrentActor actor,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        return create(
                actor,
                AssetType.SKILL,
                namespace,
                slug,
                knowledgeSpaceId,
                input,
                Objects.requireNonNull(storedPackage, "storedPackage"));
    }

    private UUID create(
            CurrentActor actor,
            AssetType type,
            String namespace,
            String slug,
            UUID knowledgeSpaceId,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        Objects.requireNonNull(actor, "actor");
        if (type == null
                || namespace == null
                || slug == null
                || knowledgeSpaceId == null) {
            throw new BusinessValidationException(
                    "asset.identity-invalid",
                    "The Asset namespace, slug, type, or Knowledge Space is invalid");
        }
        AssetPayloadDigester.CanonicalAssetPayload canonical =
                validateDraft(type, input);
        UUID assetId;
        try {
            assetId = registrations.register(new AssetRegistrationCommand.NewAsset(
                    actor.organizationId(),
                    type,
                    namespace,
                    slug,
                    knowledgeSpaceId,
                    actor.principal(),
                    actor.userId()));
        } catch (IllegalArgumentException invalidIdentity) {
            throw new BusinessValidationException(
                    "asset.identity-invalid",
                    "The Asset namespace, slug, type, or Knowledge Space is invalid",
                    invalidIdentity);
        }
        AssetDraft draft = drafts.saveAndFlush(new AssetDraft(
                actor.organizationId(),
                assetId,
                canonical.title(),
                canonical.summary(),
                canonical.classification(),
                canonical.schemaVersion(),
                canonical.payload(),
                actor.userId()));
        if (storedPackage != null) {
            if (type != AssetType.SKILL) {
                throw new IllegalArgumentException(
                        "Only Skill Assets may carry a package reference");
            }
            SkillPackageSpec spec = skillSpec(canonical.payload());
            requireMatchingPackage(spec.artifact(), storedPackage);
            payloadReferences.saveAndFlush(
                    AssetPayloadReference.forDraft(draft, storedPackage));
        }
        recordAudit(
                actor,
                assetId,
                "ASSET_CREATED",
                "DRAFT",
                draft.getId(),
                "{\"permission\":\"can_create_asset\"}");
        return assetId;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<AssetAuthorizationTarget> target(UUID organizationId, UUID assetId) {
        return targets.find(organizationId, assetId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    List<AssetSummary> summaries(
            UUID organizationId,
            Collection<UUID> ids,
            String query,
            AssetType type) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return catalog.searchAuthorized(
                organizationId,
                ids,
                normalizedQuery,
                type,
                PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    List<AssetRecommendation> recommendations(
            UUID organizationId,
            Collection<UUID> ids,
            String query,
            AssetType type) {
        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return summaries(organizationId, ids, null, type).stream()
                .map(summary -> recommendation(organizationId, summary))
                .flatMap(Optional::stream)
                .filter(item -> matchesRecommendation(item, normalizedQuery))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetSummaryPage ownedSummaryPage(
            UUID organizationId,
            UUID userId,
            Collection<UUID> visibleIds,
            String query,
            AssetType type,
            AssetOwnedSort sort,
            int page,
            int pageSize) {
        if (visibleIds.isEmpty()) {
            return AssetSummaryPage.empty(page, pageSize, sort);
        }
        var ids = new java.util.LinkedHashSet<>(roleQueries.activeAssetIdsForUserRole(
                organizationId,
                userId.toString(),
                AssetRole.OWNER,
                Instant.now()));
        ids.retainAll(visibleIds);
        if (ids.isEmpty()) {
            return AssetSummaryPage.empty(page, pageSize, sort);
        }
        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Page<AssetSummary> result = catalog.searchOwnedSummaries(
                organizationId,
                ids,
                normalizedQuery,
                type,
                sort.name(),
                PageRequest.of(page - 1, pageSize));
        return new AssetSummaryPage(
                result.getContent(),
                result.getTotalElements(),
                page,
                pageSize,
                result.getTotalPages(),
                sort);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetRecommendationPage recommendationPage(
            UUID organizationId,
            Collection<UUID> ids,
            String query,
            AssetType type,
            AssetCatalogSort sort,
            int page,
            int pageSize) {
        if (ids.isEmpty()) {
            return AssetRecommendationPage.empty(page, pageSize, sort);
        }
        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Page<AssetRecommendation> result = catalog.searchAuthorizedRecommendations(
                organizationId,
                ids,
                normalizedQuery,
                type,
                AssetAvailability.WITHDRAWN,
                sort.name(),
                PageRequest.of(page - 1, pageSize));
        return new AssetRecommendationPage(
                result.getContent(),
                result.getTotalElements(),
                page,
                pageSize,
                result.getTotalPages(),
                sort);
    }

    private Optional<AssetRecommendation> recommendation(
            UUID organizationId, AssetSummary summary) {
        return releases.findByAssetIdAndOrganizationIdOrderBySequenceDesc(
                        summary.id(), organizationId)
                .stream()
                .filter(release -> currentAvailability(release.getId())
                        != AssetAvailability.WITHDRAWN)
                .findFirst()
                .map(release -> {
                    AssetAvailability releaseAvailability =
                            currentAvailability(release.getId());
                    return new AssetRecommendation(
                            summary.id(),
                            summary.type(),
                            summary.namespace(),
                            summary.slug(),
                            release.getTitle(),
                            release.getSummary(),
                            summary.knowledgeSpaceId(),
                            summary.portfolioState(),
                            release.getId(),
                            release.getVersionLabel(),
                            release.getDigest(),
                            releaseAvailability,
                            release.getReleasedAt());
                });
    }

    private static boolean matchesRecommendation(
            AssetRecommendation recommendation, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return List.of(
                        recommendation.namespace(),
                        recommendation.slug(),
                        recommendation.title(),
                        recommendation.summary())
                .stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetView view(UUID organizationId, UUID assetId) {
        AssetIdentity asset = requiredAsset(organizationId, assetId);
        return view(asset);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetConsumptionRelease consumptionRelease(
            UUID organizationId,
            UUID assetId,
            UUID releaseId) {
        AssetIdentity asset = requiredAsset(organizationId, assetId);
        AssetRelease release = releases
                .findByIdAndAssetIdAndOrganizationId(
                        releaseId, assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
        AssetAvailability releaseAvailability = currentAvailability(releaseId);
        if (releaseAvailability == AssetAvailability.WITHDRAWN) {
            throw new AssetUnavailableException(
                    "The requested Asset release is not available for new use");
        }
        return new AssetConsumptionRelease(
                asset.id(),
                release.getId(),
                release.getRevisionId(),
                asset.type(),
                asset.namespace(),
                asset.slug(),
                release.getVersionLabel(),
                release.getPublicationMode(),
                release.getTitle(),
                release.getSummary(),
                release.getClassification(),
                release.getSchemaVersion(),
                release.getPayload(),
                release.getDigest(),
                releaseAvailability,
                release.getReleasedAt());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetConsumptionRelease latestConsumptionRelease(
            UUID organizationId, UUID assetId) {
        AssetIdentity asset = requiredAsset(organizationId, assetId);
        AssetRelease release = releases
                .findLatestUsable(
                        assetId,
                        organizationId,
                        AssetAvailability.WITHDRAWN,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(AssetNotFoundException::new);
        return consumptionRelease(
                organizationId, asset.id(), release.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView updateDraft(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            AssetDraftInput input) {
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        if (asset.type() == AssetType.SKILL) {
            throw new BusinessValidationException(
                    "skill.generic-edit-unsupported",
                    "Skill drafts cannot be changed through the generic payload endpoint");
        }
        AssetDraft draft = requiredDraft(actor.organizationId(), assetId);
        if (draft.getVersion() != expectedLockVersion) {
            throw new AssetConflictException(
                    "The Asset draft changed; reload it before saving");
        }
        AssetPayloadDigester.CanonicalAssetPayload canonical =
                validateDraft(asset.type(), input);
        draft.update(
                canonical.title(),
                canonical.summary(),
                canonical.classification(),
                canonical.schemaVersion(),
                canonical.payload(),
                actor.userId());
        drafts.saveAndFlush(draft);
        recordAudit(
                actor,
                assetId,
                "DRAFT_UPDATED",
                "DRAFT",
                draft.getId(),
                "{\"permission\":\"can_edit\"}");
        return view(asset);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SkillDraftReplacement replaceSkillDraft(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            AssetDraftInput input,
            SkillPackageStoragePort.StoredSkillPackage storedPackage) {
        AssetDraft draft = requiredDraftForUpdate(actor.organizationId(), assetId);
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        if (asset.type() != AssetType.SKILL) {
            throw new BusinessValidationException(
                    "skill.package-replacement-unsupported",
                    "Only Skill drafts can replace a package");
        }
        if (draft.getVersion() != expectedLockVersion) {
            throw new AssetConflictException(
                    "The Skill draft changed; reload it before replacing the package");
        }
        AssetPayloadDigester.CanonicalAssetPayload canonical =
                validateDraft(AssetType.SKILL, input);
        SkillPackageSpec spec = skillSpec(canonical.payload());
        requireMatchingPackage(spec.artifact(), storedPackage);
        AssetPayloadReference previous = payloadReferences
                .findByDraftIdAndOrganizationId(draft.getId(), actor.organizationId())
                .orElseThrow(() -> new AssetConflictException(
                        "The Skill draft is missing its package reference"));

        payloadReferences.delete(previous);
        payloadReferences.flush();
        payloadReferences.saveAndFlush(
                AssetPayloadReference.forDraft(draft, storedPackage));
        draft.update(
                canonical.title(),
                canonical.summary(),
                canonical.classification(),
                canonical.schemaVersion(),
                canonical.payload(),
                actor.userId());
        drafts.saveAndFlush(draft);
        SkillPackageSupersession supersession = packageSupersessions.saveAndFlush(
                new SkillPackageSupersession(
                        actor.organizationId(),
                        assetId,
                        previous.getReferenceValue(),
                        storedPackage.objectKey(),
                        Instant.now()));
        recordAudit(
                actor,
                assetId,
                "SKILL_DRAFT_PACKAGE_REPLACED",
                "DRAFT",
                draft.getId(),
                "{\"permission\":\"can_edit\",\"previousDigest\":\""
                        + previous.getDigest()
                        + "\",\"replacementDigest\":\""
                        + storedPackage.sha256()
                        + "\"}");
        return new SkillDraftReplacement(view(asset), supersession.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView submit(CurrentActor actor, UUID assetId, String changeNote) {
        String validatedChangeNote = requireChangeNote(changeNote);
        AssetDraft draft = requiredDraftForUpdate(actor.organizationId(), assetId);
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        if (reviews.existsByAssetIdAndOrganizationIdAndState(
                assetId, actor.organizationId(), AssetReviewState.IN_REVIEW)) {
            throw new AssetConflictException("This Asset already has a revision in review");
        }
        profiles.require(asset.type()).requireSupported(draft.getSchemaVersion());
        AssetPayloadDigester.CanonicalAssetPayload canonical = digester.canonicalize(
                draft.getTitle(),
                draft.getSummary(),
                draft.getClassification(),
                draft.getSchemaVersion(),
                draft.getPayload());
        AssetRevision revision;
        try {
            revision = revisions.saveAndFlush(new AssetRevision(
                    draft,
                    revisions.maxSequence(assetId, actor.organizationId()) + 1,
                    canonical,
                    validatedChangeNote,
                    actor.userId()));
        } catch (DataIntegrityViolationException duplicate) {
            if (!causedByConstraint(duplicate, "uq_asset_revision_sequence")) {
                throw duplicate;
            }
            throw new AssetConflictException(
                    "Another revision was submitted concurrently; reload and retry",
                    duplicate);
        }
        AssetReviewCase review = reviews.saveAndFlush(new AssetReviewCase(
                revision, REVIEW_POLICY_VERSION, actor.userId()));
        if (asset.type() == AssetType.SKILL) {
            SkillPackageSpec spec = skillSpec(draft.getPayload());
            AssetPayloadReference draftReference = payloadReferences
                    .findByDraftIdAndOrganizationId(
                            draft.getId(), actor.organizationId())
                    .orElseThrow(() -> new AssetConflictException(
                            "The Skill draft is missing its package reference"));
            requireMatchingPackage(spec.artifact(), draftReference);
            payloadReferences.saveAndFlush(
                    AssetPayloadReference.forRevision(revision, draftReference));
        }
        recordAudit(
                actor,
                assetId,
                "REVISION_SUBMITTED",
                "REVIEW_CASE",
                review.getId(),
                "{\"digest\":\"" + revision.getDigest() + "\"}");
        return view(requiredAsset(actor.organizationId(), assetId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView decide(
            CurrentActor actor,
            UUID assetId,
            UUID reviewCaseId,
            AssetReviewDecisionType decision,
            String comment) {
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        AssetReviewCase review = reviews.findByIdAndAssetIdAndOrganizationId(
                        reviewCaseId, assetId, actor.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        AssetRevision revision = revisions.findByIdAndAssetIdAndOrganizationId(
                        review.getRevisionId(), assetId, actor.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        String conflict = decisionConflict(
                actor,
                review,
                revision,
                decision);
        if (conflict != null) {
            throw new AssetConflictException(conflict);
        }
        Instant now = Instant.now();
        review.decide(decision, now);
        AssetReviewDecision recorded = new AssetReviewDecision(
                review, actor.userId(), decision, comment, now);
        decisions.save(recorded);
        reviews.saveAndFlush(review);
        recordAudit(
                actor,
                assetId,
                "REVIEW_" + decision,
                "REVIEW_CASE",
                review.getId(),
                "{\"digest\":\"" + review.getRevisionDigest() + "\"}");
        return view(asset);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    AssetReviewDecisionActions reviewDecisionActions(
            CurrentActor actor,
            UUID assetId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        Optional<AssetReviewCase> openReview = reviews
                .findByAssetIdAndOrganizationIdOrderByCreatedAtDesc(
                        assetId,
                        actor.organizationId())
                .stream()
                .filter(review -> review.getState()
                        == AssetReviewState.IN_REVIEW)
                .findFirst();
        if (openReview.isEmpty()) {
            return AssetReviewDecisionActions.none();
        }
        AssetRevision revision = revisions
                .findByIdAndAssetIdAndOrganizationId(
                        openReview.get().getRevisionId(),
                        assetId,
                        actor.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        return reviewDecisionActions(
                actor,
                openReview.get(),
                revision);
    }

    static AssetReviewDecisionActions reviewDecisionActions(
            CurrentActor actor,
            AssetReviewCase review,
            AssetRevision revision) {
        return new AssetReviewDecisionActions(
                decisionConflict(
                                actor,
                                review,
                                revision,
                                AssetReviewDecisionType.APPROVE)
                        == null,
                decisionConflict(
                                actor,
                                review,
                                revision,
                                AssetReviewDecisionType.REQUEST_CHANGES)
                        == null,
                decisionConflict(
                                actor,
                                review,
                                revision,
                                AssetReviewDecisionType.REJECT)
                        == null,
                decisionConflict(
                                actor,
                                review,
                                revision,
                                AssetReviewDecisionType.CANCEL)
                        == null);
    }

    private static String decisionConflict(
            CurrentActor actor,
            AssetReviewCase review,
            AssetRevision revision,
            AssetReviewDecisionType decision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(decision, "decision");
        if (!review.getRevisionDigest().equals(revision.getDigest())) {
            return "Review digest no longer matches its revision";
        }
        if (decision == AssetReviewDecisionType.APPROVE
                && revision.getCreatedByUserId().equals(actor.userId())) {
            return "A revision author cannot approve their own revision";
        }
        if (decision == AssetReviewDecisionType.CANCEL
                && !review.getRequestedByUserId().equals(actor.userId())) {
            return "Only the review requester may cancel this review";
        }
        if (review.getState() != AssetReviewState.IN_REVIEW) {
            return "This review case is already resolved";
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView publish(
            CurrentActor actor,
            UUID assetId,
            UUID revisionId,
            String versionLabel) {
        String validatedVersionLabel = validatedVersionLabel(versionLabel);
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        AssetRevision revision = revisions.findByIdAndAssetIdAndOrganizationId(
                        revisionId, assetId, actor.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        AssetReviewCase review = reviews.findByRevisionIdAndOrganizationId(
                        revisionId, actor.organizationId())
                .filter(candidate -> candidate.getAssetId().equals(assetId))
                .orElseThrow(() -> new AssetConflictException(
                        "The revision has not completed review"));
        if (review.getState() != AssetReviewState.APPROVED
                || !review.getRevisionDigest().equals(revision.getDigest())) {
            throw new AssetConflictException(
                    "Only the exact approved revision digest may be released");
        }
        if (releases.findByRevisionIdAndOrganizationId(
                        revisionId, actor.organizationId()).isPresent()) {
            throw new AssetConflictException("This revision already has a release");
        }
        AssetPayloadDigester.CanonicalAssetPayload canonical = digester.canonicalize(
                revision.getTitle(),
                revision.getSummary(),
                revision.getClassification(),
                revision.getSchemaVersion(),
                revision.getPayload());
        if (!canonical.digest().equals(revision.getDigest())) {
            throw new AssetConflictException("Revision bytes no longer match the approved digest");
        }
        AssetRelease release = createRelease(
                actor,
                asset,
                revision,
                validatedVersionLabel,
                AssetPublicationMode.REVIEWED,
                "Initial reviewed release");
        recordAudit(
                actor,
                assetId,
                "RELEASE_PUBLISHED",
                "RELEASE",
                release.getId(),
                "{\"digest\":\"" + release.getDigest() + "\"}");
        return view(requiredAsset(actor.organizationId(), assetId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView publishSkillDraft(
            CurrentActor actor,
            UUID assetId,
            String versionLabel) {
        String validatedVersionLabel = validatedVersionLabel(versionLabel);
        AssetDraft draft = requiredDraftForUpdate(actor.organizationId(), assetId);
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        if (asset.type() != AssetType.SKILL) {
            throw new BusinessValidationException(
                    "skill.direct-publish-unsupported",
                    "Direct publication is available only for Skill Assets");
        }
        if (reviews.existsByAssetIdAndOrganizationIdAndState(
                assetId, actor.organizationId(), AssetReviewState.IN_REVIEW)) {
            throw new AssetConflictException(
                    "This Skill already has a revision in review; finish or cancel it first");
        }
        profiles.require(AssetType.SKILL).requireSupported(draft.getSchemaVersion());
        AssetPayloadDigester.CanonicalAssetPayload canonical = digester.canonicalize(
                draft.getTitle(),
                draft.getSummary(),
                draft.getClassification(),
                draft.getSchemaVersion(),
                draft.getPayload());
        AssetPayloadReference draftReference = payloadReferences
                .findByDraftIdAndOrganizationId(
                        draft.getId(), actor.organizationId())
                .orElseThrow(() -> new AssetConflictException(
                        "The Skill draft is missing its package reference"));
        SkillPackageSpec spec = skillSpec(draft.getPayload());
        requireMatchingPackage(spec.artifact(), draftReference);

        AssetRevision revision;
        try {
            revision = revisions.saveAndFlush(new AssetRevision(
                    draft,
                    revisions.maxSequence(assetId, actor.organizationId()) + 1,
                    canonical,
                    "Direct Skill publication",
                    actor.userId()));
        } catch (DataIntegrityViolationException duplicate) {
            if (!causedByConstraint(duplicate, "uq_asset_revision_sequence")) {
                throw duplicate;
            }
            throw new AssetConflictException(
                    "Another revision was published concurrently; reload and retry",
                    duplicate);
        }
        payloadReferences.saveAndFlush(
                AssetPayloadReference.forRevision(revision, draftReference));

        AssetRelease release = createRelease(
                actor,
                asset,
                revision,
                validatedVersionLabel,
                AssetPublicationMode.DIRECT,
                "Direct Skill publication");
        recordAudit(
                actor,
                assetId,
                "SKILL_RELEASE_PUBLISHED_DIRECTLY",
                "RELEASE",
                release.getId(),
                "{\"policy\":\"skill-direct-v1\","
                        + "\"permission\":\"can_publish_skill\","
                        + "\"digest\":\"" + release.getDigest() + "\"}");
        return view(requiredAsset(actor.organizationId(), assetId));
    }

    private AssetRelease createRelease(
            CurrentActor actor,
            AssetIdentity asset,
            AssetRevision revision,
            String versionLabel,
            AssetPublicationMode publicationMode,
            String availabilityReason) {
        Instant now = Instant.now();
        AssetRelease release;
        try {
            release = releases.saveAndFlush(new AssetRelease(
                    revision,
                    releases.maxSequence(asset.id(), actor.organizationId()) + 1,
                    versionLabel,
                    publicationMode,
                    actor.userId(),
                    now));
        } catch (DataIntegrityViolationException duplicate) {
            if (!causedByConstraint(
                    duplicate,
                    "uq_asset_release_revision",
                    "uq_asset_release_sequence",
                    "uq_asset_release_label")) {
                throw duplicate;
            }
            throw new AssetConflictException(
                    "The release coordinate is already used by different content",
                    duplicate);
        }
        availability.save(new AssetReleaseAvailabilityEvent(
                release,
                AssetAvailability.AVAILABLE,
                availabilityReason,
                actor.userId(),
                now));
        if (asset.type() == AssetType.SKILL) {
            AssetPayloadReference revisionReference = payloadReferences
                    .findByRevisionIdAndOrganizationId(
                            revision.getId(), actor.organizationId())
                    .orElseThrow(() -> new AssetConflictException(
                            "The Skill revision is missing its package reference"));
            SkillPackageSpec spec = skillSpec(revision.getPayload());
            requireMatchingPackage(spec.artifact(), revisionReference);
            payloadReferences.saveAndFlush(
                    AssetPayloadReference.forRelease(release, revisionReference));
        }
        portfolios.activateAfterRelease(actor.organizationId(), asset.id());
        return release;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AssetView changeAvailability(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            AssetAvailability next,
            String reason) {
        if (next == AssetAvailability.AVAILABLE) {
            throw new IllegalArgumentException("A release cannot be made available again");
        }
        AssetIdentity asset = requiredAsset(actor.organizationId(), assetId);
        AssetRelease release = releases.findByIdAndAssetIdAndOrganizationId(
                        releaseId, assetId, actor.organizationId())
                .orElseThrow(AssetNotFoundException::new);
        AssetAvailability current = currentAvailability(releaseId);
        if (current == AssetAvailability.WITHDRAWN
                || (current == AssetAvailability.DEPRECATED
                        && next == AssetAvailability.DEPRECATED)) {
            throw new AssetConflictException("The requested availability transition is invalid");
        }
        Instant now = Instant.now();
        availability.saveAndFlush(new AssetReleaseAvailabilityEvent(
                release, next, requireReason(reason), actor.userId(), now));
        AssetIdentity updated;
        if (next == AssetAvailability.WITHDRAWN && allReleasesWithdrawn(asset, releaseId)) {
            updated = portfolios.retireAfterFinalWithdrawal(actor.organizationId(), assetId);
        } else {
            updated = portfolios.startSunsettingAfterReleaseChange(
                    actor.organizationId(), assetId);
        }
        recordAudit(
                actor,
                assetId,
                "RELEASE_" + next,
                "RELEASE",
                releaseId,
                "{\"previous\":\"" + current + "\"}");
        return view(updated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID assignRole(
            CurrentActor actor,
            UUID assetId,
            PrincipalRef principal,
            AssetRole role) {
        UUID assignmentId = roleCommands.assign(new AssetRoleCommand.Assignment(
                actor.organizationId(),
                assetId,
                principal,
                role,
                actor.userId()));
        recordAudit(
                actor,
                assetId,
                "ROLE_ASSIGNED",
                "ROLE_ASSIGNMENT",
                assignmentId,
                "{\"role\":\"" + role + "\"}");
        return assetId;
    }

    private AssetIdentity requiredAsset(UUID organizationId, UUID assetId) {
        return identities.findById(organizationId, assetId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private AssetDraft requiredDraftForUpdate(UUID organizationId, UUID assetId) {
        return drafts.findForUpdate(assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private AssetDraft requiredDraft(UUID organizationId, UUID assetId) {
        return drafts.findByAssetIdAndOrganizationId(assetId, organizationId)
                .orElseThrow(AssetNotFoundException::new);
    }

    private boolean allReleasesWithdrawn(AssetIdentity asset, UUID releaseBeingWithdrawn) {
        return releases.findByAssetIdAndOrganizationIdOrderBySequenceDesc(
                        asset.id(), asset.organizationId())
                .stream()
                .allMatch(release -> release.getId().equals(releaseBeingWithdrawn)
                        || currentAvailability(release.getId()) == AssetAvailability.WITHDRAWN);
    }

    private AssetAvailability currentAvailability(UUID releaseId) {
        return availability
                .findFirstByReleaseIdOrderByEffectiveAtDescCreatedAtDesc(releaseId)
                .map(AssetReleaseAvailabilityEvent::getAvailability)
                .orElseThrow(() -> new IllegalStateException(
                        "Asset release is missing availability history"));
    }

    private AssetView view(AssetIdentity asset) {
        AssetDraft draft = requiredDraft(asset.organizationId(), asset.id());
        List<AssetRevision> assetRevisions =
                revisions.findByAssetIdAndOrganizationIdOrderBySequenceDesc(
                        asset.id(), asset.organizationId());
        List<AssetReviewCase> assetReviews =
                reviews.findByAssetIdAndOrganizationIdOrderByCreatedAtDesc(
                        asset.id(), asset.organizationId());
        List<AssetRelease> assetReleases =
                releases.findByAssetIdAndOrganizationIdOrderBySequenceDesc(
                        asset.id(), asset.organizationId());
        Instant viewedAt = Instant.now();
        AssetRoleQuery.RoleHistory roleHistory =
                roleQueries.history(asset.organizationId(), asset.id(), viewedAt);
        return new AssetView(
                asset.id(),
                asset.type(),
                asset.namespace(),
                asset.slug(),
                asset.knowledgeSpaceId(),
                asset.portfolioState(),
                asset.authorizationReady(),
                new AssetView.Draft(
                        draft.getId(),
                        draft.getVersion(),
                        draft.getTitle(),
                        draft.getSummary(),
                        draft.getClassification(),
                        draft.getSchemaVersion(),
                        draft.getPayload(),
                        draft.getEditedByUserId(),
                        draft.getUpdatedAt()),
                assetRevisions.stream().map(AssetRegistryCoordinator::revisionView).toList(),
                assetReviews.stream().map(this::reviewView).toList(),
                assetReleases.stream().map(this::releaseView).toList(),
                ownershipHealth(roleHistory.ownershipHealth()),
                roleHistory.assignments().stream()
                        .map(AssetRegistryCoordinator::roleView)
                        .toList());
    }

    private static AssetView.OwnershipHealth ownershipHealth(
            AssetRoleQuery.OwnershipHealth ownershipHealth) {
        return new AssetView.OwnershipHealth(
                ownershipHealth.ownerPresent(),
                ownershipHealth.backupOwnerPresent(),
                ownershipHealth.orphaned(),
                ownershipHealth.continuityAtRisk());
    }

    private AssetView.Review reviewView(AssetReviewCase review) {
        return new AssetView.Review(
                review.getId(),
                review.getRevisionId(),
                review.getRevisionDigest(),
                review.getState(),
                review.getPolicyVersion(),
                review.getRequestedByUserId(),
                review.getCreatedAt(),
                review.getResolvedAt(),
                decisions.findByReviewCaseIdOrderByDecidedAtAsc(review.getId())
                        .stream()
                        .map(decision -> new AssetView.Decision(
                                decision.getReviewerUserId(),
                                decision.getDecision(),
                                decision.getComment(),
                                decision.getDecidedAt()))
                        .toList());
    }

    private AssetView.Release releaseView(AssetRelease release) {
        List<AssetView.AvailabilityEvent> history = availability
                .findByReleaseIdOrderByEffectiveAtAscCreatedAtAsc(release.getId())
                .stream()
                .map(event -> new AssetView.AvailabilityEvent(
                        event.getAvailability(),
                        event.getReason(),
                        event.getChangedByUserId(),
                        event.getEffectiveAt()))
                .toList();
        AssetAvailability current = currentAvailability(release.getId());
        return new AssetView.Release(
                release.getId(),
                release.getRevisionId(),
                release.getSequence(),
                release.getVersionLabel(),
                release.getPublicationMode(),
                release.getTitle(),
                release.getSummary(),
                release.getClassification(),
                release.getSchemaVersion(),
                release.getPayload(),
                release.getDigest(),
                release.getReleasedByUserId(),
                release.getReleasedAt(),
                current,
                history);
    }

    private void recordAudit(
            CurrentActor actor,
            UUID assetId,
            String eventType,
            String subjectType,
            UUID subjectId,
            String context) {
        audit.save(new AssetAuditEvent(
                actor.organizationId(),
                assetId,
                actor.userId(),
                eventType,
                subjectType,
                subjectId,
                context,
                Instant.now()));
    }

    private static AssetView.Revision revisionView(AssetRevision revision) {
        return new AssetView.Revision(
                revision.getId(),
                revision.getSequence(),
                revision.getTitle(),
                revision.getSummary(),
                revision.getClassification(),
                revision.getSchemaVersion(),
                revision.getPayload(),
                revision.getDigest(),
                revision.getChangeNote(),
                revision.getCreatedByUserId(),
                revision.getCreatedAt());
    }

    private static AssetView.RoleAssignment roleView(AssetRoleQuery.RoleAssignment assignment) {
        return new AssetView.RoleAssignment(
                assignment.id(),
                assignment.principalType(),
                assignment.principalId(),
                assignment.role(),
                assignment.validFrom(),
                assignment.validUntil(),
                assignment.assignedByUserId(),
                assignment.projectedAt());
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new BusinessValidationException(
                    "asset.availability-reason-required",
                    "An availability change needs a reason");
        }
        return normalized;
    }

    private AssetPayloadDigester.CanonicalAssetPayload validateDraft(
            AssetType type,
            AssetDraftInput input) {
        if (input == null
                || input.title() == null
                || input.summary() == null
                || input.classification() == null
                || input.schemaVersion() == null
                || input.payload() == null) {
            throw new BusinessValidationException(
                    "asset.draft-invalid",
                    "The Asset draft is invalid");
        }
        try {
            AssetTypeProfile profile = profiles.require(type);
            profile.validate(input.schemaVersion(), input.payload());
            return digester.canonicalize(
                    input.title(),
                    input.summary(),
                    input.classification(),
                    input.schemaVersion(),
                    input.payload());
        } catch (IllegalArgumentException invalidDraft) {
            throw new BusinessValidationException(
                    "asset.draft-invalid",
                    "The Asset draft is invalid",
                    invalidDraft);
        }
    }

    private static String requireChangeNote(String changeNote) {
        String normalized = changeNote == null ? "" : changeNote.trim();
        if (normalized.isEmpty() || normalized.length() > 1024) {
            throw new BusinessValidationException(
                    "asset.change-note-invalid",
                    "A change note of at most 1024 characters is required");
        }
        return normalized;
    }

    private static String validatedVersionLabel(String versionLabel) {
        if (versionLabel == null) {
            throw new BusinessValidationException(
                    "asset.version-label-invalid",
                    "The release version label is invalid");
        }
        try {
            return AssetRelease.validateVersionLabel(versionLabel);
        } catch (IllegalArgumentException invalidLabel) {
            throw new BusinessValidationException(
                    "asset.version-label-invalid",
                    "The release version label is invalid",
                    invalidLabel);
        }
    }

    private SkillPackageSpec skillSpec(String payload) {
        return skillPackages.read(payload);
    }

    private static void requireMatchingPackage(
            SkillPackageSpec.Artifact artifact,
            SkillPackageStoragePort.StoredSkillPackage stored) {
        if (!stored.sha256().equals(artifact.sha256())
                || stored.contentLength() != artifact.contentLength()
                || !stored.mediaType().equals(artifact.mediaType())) {
            throw new AssetConflictException(
                    "The stored Skill package does not match its validated metadata");
        }
    }

    private static void requireMatchingPackage(
            SkillPackageSpec.Artifact artifact,
            AssetPayloadReference reference) {
        if (!reference.isBlobReference()
                || reference.getDigest() == null
                || !reference.getDigest().equals(artifact.sha256())
                || reference.getContentLength() == null
                || reference.getContentLength() != artifact.contentLength()
                || reference.getMediaType() == null
                || !reference.getMediaType().equals(artifact.mediaType())) {
            throw new AssetConflictException(
                    "The Skill package reference no longer matches its validated metadata");
        }
    }

    private static boolean causedByConstraint(
            Throwable failure, String... constraintNames) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            for (String constraintName : constraintNames) {
                if (message.contains(constraintName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
