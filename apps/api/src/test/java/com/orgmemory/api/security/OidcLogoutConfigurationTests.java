package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.util.UriComponentsBuilder;

class OidcLogoutConfigurationTests {

    @Test
    void providerLogoutUsesTheExactRegisteredWebRedirect() throws Exception {
        var oidc = new OrgMemoryOidcProperties(
                URI.create("http://localhost:8180/realms/orgmemory"),
                "orgmemory-web",
                "test-secret",
                URI.create("http://localhost:5173"));
        var security = new SecurityConfig();
        var registrations = security.clientRegistrationRepository(oidc);
        var handler = security.oidcLogoutSuccessHandler(registrations, oidc);
        Instant issuedAt = Instant.now();
        var idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("sub", "test-user"));
        var user = new DefaultOidcUser(java.util.List.of(), idToken);
        var authentication = new OAuth2AuthenticationToken(
                user, user.getAuthorities(), "keycloak");
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        var response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request, response, authentication);

        var redirect = UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build();
        assertEquals("http://localhost:5173/login", redirect.getQueryParams().getFirst("post_logout_redirect_uri"));
    }
}
