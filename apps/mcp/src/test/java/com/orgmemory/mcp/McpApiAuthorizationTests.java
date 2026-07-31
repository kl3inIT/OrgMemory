package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;

class McpApiAuthorizationTests {

    private final OAuth2AuthorizedClientManager manager =
            mock(OAuth2AuthorizedClientManager.class);
    private final McpApiAuthorization authorization =
            new McpApiAuthorization(manager);

    @Test
    void returnsOnlyTheExchangedApiAudienceToken() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        var incoming = Jwt.withTokenValue("mcp-audience-token")
                .header("alg", "RS256")
                .subject("actor-1")
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();
        var authentication = new JwtAuthenticationToken(
                incoming,
                List.of(new SimpleGrantedAuthority(
                        "SCOPE_assets:read")));
        var context = McpTransportContext.create(Map.of(
                McpTransportConfiguration.AUTHENTICATION_CONTEXT_KEY,
                authentication));
        var client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "api-audience-token",
                now,
                now.plusSeconds(60)));
        when(manager.authorize(any())).thenReturn(client);

        assertEquals(
                "Bearer api-audience-token",
                authorization.require(context));
    }

    @Test
    void refusesMissingAuthenticatedContext() {
        assertThrows(
                McpGatewayException.class,
                () -> authorization.require(McpTransportContext.EMPTY));
    }

    @Test
    void hidesTokenEndpointFailureDetails() {
        McpTransportContext context = authenticatedContext();
        when(manager.authorize(any())).thenThrow(
                new OAuth2AuthorizationException(new OAuth2Error(
                        "invalid_grant",
                        "private issuer response",
                        null)));

        var failure = assertThrows(
                McpGatewayException.class,
                () -> authorization.require(context));

        assertEquals(
                "OrgMemory could not authorize the downstream Asset request",
                failure.getMessage());
    }

    @Test
    void publicationUsesTheWriteScopedDownstreamRegistration() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        var client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "publisher-token",
                now,
                now.plusSeconds(60)));
        when(manager.authorize(any())).thenReturn(client);
        var authentication = (org.springframework.security.core.Authentication)
                authenticatedContext().get(
                        McpTransportConfiguration.AUTHENTICATION_CONTEXT_KEY);

        assertEquals(
                "Bearer publisher-token",
                authorization.requirePublisher(authentication));

        var request = org.mockito.ArgumentCaptor.forClass(
                OAuth2AuthorizeRequest.class);
        verify(manager).authorize(request.capture());
        assertEquals(
                McpApiAuthorization.PUBLISHER_CLIENT_REGISTRATION_ID,
                request.getValue().getClientRegistrationId());
    }

    @Test
    void exchangedAuthorizedClientsAreNeverPersistedByPrincipalName() {
        var repository =
                new McpDownstreamOAuthConfiguration
                        .NonPersistingAuthorizedClientRepository();
        var principal = authenticatedContext().get(
                McpTransportConfiguration.AUTHENTICATION_CONTEXT_KEY);

        assertNull(repository.loadAuthorizedClient(
                "orgmemory-api",
                (org.springframework.security.core.Authentication) principal,
                new MockHttpServletRequest()));
    }

    @Test
    void keycloakTokenExchangeUsesOneAccessTokenTypeAndTheApiAudience() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add(
                OAuth2ParameterNames.SUBJECT_TOKEN_TYPE,
                "urn:ietf:params:oauth:token-type:jwt");

        McpDownstreamOAuthConfiguration.customizeTokenExchangeParameters(
                parameters,
                "orgmemory-web");

        assertEquals(
                List.of("urn:ietf:params:oauth:token-type:access_token"),
                parameters.get(OAuth2ParameterNames.SUBJECT_TOKEN_TYPE));
        assertEquals(
                List.of("orgmemory-web"),
                parameters.get(OAuth2ParameterNames.AUDIENCE));
    }

    private static McpTransportContext authenticatedContext() {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        var incoming = Jwt.withTokenValue("mcp-audience-token")
                .header("alg", "RS256")
                .subject("actor-1")
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();
        var authentication = new JwtAuthenticationToken(
                incoming,
                List.of(new SimpleGrantedAuthority(
                        "SCOPE_assets:read")));
        return McpTransportContext.create(Map.of(
                McpTransportConfiguration.AUTHENTICATION_CONTEXT_KEY,
                authentication));
    }
}
