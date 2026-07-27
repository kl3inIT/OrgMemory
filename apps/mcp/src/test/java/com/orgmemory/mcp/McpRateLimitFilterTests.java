package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class McpRateLimitFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsEachSubjectAndOAuthClientWithoutLeakingTheToken()
            throws Exception {
        authenticate("actor-1", "claude-code", "secret-token");
        var filter = filter(properties(100, 2, 1_000));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(100), new MockHttpServletResponse(), chain);
        filter.doFilter(request(100), new MockHttpServletResponse(), chain);
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(request(100), refused, chain);

        verify(chain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals(429, refused.getStatus());
        assertTrue(
                Long.parseLong(refused.getHeader("Retry-After")) >= 1);
        assertTrue(refused.getContentAsString()
                .contains("mcp.caller-rate-limited"));
        assertFalse(refused.getContentAsString()
                .contains("secret-token"));
    }

    @Test
    void sameSubjectThroughDifferentClientsGetsIndependentBuckets()
            throws Exception {
        var filter = filter(properties(100, 1, 1_000));
        FilterChain chain = mock(FilterChain.class);

        authenticate("actor-1", "claude-code", "token-a");
        filter.doFilter(request(100), new MockHttpServletResponse(), chain);
        authenticate("actor-1", "claude-web", "token-b");
        filter.doFilter(request(100), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals(2, filter.trackedCallerCount());
    }

    @Test
    void globalLimitTripsAcrossIndependentCallers() throws Exception {
        var filter = filter(properties(2, 10, 1_000));
        FilterChain chain = mock(FilterChain.class);

        authenticate("actor-1", "claude-code", "token-a");
        filter.doFilter(request(100), new MockHttpServletResponse(), chain);
        authenticate("actor-2", "claude-code", "token-b");
        filter.doFilter(request(100), new MockHttpServletResponse(), chain);
        authenticate("actor-3", "claude-code", "token-c");
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(request(100), refused, chain);

        assertEquals(429, refused.getStatus());
        assertTrue(refused.getContentAsString()
                .contains("mcp.global-rate-limited"));
    }

    @Test
    void rejectsKnownOversizedBodiesBeforeToolDispatch() throws Exception {
        authenticate("actor-1", "claude-code", "token");
        var filter = filter(properties(100, 100, 64));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse refused = new MockHttpServletResponse();

        filter.doFilter(request(65), refused, chain);

        assertEquals(413, refused.getStatus());
        assertTrue(refused.getContentAsString()
                .contains("mcp.request-too-large"));
        verify(chain, times(0)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsChunkedOversizedBodiesWhileTheyAreRead() throws Exception {
        authenticate("actor-1", "claude-code", "token");
        var filter = filter(properties(100, 100, 64));
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setRequestURI("/mcp");
        request.setContent(new byte[65]);
        MockHttpServletResponse refused = new MockHttpServletResponse();

        filter.doFilter(
                request,
                refused,
                (limitedRequest, response) ->
                        limitedRequest.getInputStream().readAllBytes());

        assertEquals(413, refused.getStatus());
        assertTrue(refused.getContentAsString()
                .contains("mcp.request-too-large"));
    }

    @Test
    void givesSkillPublicationItsOwnBoundedMultipartBudget()
            throws Exception {
        authenticate("actor-1", "orgmemory-cli", "token");
        var filter = filter(properties(100, 100, 64));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest accepted = request(65);
        accepted.setRequestURI("/skill-publications");

        filter.doFilter(
                accepted,
                new MockHttpServletResponse(),
                chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unauthenticatedRequestsConsumeTheGlobalBudget()
            throws Exception {
        var filter = filter(properties(1, 100, 1_000));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request(100),
                new MockHttpServletResponse(),
                chain);
        MockHttpServletResponse refused =
                new MockHttpServletResponse();
        filter.doFilter(request(100), refused, chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertEquals(429, refused.getStatus());
        assertTrue(refused.getContentAsString()
                .contains("mcp.global-rate-limited"));
    }

    @Test
    void nonMcpPathsBypassTheLimiter() throws Exception {
        var filter = filter(properties(1, 1, 1));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest health = request(100);
        health.setRequestURI("/actuator/health");

        filter.doFilter(
                health,
                new MockHttpServletResponse(),
                chain);
        filter.doFilter(
                health,
                new MockHttpServletResponse(),
                chain);

        verify(chain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static McpRateLimitFilter filter(
            McpRateLimitProperties properties) {
        return new McpRateLimitFilter(
                properties, JsonMapper.builder().build());
    }

    private static McpRateLimitProperties properties(
            long globalCapacity,
            long callerCapacity,
            long maxBodyBytes) {
        return new McpRateLimitProperties(
                true,
                globalCapacity,
                Duration.ofMinutes(1),
                callerCapacity,
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                100,
                maxBodyBytes,
                maxBodyBytes * 2);
    }

    private static void authenticate(
            String subject,
            String client,
            String tokenValue) {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .claim("azp", client)
                .audience(List.of("https://mcp.example.test/mcp"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .claims(claims -> claims.putAll(
                        Map.of("scope", "assets:read")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority(
                                "SCOPE_assets:read"))));
    }

    private static MockHttpServletRequest request(int contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/mcp");
        request.setContent(new byte[contentLength]);
        return request;
    }
}
