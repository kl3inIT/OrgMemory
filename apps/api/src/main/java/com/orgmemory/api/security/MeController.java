package com.orgmemory.api.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    record MeResponse(boolean authenticated, String subject, String email, String name, List<String> roles) {
        static MeResponse anonymous() {
            return new MeResponse(false, null, null, null, List.of());
        }
    }

    @GetMapping("/api/me")
    MeResponse me(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return MeResponse.anonymous();
        }
        List<String> roles = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()).toLowerCase())
                .toList();
        return new MeResponse(
                true,
                token.getToken().getSubject(),
                token.getToken().getClaimAsString("email"),
                token.getToken().getClaimAsString("name"),
                roles);
    }
}
