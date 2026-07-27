package com.orgmemory.api.scim;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ScimRequestGuardFilter extends OncePerRequestFilter {

    private final ScimSecurityProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();

    ScimRequestGuardFilter(ScimSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        if (properties.requireTls() && !request.isSecure()) {
            ScimErrorWriter.write(response, 403, "TLS is required");
            return;
        }
        if (request.getContentLengthLong() > properties.maximumRequestSize().toBytes()) {
            ScimErrorWriter.write(response, 413, "Request body exceeds the configured limit");
            return;
        }

        String requestId = requestId(request.getHeader("X-Request-ID"));
        response.setHeader("X-Request-ID", requestId);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof ScimMachinePrincipal machine && !consume(machine.connectionId())) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            ScimErrorWriter.write(response, 429, "Request rate exceeded");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean consume(UUID connectionId) {
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Window current = windows.compute(connectionId, (ignored, previous) -> {
            if (previous == null || !previous.minute().equals(minute)) {
                return new Window(minute, new AtomicInteger(1));
            }
            previous.count().incrementAndGet();
            return previous;
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute().isBefore(minute));
        }
        return current.count().get() <= properties.requestsPerMinute();
    }

    private static String requestId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private record Window(Instant minute, AtomicInteger count) {
    }
}
