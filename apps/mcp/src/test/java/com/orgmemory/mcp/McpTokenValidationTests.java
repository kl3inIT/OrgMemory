package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

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
