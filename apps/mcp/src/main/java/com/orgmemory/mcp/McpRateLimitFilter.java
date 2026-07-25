package com.orgmemory.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class McpRateLimitFilter extends OncePerRequestFilter {

    private final int requestsPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    McpRateLimitFilter(int requestsPerMinute) {
        this(requestsPerMinute, Clock.systemUTC());
    }

    McpRateLimitFilter(int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/mcp".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        var authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        long minute = clock.millis() / 60_000;
        Window result = windows.compute(
                jwt.getToken().getSubject(),
                (subject, current) -> current == null || current.minute() != minute
                        ? new Window(minute, 1)
                        : new Window(minute, current.count() + 1));
        if (result.count() > requestsPerMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"title\":\"Too Many Requests\",\"status\":429,"
                            + "\"detail\":\"MCP request rate exceeded\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(
                    entry -> entry.getValue().minute() < minute - 1);
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long minute, int count) {
    }
}
