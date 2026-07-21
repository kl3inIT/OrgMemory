package com.orgmemory.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
final class BrowserLoginFlow {

    static final String RETURN_TO_SESSION_ATTRIBUTE = BrowserLoginFlow.class.getName() + ".RETURN_TO";
    private static final String DEFAULT_RETURN_PATH = "/";

    private final OrgMemoryOidcProperties oidc;

    BrowserLoginFlow(OrgMemoryOidcProperties oidc) {
        this.oidc = oidc;
    }

    void rememberReturnPath(HttpServletRequest request, String requestedReturnPath) {
        HttpSession session = request.getSession(true);
        if (requestedReturnPath != null) {
            session.setAttribute(RETURN_TO_SESSION_ATTRIBUTE, safeReturnPath(requestedReturnPath));
        } else if (session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE) == null) {
            session.setAttribute(RETURN_TO_SESSION_ATTRIBUTE, DEFAULT_RETURN_PATH);
        }
    }

    void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        Objects.requireNonNull(authentication, "authentication");
        response.sendRedirect(oidc.webLocation(consumeReturnPath(request)));
    }

    static String safeReturnPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return DEFAULT_RETURN_PATH;
        }

        String path = candidate.trim();
        if (!path.startsWith("/")
                || path.startsWith("//")
                || path.contains("\\")
                || path.toLowerCase(Locale.ROOT).contains("%5c")
                || path.chars().anyMatch(Character::isISOControl)) {
            return DEFAULT_RETURN_PATH;
        }

        try {
            URI uri = URI.create(path);
            if (uri.isAbsolute()
                    || uri.getRawAuthority() != null
                    || uri.getRawPath() == null
                    || !uri.getRawPath().startsWith("/")
                    || uri.getRawPath().startsWith("//")) {
                return DEFAULT_RETURN_PATH;
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return DEFAULT_RETURN_PATH;
        }
    }

    private static String consumeReturnPath(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return DEFAULT_RETURN_PATH;
        }
        Object candidate = session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        return candidate instanceof String value ? safeReturnPath(value) : DEFAULT_RETURN_PATH;
    }
}
