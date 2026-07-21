package com.orgmemory.api.security;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
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

    OidcCurrentActorProvider(ExternalIdentityRepository identities, AppUserRepository users) {
        this.identities = identities;
        this.users = users;
    }

    @Override
    @Transactional
    public CurrentActor current(Authentication authentication) {
        ExternalSubject external = externalSubject(authentication);
        AppUser user = identities.findByIssuerAndSubject(external.issuer(), external.subject())
                .map(identity -> findUser(identity.getAppUserId()))
                .orElseThrow(() -> new OrgMemoryAccessDeniedException(
                        "The OIDC identity is not linked to an OrgMemory user"));
        if (!user.isActive()) {
            throw new OrgMemoryAccessDeniedException("The linked OrgMemory user is inactive");
        }
        return new CurrentActor(
                user.getId(),
                user.getOrganizationId(),
                user.getDepartmentId(),
                user.getName(),
                user.getEmail());
    }

    private static ExternalSubject externalSubject(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return externalSubject(token.getToken().getIssuer(), token.getToken().getSubject());
        }
        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser user) {
            return externalSubject(user.getIdToken().getIssuer(), user.getIdToken().getSubject());
        }
        throw new OrgMemoryAccessDeniedException("An OIDC identity is required");
    }

    private static ExternalSubject externalSubject(URL issuerUrl, String subject) {
        if (issuerUrl == null || !StringUtils.hasText(subject)) {
            throw new OrgMemoryAccessDeniedException("OIDC issuer and subject are required");
        }
        return new ExternalSubject(issuerUrl.toString(), subject);
    }

    private AppUser findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new OrgMemoryAccessDeniedException("The linked OrgMemory user no longer exists"));
    }

    private record ExternalSubject(String issuer, String subject) {
    }
}
