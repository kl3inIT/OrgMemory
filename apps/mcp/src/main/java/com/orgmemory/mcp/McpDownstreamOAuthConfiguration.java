package com.orgmemory.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;

@Configuration(proxyBeanMethods = false)
class McpDownstreamOAuthConfiguration {

    @Bean
    OAuth2AuthorizedClientManager mcpAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients,
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
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        registrations,
                        authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }
}
