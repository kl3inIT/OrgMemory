package com.orgmemory.mcp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;

@Configuration(proxyBeanMethods = false)
class McpDownstreamOAuthConfiguration {

    @Bean
    OAuth2AuthorizedClientManager mcpAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            McpGatewayProperties properties) {
        var responseClient =
                new RestClientTokenExchangeTokenResponseClient();
        responseClient.addParametersConverter(request -> {
            var parameters =
                    new LinkedMultiValueMap<String, String>();
            parameters.set(
                    OAuth2ParameterNames.SUBJECT_TOKEN_TYPE,
                    "urn:ietf:params:oauth:token-type:access_token");
            parameters.set(
                    OAuth2ParameterNames.AUDIENCE,
                    properties.apiAudience());
            return parameters;
        });

        var tokenExchange =
                new TokenExchangeOAuth2AuthorizedClientProvider();
        tokenExchange.setAccessTokenResponseClient(responseClient);
        tokenExchange.setSubjectTokenResolver(context -> {
            Object principal = context.getPrincipal().getPrincipal();
            return principal instanceof OAuth2Token token ? token : null;
        });

        OAuth2AuthorizedClientProvider provider = tokenExchange;
        var manager =
                new DefaultOAuth2AuthorizedClientManager(
                        registrations,
                        new NonPersistingAuthorizedClientRepository());
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    /**
     * Exchanged tokens are bound to the exact inbound subject token. Reusing
     * one by principal name could carry stale grants into a later MCP request.
     */
    static final class NonPersistingAuthorizedClientRepository
            implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                String clientRegistrationId,
                Authentication principal,
                HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient authorizedClient,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {
            // Intentionally exchange again for every MCP request.
        }

        @Override
        public void removeAuthorizedClient(
                String clientRegistrationId,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {
            // Nothing is persisted.
        }
    }
}
