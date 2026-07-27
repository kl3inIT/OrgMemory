package com.orgmemory.api.scim;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScimSecurityProperties.class)
class ScimSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain scimSecurityFilterChain(
            HttpSecurity http,
            ScimAuthenticationProvider authenticationProvider,
            ScimSecurityProperties properties)
            throws Exception {
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint)
                (request, response, failure) ->
                        ScimErrorWriter.write(response, 401, "Invalid or missing credential");
        var bearer = new BearerTokenAuthenticationFilter(
                new ProviderManager(authenticationProvider));
        bearer.setAuthenticationEntryPoint(entryPoint);

        http
                .securityMatcher("/scim/v2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/scim/v2/Users/**")
                        .hasAuthority("SCOPE_scim.users")
                        .requestMatchers("/scim/v2/Groups/**")
                        .hasAuthority("SCOPE_scim.groups")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, failure) ->
                                ScimErrorWriter.write(response, 403, "Insufficient scope")))
                .addFilterAt(bearer, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(
                        new ScimRequestGuardFilter(properties),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
