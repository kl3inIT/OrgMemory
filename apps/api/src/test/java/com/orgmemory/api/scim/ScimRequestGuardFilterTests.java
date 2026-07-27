package com.orgmemory.api.scim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;

class ScimRequestGuardFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInsecureRequestsBeforeAuthentication() throws Exception {
        var filter = new ScimRequestGuardFilter(properties(true, 256, 120));
        var request = new MockHttpServletRequest("GET", "/scim/v2/ServiceProviderConfig");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertEquals(403, response.getStatus());
        assertFalse(invoked.get());
        assertTrue(response.getHeader("X-Request-ID").matches("[0-9a-f-]{36}"));
    }

    @Test
    void enforcesTheActualChunkedBodySizeAndReplaysAllowedBodies() throws Exception {
        byte[] oversized = "12345".getBytes();
        var filter = new ScimRequestGuardFilter(properties(false, 4, 120));
        var request = unknownLengthPost(oversized);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Oversized request must not reach the chain");
        });

        assertEquals(413, response.getStatus());

        byte[] allowed = "1234".getBytes();
        var allowedRequest = unknownLengthPost(allowed);
        var allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowedRequest, allowedResponse, (guarded, ignoredResponse) ->
                assertArrayEquals(
                        allowed,
                        guarded.getInputStream().readAllBytes()));
    }

    @Test
    void replacesAnInvalidInboundRequestId() throws Exception {
        var filter = new ScimRequestGuardFilter(properties(false, 256, 120));
        var request = new MockHttpServletRequest("GET", "/scim/v2/ServiceProviderConfig");
        request.addHeader("X-Request-ID", "invalid request id\r\n");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertTrue(response.getHeader("X-Request-ID").matches("[0-9a-f-]{36}"));
    }

    @Test
    void rateLimitsAuthenticationFailuresAndAuthenticatedConnections() throws Exception {
        var limiter = new ScimRequestRateLimiter(1, Clock.systemUTC());
        var failedRequest = new MockHttpServletRequest();
        failedRequest.setRemoteAddr("192.0.2.10");
        failedRequest.addHeader(
                HttpHeaders.AUTHORIZATION,
                "Bearer omscim_abcdefghijklmnop.invalid");
        assertTrue(limiter.consumeAuthenticationFailure(failedRequest));
        assertFalse(limiter.consumeAuthenticationFailure(failedRequest));

        UUID connectionId = UUID.randomUUID();
        var machine = new ScimMachinePrincipal(
                UUID.randomUUID(),
                connectionId,
                UUID.randomUUID(),
                "abcdefghijklmnop",
                com.orgmemory.core.identityprovisioning.ProvisioningOperationalState.DISABLED);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        machine, null, java.util.List.of()));
        var filter = new ScimConnectionRateLimitFilter(limiter);
        var firstResponse = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        filter.doFilter(
                new MockHttpServletRequest(),
                firstResponse,
                (ignoredRequest, ignoredResponse) -> invoked.set(true));
        assertTrue(invoked.get());

        var limitedResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest(),
                limitedResponse,
                (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("Rate-limited request must not reach the chain");
                });
        assertEquals(429, limitedResponse.getStatus());
        assertEquals("60", limitedResponse.getHeader(HttpHeaders.RETRY_AFTER));
    }

    private static MockHttpServletRequest unknownLengthPost(byte[] content) {
        var request = new MockHttpServletRequest("POST", "/scim/v2/Users") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(content);
        request.addHeader("Transfer-Encoding", "chunked");
        return request;
    }

    private static ScimSecurityProperties properties(
            boolean requireTls, long maximumBytes, int requestsPerMinute) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        return new ScimSecurityProperties(
                Base64.getUrlEncoder().withoutPadding().encodeToString(key),
                1,
                "",
                requireTls,
                DataSize.ofBytes(maximumBytes),
                requestsPerMinute,
                Duration.ofDays(90),
                Duration.ofMinutes(15));
    }
}
