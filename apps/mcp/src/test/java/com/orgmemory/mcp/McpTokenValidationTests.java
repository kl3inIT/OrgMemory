package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenError;

class McpTokenValidationTests {

    @Test
    void acceptsOnlyUnexpiredIssuerBoundTokensWithTheMcpAudience() {
        var validator = McpSecurityConfiguration.tokenValidator(
                AssetDeliveryApiClientTests.properties());
        Instant now = Instant.now();

        assertFalse(validator.validate(jwt(
                        now.minusSeconds(30),
                        now.plusSeconds(300),
                        "https://id.example.test/realms/orgmemory",
                        List.of("https://mcp.example.test/mcp", "orgmemory-web")))
                .hasErrors());
        assertTrue(validator.validate(jwt(
                        now.minusSeconds(30),
                        now.plusSeconds(300),
                        "https://id.example.test/realms/orgmemory",
                        List.of("orgmemory-web")))
                .hasErrors());
        assertTrue(validator.validate(jwt(
                        now.minusSeconds(600),
                        now.minusSeconds(300),
                        "https://id.example.test/realms/orgmemory",
                        List.of("https://mcp.example.test/mcp")))
                .hasErrors());
        assertTrue(validator.validate(jwt(
                        now.minusSeconds(30),
                        now.plusSeconds(300),
                        "https://wrong-issuer.example.test",
                        List.of("https://mcp.example.test/mcp")))
                .hasErrors());
    }

    @Test
    void preservesBearerFailureDetailsAndAddsResourceMetadata()
            throws Exception {
        var response = new MockHttpServletResponse();
        var error = new BearerTokenError(
                "invalid_token",
                HttpStatus.UNAUTHORIZED,
                "The token is invalid",
                null);

        McpSecurityConfiguration.authenticationEntryPoint(
                        URI.create(
                                "https://mcp.example.test/.well-known/oauth-protected-resource/mcp"))
                .commence(
                        new MockHttpServletRequest(),
                        response,
                        new OAuth2AuthenticationException(error));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        String challenge = response.getHeader("WWW-Authenticate");
        assertTrue(challenge.contains("error=\"invalid_token\""));
        assertTrue(challenge.contains(
                "error_description=\"The token is invalid\""));
        assertTrue(challenge.contains(
                "resource_metadata=\"https://mcp.example.test/.well-known/oauth-protected-resource/mcp\""));
    }

    private static Jwt jwt(
            Instant issuedAt,
            Instant expiresAt,
            String issuer,
            List<String> audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("actor-1")
                .issuer(issuer)
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
