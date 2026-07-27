package com.orgmemory.api.scim;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ScimConnectionRateLimitFilter extends OncePerRequestFilter {

    private final ScimRequestRateLimiter rateLimiter;

    ScimConnectionRateLimitFilter(ScimRequestRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof ScimMachinePrincipal machine
                && !rateLimiter.consumeConnection(machine.connectionId())) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            ScimErrorWriter.write(response, 429, "Request rate exceeded");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
