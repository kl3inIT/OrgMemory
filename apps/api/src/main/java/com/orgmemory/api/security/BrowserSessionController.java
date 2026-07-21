package com.orgmemory.api.security;

import com.orgmemory.core.organization.CurrentActor;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
class BrowserSessionController {

    private final CurrentActorProvider actors;
    private final BrowserLoginFlow loginFlow;

    BrowserSessionController(CurrentActorProvider actors, BrowserLoginFlow loginFlow) {
        this.actors = actors;
        this.loginFlow = loginFlow;
    }

    record SessionResponse(
            boolean authenticated,
            String name,
            String email,
            UUID userId,
            UUID organizationId,
            UUID departmentId) {

        static SessionResponse anonymous() {
            return new SessionResponse(false, null, null, null, null, null);
        }
    }

    record CsrfResponse(String headerName, String parameterName, String token) {
    }

    @GetMapping("/api/session")
    @Operation(operationId = "getBrowserSession", summary = "Read the current browser session")
    SessionResponse session(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SessionResponse.anonymous();
        }
        CurrentActor actor = actors.current(authentication);
        return new SessionResponse(
                true,
                actor.name(),
                actor.email(),
                actor.userId(),
                actor.organizationId(),
                actor.departmentId());
    }

    @GetMapping("/api/session/login")
    @Hidden
    RedirectView login(
            @RequestParam(required = false) String returnTo,
            @Parameter(hidden = true) HttpServletRequest request) {
        loginFlow.rememberReturnPath(request, returnTo);
        return new RedirectView("/oauth2/authorization/keycloak");
    }

    @GetMapping("/api/session/csrf")
    @Operation(operationId = "getBrowserCsrfToken", summary = "Issue a CSRF token for browser mutations")
    CsrfResponse csrf(@Parameter(hidden = true) CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getParameterName(), csrf.getToken());
    }
}
