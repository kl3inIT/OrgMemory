package com.orgmemory.api.scim;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Node-local defense in depth. Multi-replica deployments must additionally
 * enforce a global request limit at the trusted ingress or API gateway.
 */
final class ScimRequestRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int requestsPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Window> connectionWindows =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> authenticationFailureWindows =
            new ConcurrentHashMap<>();

    ScimRequestRateLimiter(ScimSecurityProperties properties) {
        this(properties.requestsPerMinute(), Clock.systemUTC());
    }

    ScimRequestRateLimiter(int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    boolean consumeConnection(UUID connectionId) {
        return consume(connectionWindows, connectionId);
    }

    boolean consumeAuthenticationFailure(HttpServletRequest request) {
        boolean clientAllowed =
                consume(authenticationFailureWindows, "client:" + request.getRemoteAddr());
        String publicId = publicTokenId(request.getHeader("Authorization"));
        boolean tokenAllowed = publicId == null
                || consume(authenticationFailureWindows, "token:" + publicId);
        return clientAllowed && tokenAllowed;
    }

    private <K> boolean consume(ConcurrentHashMap<K, Window> windows, K key) {
        Instant minute = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        Window current = windows.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.minute().equals(minute)) {
                return new Window(minute, new AtomicInteger(1));
            }
            previous.count().incrementAndGet();
            return previous;
        });
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute().isBefore(minute));
        }
        return current.count().get() <= requestsPerMinute;
    }

    private static String publicTokenId(String authorization) {
        String prefix = "Bearer omscim_";
        if (authorization == null || !authorization.startsWith(prefix)) {
            return null;
        }
        int separator = authorization.indexOf('.', prefix.length());
        if (separator != prefix.length() + 16) {
            return null;
        }
        String publicId = authorization.substring(prefix.length(), separator);
        return publicId.matches("[A-Za-z0-9_-]{16}") ? publicId : null;
    }

    private record Window(Instant minute, AtomicInteger count) {
    }
}
