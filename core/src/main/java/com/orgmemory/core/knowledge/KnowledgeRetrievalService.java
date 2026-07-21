package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeAccessRequest;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.permission.KnowledgeResource;
import com.orgmemory.core.permission.KnowledgeRole;
import com.orgmemory.core.permission.KnowledgeSubject;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.permission.PermissionDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetrievalService {

    private static final PermissionKey CAN_SEARCH_KNOWLEDGE = PermissionKey.of("can_search_knowledge");

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final KnowledgeAssetRepository knowledgeAssets;
    private final AppUserRepository users;
    private final SourceAclPolicy sourceAclPolicy;
    private final KnowledgePermissionPolicy permissionPolicy;
    private final PermissionAuditService audit;
    private final EffectiveAuthorizationService authorization;

    public KnowledgeRetrievalService(
            KnowledgeAssetRepository knowledgeAssets,
            AppUserRepository users,
            SourceAclPolicy sourceAclPolicy,
            KnowledgePermissionPolicy permissionPolicy,
            PermissionAuditService audit,
            EffectiveAuthorizationService authorization) {
        this.knowledgeAssets = knowledgeAssets;
        this.users = users;
        this.sourceAclPolicy = sourceAclPolicy;
        this.permissionPolicy = permissionPolicy;
        this.audit = audit;
        this.authorization = authorization;
    }

    public List<KnowledgeAssetSummary> search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId) {
        String effectiveRequestId = requestId(requestId);
        String normalizedQuery = normalizeQuery(query);
        requireReader(
                actor,
                effectiveRequestId,
                normalizedQuery,
                "SEARCH",
                actor.organizationId().toString());
        int limit = validateLimit(requestedLimit);
        ResolvedSubject resolved = resolveSubject(
                actor, effectiveRequestId, normalizedQuery, "SEARCH", "KNOWLEDGE_SEARCH");
        Instant evaluatedAt = Instant.now();
        String pattern = searchPattern(normalizedQuery);

        List<KnowledgeAssetSearchRow> permitted = knowledgeAssets.searchPermitted(
                actor.organizationId(),
                actor.userId().toString(),
                resolved.departmentId() == null ? null : resolved.departmentId().toString(),
                actor.organizationId().toString(),
                resolved.departmentId(),
                resolved.role().name(),
                evaluatedAt,
                pattern,
                limit);

        List<PermissionAuditCommand> auditCommands = new ArrayList<>();
        auditCommands.add(auditCommand(
                actor,
                "SEARCH",
                "KNOWLEDGE_SEARCH",
                actor.organizationId().toString(),
                PermissionAuditDecision.ALLOW,
                "PERMISSION_FILTER_APPLIED",
                effectiveRequestId,
                normalizedQuery));
        List<KnowledgeAssetSummary> results = new ArrayList<>();
        for (KnowledgeAssetSearchRow asset : permitted) {
            VerifiedAsset verified = verifyReturnedAsset(actor, resolved, asset, evaluatedAt);
            if (!verified.decision().allowed()) {
                auditCommands.add(auditCommand(
                        actor,
                        "SEARCH",
                        "KNOWLEDGE_ASSET",
                        asset.getId().toString(),
                        PermissionAuditDecision.DENY,
                        "POLICY_PREDICATE_DRIFT_" + verified.decision().reason(),
                        effectiveRequestId,
                        normalizedQuery,
                        verified.sourceAcl()));
                log.error(
                        "Permission predicate drift omitted knowledge asset {} for request {}",
                        asset.getId(),
                        effectiveRequestId);
                continue;
            }
            auditCommands.add(auditCommand(
                    actor,
                    "SEARCH",
                    "KNOWLEDGE_ASSET",
                    asset.getId().toString(),
                    PermissionAuditDecision.ALLOW,
                    verified.decision().reason().name(),
                    effectiveRequestId,
                    normalizedQuery,
                    verified.sourceAcl()));
            results.add(new KnowledgeAssetSummary(
                    asset.getId(),
                    asset.getTitle(),
                    asset.getDepartmentId(),
                    KnowledgeClassification.valueOf(asset.getClassification()),
                    asset.getUpdatedAt()));
        }
        audit.recordAll(auditCommands);
        return results;
    }

    public KnowledgeAssetDetail get(CurrentActor actor, UUID assetId, String requestId) {
        String effectiveRequestId = requestId(requestId);
        requireReader(actor, effectiveRequestId, null, "READ", assetId.toString());
        ResolvedSubject resolved = resolveSubject(
                actor, effectiveRequestId, null, "READ", assetId.toString());
        Instant evaluatedAt = Instant.now();
        KnowledgeAssetDetailRow asset = knowledgeAssets.findPermittedById(
                        assetId,
                        actor.organizationId(),
                        actor.userId().toString(),
                        resolved.departmentId() == null ? null : resolved.departmentId().toString(),
                        actor.organizationId().toString(),
                        resolved.departmentId(),
                        resolved.role().name(),
                        evaluatedAt)
                .orElseGet(() -> deniedNotFound(
                        actor, resolved, assetId, effectiveRequestId, evaluatedAt));

        VerifiedAsset verified = verifyReturnedAsset(actor, resolved, asset, evaluatedAt);
        if (!verified.decision().allowed()) {
            audit.record(auditCommand(
                    actor,
                    "READ",
                    "KNOWLEDGE_ASSET",
                    assetId.toString(),
                    PermissionAuditDecision.DENY,
                    verified.decision().reason().name(),
                    effectiveRequestId,
                    null,
                    verified.sourceAcl()));
            throw new KnowledgeAssetNotFoundException();
        }

        audit.record(auditCommand(
                actor,
                "READ",
                "KNOWLEDGE_ASSET",
                assetId.toString(),
                PermissionAuditDecision.ALLOW,
                verified.decision().reason().name(),
                effectiveRequestId,
                null,
                verified.sourceAcl()));
        return new KnowledgeAssetDetail(
                asset.getId(),
                asset.getTitle(),
                asset.getContent(),
                asset.getLanguage(),
                asset.getDepartmentId(),
                KnowledgeClassification.valueOf(asset.getClassification()),
                asset.getSourceSystem(),
                asset.getExternalObjectId(),
                SourceCitationUri.safeForOutput(asset.getSourceUri()),
                asset.getActivatedAt(),
                asset.getUpdatedAt());
    }

    private ResolvedSubject resolveSubject(
            CurrentActor actor,
            String requestId,
            String query,
            String operation,
            String resourceId) {
        AppUser user = users.findById(actor.userId()).filter(candidate ->
                        candidate.getOrganizationId().equals(actor.organizationId()))
                .orElse(null);
        KnowledgeRole role = user == null ? null : knowledgeRole(user.getRole());
        boolean active = user != null && user.isActive();
        if (!active || role == null) {
            String reason = !active ? "INACTIVE_OR_MISSING_SUBJECT" : "SUBJECT_ROLE_MISSING";
            audit.record(auditCommand(
                    actor,
                    operation,
                    "KNOWLEDGE_ACCESS",
                    resourceId,
                    PermissionAuditDecision.DENY,
                    reason,
                    requestId,
                    query));
            throw new OrgMemoryAccessDeniedException("Knowledge access profile is incomplete");
        }
        return new ResolvedSubject(
                new KnowledgeSubject(
                        actor.organizationId().toString(),
                        actor.userId().toString(),
                        user.getDepartmentId() == null ? null : user.getDepartmentId().toString(),
                        role,
                        active),
                role,
                user.getDepartmentId());
    }

    private VerifiedAsset verifyReturnedAsset(
            CurrentActor actor,
            ResolvedSubject resolved,
            KnowledgeAssetAccessRow asset,
            Instant evaluatedAt) {
        SourceAclEvaluation sourceAcl = sourceAclPolicy.evaluate(
                actor,
                resolved.departmentId(),
                asset.getIngestionSnapshotId(),
                asset.getCurrentSnapshotId(),
                evaluatedAt);
        PermissionDecision decision = permissionPolicy.evaluate(new KnowledgeAccessRequest(
                resolved.subject(),
                new KnowledgeResource(
                        actor.organizationId().toString(),
                        asset.getId().toString(),
                        asset.getDepartmentId() == null ? null : asset.getDepartmentId().toString(),
                        KnowledgeClassification.valueOf(asset.getClassification()),
                        DeclaredAccessScope.valueOf(asset.getDeclaredAccess())),
                sourceAcl.effectiveGate(),
                AccessGate.valueOf(asset.getOrgMemoryGate())));
        return new VerifiedAsset(decision, sourceAcl);
    }

    private KnowledgeAssetDetailRow deniedNotFound(
            CurrentActor actor,
            ResolvedSubject resolved,
            UUID assetId,
            String requestId,
            Instant evaluatedAt) {
        KnowledgeAssetSecurityRow security = knowledgeAssets
                .findSecurityById(assetId, actor.organizationId())
                .orElse(null);
        SourceAclEvaluation sourceAcl = security == null
                ? null
                : sourceAclPolicy.evaluate(
                        actor,
                        resolved.departmentId(),
                        security.getIngestionSnapshotId(),
                        security.getCurrentSnapshotId(),
                        evaluatedAt);
        audit.record(auditCommand(
                actor,
                "READ",
                "KNOWLEDGE_ASSET",
                assetId.toString(),
                PermissionAuditDecision.DENY,
                deniedReason(actor, resolved, security, sourceAcl),
                requestId,
                null,
                sourceAcl));
        throw new KnowledgeAssetNotFoundException();
    }

    private String deniedReason(
            CurrentActor actor,
            ResolvedSubject resolved,
            KnowledgeAssetSecurityRow security,
            SourceAclEvaluation sourceAcl) {
        if (security == null) {
            return "ASSET_ABSENT";
        }
        if (!"ACTIVE".equals(security.getAssetStatus())
                || !"NORMALIZED".equals(security.getRawStatus())
                || !"PROMOTED".equals(security.getNormalizedStatus())) {
            return "LIFECYCLE_DENY";
        }
        PermissionDecision decision = permissionPolicy.evaluate(new KnowledgeAccessRequest(
                resolved.subject(),
                new KnowledgeResource(
                        actor.organizationId().toString(),
                        security.getId().toString(),
                        security.getDepartmentId() == null ? null : security.getDepartmentId().toString(),
                        KnowledgeClassification.valueOf(security.getClassification()),
                        DeclaredAccessScope.valueOf(security.getDeclaredAccess())),
                sourceAcl == null ? AccessGate.UNKNOWN : sourceAcl.effectiveGate(),
                AccessGate.valueOf(security.getOrgMemoryGate())));
        return decision.allowed() ? "SECURITY_STATE_CHANGED" : decision.reason().name();
    }

    private static KnowledgeRole knowledgeRole(UserRole role) {
        return switch (role) {
            case EMPLOYEE -> KnowledgeRole.EMPLOYEE;
            case TEAM_LEAD, MANAGER -> KnowledgeRole.MANAGER;
            case DIRECTOR -> KnowledgeRole.DIRECTOR;
            case EXECUTIVE -> KnowledgeRole.EXECUTIVE;
            case ADMIN -> null;
        };
    }

    private void requireReader(
            CurrentActor actor,
            String requestId,
            String query,
            String operation,
            String resourceId) {
        if (authorization.authorize(
                actor.organizationId(),
                actor.principal(),
                CAN_SEARCH_KNOWLEDGE,
                ResourceRef.of(actor.organizationId(), "organization", actor.organizationId())).allowed()) {
            return;
        }
        audit.record(auditCommand(
                actor,
                operation,
                "KNOWLEDGE_ACCESS",
                resourceId,
                PermissionAuditDecision.DENY,
                "OPENFGA_SEARCH_DENIED",
                requestId,
                query));
        throw new OrgMemoryAccessDeniedException("The current user does not have permission for this operation");
    }

    private static int validateLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("q must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        return normalized;
    }

    private static String searchPattern(String query) {
        if (query == null) {
            return null;
        }
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String requestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = requestId.trim();
        return normalized.length() <= MAX_REQUEST_ID_LENGTH
                ? normalized
                : UUID.randomUUID().toString();
    }

    private static PermissionAuditCommand auditCommand(
            CurrentActor actor,
            String operation,
            String resourceType,
            String resourceId,
            PermissionAuditDecision decision,
            String reason,
            String requestId,
            String query) {
        return auditCommand(
                actor,
                operation,
                resourceType,
                resourceId,
                decision,
                reason,
                requestId,
                query,
                null);
    }

    private static PermissionAuditCommand auditCommand(
            CurrentActor actor,
            String operation,
            String resourceType,
            String resourceId,
            PermissionAuditDecision decision,
            String reason,
            String requestId,
            String query,
            SourceAclEvaluation sourceAcl) {
        return new PermissionAuditCommand(
                actor.organizationId(),
                actor.userId(),
                operation,
                resourceType,
                resourceId,
                decision,
                reason,
                KnowledgePermissionPolicy.POLICY_VERSION,
                requestId,
                query,
                sourceAcl == null ? null : sourceAcl.ingestionSnapshotId(),
                sourceAcl == null ? null : sourceAcl.currentSnapshotId());
    }

    private record ResolvedSubject(KnowledgeSubject subject, KnowledgeRole role, UUID departmentId) {
    }

    private record VerifiedAsset(PermissionDecision decision, SourceAclEvaluation sourceAcl) {
    }
}
