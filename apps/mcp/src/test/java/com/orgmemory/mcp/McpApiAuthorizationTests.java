package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
                AssetDeliveryApiClient.AssetDeliveryGatewayException.class,
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
                AssetDeliveryApiClient.AssetDeliveryGatewayException.class,
                () -> authorization.require(context));

        assertEquals(
                "OrgMemory could not authorize the downstream Asset request",
                failure.getMessage());
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
