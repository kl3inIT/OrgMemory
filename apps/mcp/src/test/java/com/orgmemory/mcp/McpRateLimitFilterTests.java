package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class McpRateLimitFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsEachAuthenticatedSubjectWithoutLeakingTheToken() throws Exception {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("secret-token")
                .header("alg", "RS256")
                .subject("actor-1")
                .audience(List.of("https://mcp.example.test/mcp"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority(
                                "SCOPE_assets:read"))));
        var filter = new McpRateLimitFilter(
                2, Clock.fixed(now, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(), new MockHttpServletResponse(), chain);
        filter.doFilter(request(), new MockHttpServletResponse(), chain);
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(request(), refused, chain);

        verify(chain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals(429, refused.getStatus());
        assertEquals("60", refused.getHeader("Retry-After"));
        assertEquals(false, refused.getContentAsString().contains("secret-token"));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/mcp");
        return request;
    }
}
