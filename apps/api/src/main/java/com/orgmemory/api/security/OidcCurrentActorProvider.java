package com.orgmemory.api.security;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserProvisioningService;
import java.net.URL;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
class OidcCurrentActorProvider implements CurrentActorProvider {

    private final ExternalIdentityRepository identities;
    private final AppUserRepository users;
    private final UserProvisioningService provisioning;

    OidcCurrentActorProvider(
            ExternalIdentityRepository identities,
            AppUserRepository users,
            UserProvisioningService provisioning) {
        this.identities = identities;
        this.users = users;
        this.provisioning = provisioning;
    }

    @Override
    @Transactional
    public CurrentActor current(Authentication authentication) {
        ExternalSubject external = externalSubject(authentication);
        // An unlinked identity may be a pre-provisioned directory user or an
        // invited unmanaged user. Verified email is used once to choose exactly
        // one actor; every later sign-in resolves only the issuer/subject binding.
        AppUser user = identities.findByIssuerAndSubject(external.issuer(), external.subject())
                .map(identity -> findUser(identity.getAppUserId()))
                .orElseGet(() -> provisionVerifiedSignIn(external));
        if (!user.isActive()) {
            throw new OrgMemoryAccessDeniedException("The linked OrgMemory user is inactive");
        }
        return new CurrentActor(
                user.getId(),
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getName(),
                user.getEmail(),
                user.getClearance());
    }

    private static ExternalSubject externalSubject(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return externalSubject(
                    token.getToken().getIssuer(),
                    token.getToken().getSubject(),
                    token.getToken().getClaimAsString("email"),
                    Boolean.TRUE.equals(token.getToken()
                            .getClaimAsBoolean("email_verified")));
        }
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser user) {
            return externalSubject(
                    user.getIdToken().getIssuer(),
                    user.getIdToken().getSubject(),
                    user.getEmail(),
                    Boolean.TRUE.equals(user.getEmailVerified()));
        }
        throw new OrgMemoryAccessDeniedException("An OIDC identity is required");
    }

    private static ExternalSubject externalSubject(
            URL issuerUrl,
            String subject,
            String email,
            boolean emailVerified) {
        if (issuerUrl == null || !StringUtils.hasText(subject)) {
            throw new OrgMemoryAccessDeniedException("OIDC issuer and subject are required");
        }
        return new ExternalSubject(
                issuerUrl.toString(), subject, email, emailVerified);
    }

    private AppUser provisionVerifiedSignIn(ExternalSubject external) {
        if (!external.emailVerified() || !StringUtils.hasText(external.email())) {
            throw new OrgMemoryAccessDeniedException(
                    "A verified OIDC email is required for first sign-in");
        }
        return provisioning
                .provisionForVerifiedSignIn(
                        external.issuer(),
                        external.subject(),
                        external.email())
                .orElseThrow(() -> new OrgMemoryAccessDeniedException(
                        "The OIDC identity is not linked to an OrgMemory user"));
    }

    private AppUser findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new OrgMemoryAccessDeniedException("The linked OrgMemory user no longer exists"));
    }

    /** The address is only used for first-sign-in matching; the identity is the subject. */
    private record ExternalSubject(
            String issuer,
            String subject,
            String email,
            boolean emailVerified) {
    }
}
