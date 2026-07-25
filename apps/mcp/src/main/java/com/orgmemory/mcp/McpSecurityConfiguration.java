package com.orgmemory.mcp;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class McpSecurityConfiguration {

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http, McpGatewayProperties properties)
            throws Exception {
        URI metadataUri = protectedResourceMetadataUri(
                properties.resourceUri());
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/.well-known/oauth-protected-resource/**")
                        .permitAll()
                        .requestMatchers("/mcp").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, failure) -> {
                            response.setStatus(401);
                            response.setHeader(
                                    "WWW-Authenticate",
                                    "Bearer resource_metadata=\""
                                            + metadataUri
                                            + "\"");
                        })
                        .protectedResourceMetadata(metadata -> metadata
                                .protectedResourceMetadataCustomizer(builder -> builder
                                        .resource(properties.resourceUri().toString())
                                        .authorizationServer(properties
                                                .authorizationServer()
                                                .toString())
                                        .tlsClientCertificateBoundAccessTokens(false)
                                        .scopes(scopes -> scopes.addAll(
                                                java.util.List.of(
                                                        "assets:read",
                                                        "assets:use")))))
                        .jwt(Customizer.withDefaults()))
                .addFilterAfter(
                        new McpRateLimitFilter(
                                properties.rateLimitPerMinute()),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(McpGatewayProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        properties.jwkSetUri().toString())
                .build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(
            McpGatewayProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(
                        properties.authorizationServer().toString()),
                new JwtAudienceValidator(properties.audience()));
    }

    static URI protectedResourceMetadataUri(URI resourceUri) {
        String path = resourceUri.getPath();
        String metadataPath = "/.well-known/oauth-protected-resource"
                + (path == null ? "" : path);
        try {
            return new URI(
                    resourceUri.getScheme(),
                    resourceUri.getAuthority(),
                    metadataPath,
                    null,
                    null);
        } catch (java.net.URISyntaxException impossible) {
            throw new IllegalArgumentException(
                    "The MCP resource URI cannot form metadata discovery", impossible);
        }
    }
}
