package com.orgmemory.mcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Token-bucket rate limiting for authenticated MCP callers.
 *
 * <p>The caller bucket is consumed before the shared global bucket so a caller
 * already over its own allowance cannot drain the shared budget. Caffeine
 * bounds caller cardinality and expires idle buckets. This is deliberately
 * single-node for the POC; a clustered deployment must use a Bucket4j
 * distributed proxy manager.
 */
final class McpRateLimitFilter extends OncePerRequestFilter {

    private final McpRateLimitProperties properties;
    private final ObjectMapper json;
    private final Bucket global;
    private final Cache<String, Bucket> callers;

    McpRateLimitFilter(
            McpRateLimitProperties properties,
            ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.global = bucket(
                properties.globalCapacity(),
                properties.globalWindow());
        this.callers = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedCallers())
                .expireAfterAccess(properties.callerExpiry())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.enabled()
                || !(path.equals("/mcp")
                        || path.startsWith("/mcp/")
                        || path.equals("/skill-publications"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        long maximumBodyBytes = maximumBodyBytes(request);
        long length = request.getContentLengthLong();
        if (length > maximumBodyBytes) {
            writeProblem(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    requestCode(request),
                    requestDetail(request),
                    0);
            return;
        }

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()) {
            ConsumptionProbe globalProbe =
                    global.tryConsumeAndReturnRemaining(1);
            if (!globalProbe.isConsumed()) {
                reject(
                        response,
                        "mcp.global-rate-limited",
                        globalProbe,
                        properties.globalCapacity());
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        Bucket caller = callers.get(
                callerKey(jwt.getToken()),
                ignored -> bucket(
                        properties.callerCapacity(),
                        properties.callerWindow()));
        ConsumptionProbe callerProbe =
                caller.tryConsumeAndReturnRemaining(1);
        if (!callerProbe.isConsumed()) {
            reject(
                    response,
                    "mcp.caller-rate-limited",
                    callerProbe,
                    properties.callerCapacity());
            return;
        }

        ConsumptionProbe globalProbe =
                global.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            reject(
                    response,
                    "mcp.global-rate-limited",
                    globalProbe,
                    properties.globalCapacity());
            return;
        }

        response.setHeader(
                "RateLimit-Limit",
                Long.toString(properties.callerCapacity()));
        response.setHeader(
                "RateLimit-Remaining",
                Long.toString(callerProbe.getRemainingTokens()));
        try {
            filterChain.doFilter(
                    new LimitedBodyRequest(
                            request, maximumBodyBytes),
                    response);
        } catch (IOException | ServletException | RuntimeException failure) {
            if (!causedByBodyLimit(failure) || response.isCommitted()) {
                throw failure;
            }
            response.reset();
            writeProblem(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    requestCode(request),
                    requestDetail(request),
                    0);
        }
    }

    long trackedCallerCount() {
        callers.cleanUp();
        return callers.estimatedSize();
    }

    private static Bucket bucket(long capacity, Duration window) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, window))
                .build();
    }

    private static String callerKey(Jwt jwt) {
        String client = claim(jwt, "azp");
        if (client == null) {
            client = claim(jwt, "client_id");
        }
        if (client == null) {
            client = "unidentified-client";
        }
        return jwt.getSubject() + '\u001f' + client;
    }

    private long maximumBodyBytes(HttpServletRequest request) {
        return request.getRequestURI().equals("/skill-publications")
                ? properties.maxPublicationBodyBytes()
                : properties.maxBodyBytes();
    }

    private static String requestCode(HttpServletRequest request) {
        return request.getRequestURI().equals("/skill-publications")
                ? "skill.publication-request-too-large"
                : "mcp.request-too-large";
    }

    private static String requestDetail(HttpServletRequest request) {
        return request.getRequestURI().equals("/skill-publications")
                ? "Skill publication body exceeds the configured limit"
                : "MCP request body exceeds the configured limit";
    }

    private static String claim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private static boolean causedByBodyLimit(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RequestBodyTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void reject(
            HttpServletResponse response,
            String code,
            ConsumptionProbe probe,
            long limit)
            throws IOException {
        long retryAfter = Math.max(
                1,
                TimeUnit.NANOSECONDS.toSeconds(
                        probe.getNanosToWaitForRefill()
                                + TimeUnit.SECONDS.toNanos(1)
                                - 1));
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(retryAfter));
        response.setHeader("RateLimit-Limit", Long.toString(limit));
        response.setHeader("RateLimit-Remaining", "0");
        writeProblem(
                response,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                code,
                "MCP request rate exceeded",
                retryAfter);
    }

    private void writeProblem(
            HttpServletResponse response,
            int status,
            String code,
            String detail,
            long retryAfterSeconds)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        org.springframework.http.HttpStatusCode.valueOf(status),
                        detail);
        problem.setType(java.net.URI.create(
                "urn:orgmemory:problem:" + code));
        problem.setTitle(
                status == HttpStatus.TOO_MANY_REQUESTS.value()
                        ? "Too Many Requests"
                        : "Content Too Large");
        problem.setProperty("code", code);
        if (retryAfterSeconds > 0) {
            problem.setProperty(
                    "retryAfterSeconds", retryAfterSeconds);
        }
        json.writeValue(response.getOutputStream(), problem);
    }

    private static final class LimitedBodyRequest
            extends HttpServletRequestWrapper {

        private final long maximum;

        private LimitedBodyRequest(
                HttpServletRequest request, long maximum) {
            super(request);
            this.maximum = maximum;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(
                    super.getInputStream(), maximum);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedServletInputStream
            extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maximum;
        private long consumed;

        private LimitedServletInputStream(
                ServletInputStream delegate, long maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                consumed++;
                enforceLimit();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                consumed += read;
                enforceLimit();
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void enforceLimit() throws RequestBodyTooLargeException {
            if (consumed > maximum) {
                throw new RequestBodyTooLargeException();
            }
        }
    }

    private static final class RequestBodyTooLargeException
            extends IOException {
    }
}
