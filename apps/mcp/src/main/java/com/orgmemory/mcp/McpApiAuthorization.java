package com.orgmemory.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;

/**
 * Exchanges the MCP-audience user token for a short-lived API-audience token.
 *
 * <p>The inbound bearer is never forwarded to the downstream API. Each request
 * performs a new exchange because the resulting token is bound to that exact
 * inbound subject token and must not be reused by principal name.
 */
@Component
final class McpApiAuthorization {

    static final String CLIENT_REGISTRATION_ID = "orgmemory-api";
    static final String PUBLISHER_CLIENT_REGISTRATION_ID =
            "orgmemory-api-publisher";
    private static final Logger log =
            LoggerFactory.getLogger(McpApiAuthorization.class);

    private final OAuth2AuthorizedClientManager clients;

    McpApiAuthorization(OAuth2AuthorizedClientManager clients) {
        this.clients = clients;
    }

    String require(McpTransportContext context) {
        Object value = context.get(
                McpTransportConfiguration.AUTHENTICATION_CONTEXT_KEY);
        if (!(value instanceof Authentication authentication)) {
            throw new McpGatewayException(
                    "The MCP request has no authenticated identity");
        }
        return require(authentication);
    }

    String require(Authentication authentication) {
        return require(authentication, CLIENT_REGISTRATION_ID);
    }

    String requirePublisher(Authentication authentication) {
        return require(
                authentication, PUBLISHER_CLIENT_REGISTRATION_ID);
    }

    private String require(
            Authentication authentication,
            String clientRegistrationId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new McpGatewayException(
                    "The MCP request has no authenticated identity");
        }
        var request = OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(authentication)
                .build();
        var authorized = authorize(request);
        if (authorized == null) {
            throw new McpGatewayException(
                    "OrgMemory could not authorize the downstream Asset request");
        }
        return "Bearer " + authorized.getAccessToken().getTokenValue();
    }

    private org.springframework.security.oauth2.client.OAuth2AuthorizedClient
            authorize(OAuth2AuthorizeRequest request) {
        try {
            return clients.authorize(request);
        } catch (OAuth2AuthorizationException refused) {
            log.warn(
                    "MCP downstream token exchange refused with OAuth error {}",
                    refused.getError().getErrorCode());
            throw new McpGatewayException(
                    "OrgMemory could not authorize the downstream Asset request");
        }
    }
}
