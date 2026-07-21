package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OidcCurrentActorProviderTests {

    private static final String ISSUER = "https://identity.example.test/realms/acme";

    @Test
    void resolvesOnlyTheExplicitIssuerSubjectBindingAndIgnoresJwtRolesAndEmail() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = linkedUser(identities, users, "stable-subject", true);

        var actor = new OidcCurrentActorProvider(identities, users).current(jwt(
                "stable-subject", "attacker@example.test", "ROLE_ADMIN"));

        assertEquals(user.getId(), actor.userId());
        assertEquals(user.getOrganizationId(), actor.organizationId());
        assertEquals("laura@acme.test", actor.email());
    }

    @Test
    void resolvesTheSameBindingForAnOidcBrowserSession() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = linkedUser(identities, users, "stable-subject", true);

        var actor = new OidcCurrentActorProvider(identities, users).current(oidcSession(
                "stable-subject", "attacker@example.test", "ROLE_ADMIN"));

        assertEquals(user.getId(), actor.userId());
        assertEquals(user.getOrganizationId(), actor.organizationId());
        assertEquals("laura@acme.test", actor.email());
    }

    @Test
    void rejectsVerifiedEmailAndAdminRoleWhenNoExplicitBindingExists() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(identities.findByIssuerAndSubject(ISSUER, "unknown-subject")).thenReturn(Optional.empty());

        var provider = new OidcCurrentActorProvider(identities, users);
        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> provider.current(oidcSession(
                        "unknown-subject", "known-user@acme.test", "ROLE_ADMIN")));
    }

    @Test
    void rejectsAnInactiveLinkedUser() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        linkedUser(identities, users, "former-subject", false);

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> new OidcCurrentActorProvider(identities, users).current(jwt(
                        "former-subject", "former@acme.test", "ROLE_ADMIN")));
    }

    private static AppUser linkedUser(
            ExternalIdentityRepository identities,
            AppUserRepository users,
            String subject,
            boolean active) {
        AppUser user = new AppUser(
                UUID.randomUUID(),
                active ? UUID.randomUUID() : null,
                "Laura",
                "laura@acme.test",
                active ? UserRole.MANAGER : UserRole.EMPLOYEE,
                active);
        ExternalIdentity identity = new ExternalIdentity(user.getId(), ISSUER, subject);
        when(identities.findByIssuerAndSubject(ISSUER, subject)).thenReturn(Optional.of(identity));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static JwtAuthenticationToken jwt(String subject, String email, String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", true)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private static OAuth2AuthenticationToken oidcSession(String subject, String email, String authority) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", ISSUER,
                        "sub", subject,
                        "email", email,
                        "email_verified", true));
        var authorities = List.of(new SimpleGrantedAuthority(authority));
        DefaultOidcUser user = new DefaultOidcUser(authorities, idToken);
        return new OAuth2AuthenticationToken(user, authorities, "keycloak");
    }
}
