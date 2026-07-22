package com.orgmemory.api.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(OrgMemoryOidcProperties.class)
class SecurityConfig {

    private static final String REGISTRATION_ID = "keycloak";

    @Bean
    @Order(1)
    SecurityFilterChain bearerApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(bearerApiRequest())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain browserSecurityFilterChain(
            HttpSecurity http,
            OrgMemoryOidcProperties oidc,
            ClientRegistrationRepository registrations,
            BrowserLoginFlow loginFlow,
            LogoutSuccessHandler oidcLogoutSuccessHandler,
            Environment environment) throws Exception {
        RequestMatcher apiRequest = request -> request.getRequestURI().startsWith("/api/");
        http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/api/health", "/actuator/health").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("dev"))) {
                        authorize.requestMatchers(
                                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll();
                    } else {
                        authorize.requestMatchers(
                                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .denyAll();
                    }
                    authorize.requestMatchers("/oauth2/**", "/login/**", "/error").permitAll();
                    authorize.requestMatchers(
                                    "/api/session", "/api/session/csrf", "/api/session/login")
                            .permitAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(loginFlow::onAuthenticationSuccess)
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(oidc.webLocation("/login?error=authentication_failed"))))
                .logout(logout -> logout
                        .logoutUrl("/api/session/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler)
                        .deleteCookies("ORGMEMORY_SESSION"))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required"),
                                apiRequest)
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Access denied")))
                .headers(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    LogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository registrations,
            OrgMemoryOidcProperties oidc) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(registrations);
        handler.setPostLogoutRedirectUri(oidc.webLocation("/login"));
        return handler;
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OrgMemoryOidcProperties oidc) {
        ClientRegistration keycloak = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(oidc.clientId())
                .clientSecret(oidc.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(oidc.endpoint("/protocol/openid-connect/auth"))
                .tokenUri(oidc.endpoint("/protocol/openid-connect/token"))
                .userInfoUri(oidc.endpoint("/protocol/openid-connect/userinfo"))
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri(oidc.endpoint("/protocol/openid-connect/certs"))
                .issuerUri(oidc.issuerUri().toString())
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", oidc.endpoint("/protocol/openid-connect/logout")))
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .clientName("OrgMemory")
                .build();
        return new InMemoryClientRegistrationRepository(keycloak);
    }

    private static RequestMatcher bearerApiRequest() {
        return request -> {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            return request.getRequestURI().startsWith("/api/")
                    && authorization != null
                    && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
        };
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}
                """.formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
