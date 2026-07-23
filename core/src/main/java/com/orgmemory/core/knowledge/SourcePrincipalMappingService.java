package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves external {@code SOURCE_USER} principals to verified internal users. Automatic
 * matching runs the trusted tiers first: an issuer/subject IdP join, then an email join that
 * needs the address vouched for either by the source itself or by an administrator's standing
 * decision on the connection; {@code selfClaim} and {@code adminConfirm} cover the tail
 * neither reaches. Every mutation validates
 * an active internal user, keeps at most one active mapping per principal, and appends a
 * permission audit event. Anything not matched here stays unmapped and therefore denied.
 */
@Service
class SourcePrincipalMappingService {

    static final String POLICY_VERSION = "external-principal-mapping-v1";

    private final SourcePrincipalMappingRepository mappings;
    private final AppUserRepository users;
    private final ExternalIdentityRepository identities;
    private final PermissionAuditService audit;

    SourcePrincipalMappingService(
            SourcePrincipalMappingRepository mappings,
            AppUserRepository users,
            ExternalIdentityRepository identities,
            PermissionAuditService audit) {
        this.mappings = mappings;
        this.users = users;
        this.identities = identities;
        this.audit = audit;
    }

    @Transactional
    Optional<SourcePrincipalMapping> autoMap(
            SourcePrincipal principal,
            String idpIssuer,
            String idpSubject,
            SourceIdentityTrust connectionTrust) {
        if (principal.getKind() != SourcePrincipalKind.SOURCE_USER) {
            return Optional.empty();
        }
        if (hasText(idpIssuer) && hasText(idpSubject)) {
            Optional<SourcePrincipalMapping> byIdp = identities.findByIssuerAndSubject(idpIssuer, idpSubject)
                    .map(ExternalIdentity::getAppUserId)
                    .filter(userId -> isActiveInOrg(userId, principal.getOrganizationId()))
                    .map(userId -> bind(
                            principal,
                            userId,
                            SourcePrincipalMappingMethod.IDP_JOIN,
                            "idp:" + idpIssuer + "|" + idpSubject));
            if (byIdp.isPresent()) {
                return byIdp;
            }
        }
        if (hasText(principal.getObservedEmail()) && emailIsVouchedFor(principal, connectionTrust)) {
            return users.findByEmailIgnoreCase(principal.getObservedEmail())
                    .filter(user -> user.isActive()
                            && user.getOrganizationId().equals(principal.getOrganizationId()))
                    .map(user -> bind(
                            principal,
                            user.getId(),
                            SourcePrincipalMappingMethod.SSO_EMAIL_JOIN,
                            emailJoinEvidence(principal)));
        }
        return Optional.empty();
    }

    /**
     * Whether an observed email may be believed well enough to bind on its own. Two
     * independent signals qualify. A source that confirms address ownership before an account
     * can exist vouches per principal, and its crawl says so. A source that cannot vouch needs
     * an administrator to attest the whole connection instead — a property of the workspace,
     * not of any one user, which is why the decision lives on the connection. Absent both, the
     * principal stays unmapped and therefore denied.
     */
    private static boolean emailIsVouchedFor(SourcePrincipal principal, SourceIdentityTrust connectionTrust) {
        return principal.isSsoVerified() || connectionTrust == SourceIdentityTrust.SSO_VERIFIED;
    }

    /** Records which of the two signals carried the bind, so the audit trail can be read back. */
    private static String emailJoinEvidence(SourcePrincipal principal) {
        return "sso-email:" + principal.getObservedEmail()
                + "|vouched-by:" + (principal.isSsoVerified() ? "source" : "connection-trust");
    }

    @Transactional
    SourcePrincipalMapping selfClaim(SourcePrincipal principal, UUID appUserId, String evidence) {
        return bind(principal, appUserId, SourcePrincipalMappingMethod.SELF_CLAIM, evidence);
    }

    @Transactional
    SourcePrincipalMapping adminConfirm(SourcePrincipal principal, UUID appUserId, String evidence) {
        return bind(principal, appUserId, SourcePrincipalMappingMethod.ADMIN_CONFIRMED, evidence);
    }

    @Transactional
    void revoke(UUID organizationId, UUID sourcePrincipalId) {
        mappings.findByOrganizationIdAndSourcePrincipalIdAndStatus(
                        organizationId, sourcePrincipalId, SourcePrincipalMappingStatus.ACTIVE)
                .ifPresent(mapping -> {
                    mapping.revoke(Instant.now());
                    mappings.save(mapping);
                    audit.record(mappingAudit(
                            organizationId,
                            mapping.getAppUserId(),
                            sourcePrincipalId,
                            "SOURCE_PRINCIPAL_UNMAP",
                            "MAPPING_REVOKED"));
                });
    }

    private SourcePrincipalMapping bind(
            SourcePrincipal principal,
            UUID appUserId,
            SourcePrincipalMappingMethod method,
            String evidence) {
        if (principal.getKind() != SourcePrincipalKind.SOURCE_USER) {
            throw new IllegalArgumentException("Only SOURCE_USER principals can map to an internal user");
        }
        if (!isActiveInOrg(appUserId, principal.getOrganizationId())) {
            throw new IllegalArgumentException("Cannot map to an inactive or unknown internal user");
        }
        Optional<SourcePrincipalMapping> active = mappings.findBySourcePrincipalIdAndStatus(
                principal.getId(), SourcePrincipalMappingStatus.ACTIVE);
        if (active.isPresent()) {
            if (active.get().getAppUserId().equals(appUserId)) {
                return active.get();
            }
            throw new IllegalStateException(
                    "Source principal already has a different active mapping; revoke it first");
        }
        SourcePrincipalMapping mapping = mappings.save(new SourcePrincipalMapping(
                UUID.randomUUID(),
                principal.getOrganizationId(),
                principal.getId(),
                appUserId,
                method,
                hasText(evidence) ? evidence : method.name(),
                Instant.now()));
        audit.record(mappingAudit(
                principal.getOrganizationId(),
                appUserId,
                principal.getId(),
                "SOURCE_PRINCIPAL_MAP",
                "MAPPING_CREATED_" + method.name()));
        return mapping;
    }

    private boolean isActiveInOrg(UUID appUserId, UUID organizationId) {
        return users.findById(appUserId)
                .map(user -> user.isActive() && user.getOrganizationId().equals(organizationId))
                .orElse(false);
    }

    private static PermissionAuditCommand mappingAudit(
            UUID organizationId,
            UUID actorUserId,
            UUID sourcePrincipalId,
            String operation,
            String reasonCode) {
        return new PermissionAuditCommand(
                organizationId,
                actorUserId,
                operation,
                "SOURCE_PRINCIPAL",
                sourcePrincipalId.toString(),
                PermissionAuditDecision.ALLOW,
                reasonCode,
                POLICY_VERSION,
                null,
                null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
