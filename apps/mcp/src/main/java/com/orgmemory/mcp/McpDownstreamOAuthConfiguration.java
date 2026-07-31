package com.orgmemory.mcp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;

@Configuration(proxyBeanMethods = false)
class McpDownstreamOAuthConfiguration {

    private static final String ACCESS_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:access_token";

    @Bean
    OAuth2AuthorizedClientManager mcpAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            McpGatewayProperties properties) {
        var responseClient =
                new RestClientTokenExchangeTokenResponseClient();
        responseClient.setParametersCustomizer(parameters ->
                customizeTokenExchangeParameters(
                        parameters,
                        properties.apiAudience()));

        var tokenExchange =
                new TokenExchangeOAuth2AuthorizedClientProvider();
        tokenExchange.setAccessTokenResponseClient(responseClient);
        tokenExchange.setSubjectTokenResolver(context -> {
            Object principal = context.getPrincipal().getPrincipal();
            return principal instanceof OAuth2Token token ? token : null;
        });

        var manager =
                new DefaultOAuth2AuthorizedClientManager(
                        registrations,
                        new NonPersistingAuthorizedClientRepository());
        manager.setAuthorizedClientProvider(tokenExchange);
        return manager;
    }

    static void customizeTokenExchangeParameters(
            MultiValueMap<String, String> parameters,
            String audience) {
        /*
         * Spring identifies Jwt as the RFC 8693 "jwt" token type by default,
         * while Keycloak's standard exchange accepts its access-token JWT as
         * an "access_token". set() also replaces that default value. Using an
         * additional parameters converter would append a second
         * subject_token_type and Keycloak rejects the ambiguous request.
         */
        parameters.set(
                OAuth2ParameterNames.SUBJECT_TOKEN_TYPE,
                ACCESS_TOKEN_TYPE);
        parameters.set(OAuth2ParameterNames.AUDIENCE, audience);
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
