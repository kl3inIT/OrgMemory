package com.orgmemory.api.scim;

import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import com.orgmemory.core.identityprovisioning.ProvisioningNotFoundException;
import com.orgmemory.core.identityprovisioning.ProvisioningOperationalState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class ScimAuthenticationProvider implements AuthenticationProvider {

    private static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final ProvisioningLedgerService ledger;
    private final ScimTokenCodec tokens;
    private final Clock clock = Clock.systemUTC();

    ScimAuthenticationProvider(ProvisioningLedgerService ledger, ScimTokenCodec tokens) {
        this.ledger = ledger;
        this.tokens = tokens;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        ScimTokenCodec.ParsedToken parsed;
        try {
            parsed = tokens.parse(
                    ((BearerTokenAuthenticationToken) authentication).getToken());
        } catch (IllegalArgumentException parsingFailure) {
            throw new BadCredentialsException("Invalid SCIM credential", parsingFailure);
        }

        ProvisioningLedgerService.CredentialAuthentication credential;
        try {
            credential = ledger.credentialForAuthentication(parsed.publicId());
        } catch (ProvisioningNotFoundException notFound) {
            throw new BadCredentialsException("Invalid SCIM credential", notFound);
        }
        Instant now = clock.instant();
        if (!tokens.matches(
                        parsed.secret(),
                        credential.verifierDigest(),
                        credential.verifierKeyVersion())
                || credential.revokedAt() != null
                || (credential.expiresAt() != null && !credential.expiresAt().isAfter(now))
                || (credential.overlapEndsAt() != null
                        && !credential.overlapEndsAt().isAfter(now))
                || credential.connectionState() == ProvisioningOperationalState.SUSPENDED) {
            throw new BadCredentialsException("Invalid SCIM credential");
        }

        var authorities = new ArrayList<SimpleGrantedAuthority>();
        if (credential.usersScope()) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_scim.users"));
        }
        if (credential.groupsScope()) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_scim.groups"));
        }
        var principal = new ScimMachinePrincipal(
                credential.organizationId(),
                credential.connectionId(),
                credential.credentialId(),
                credential.publicTokenId(),
                credential.connectionState());
        if (credential.lastUsedAt() == null
                || credential.lastUsedAt().isBefore(now.minus(LAST_USED_WRITE_INTERVAL))) {
            ledger.markCredentialUsed(
                    credential.organizationId(), credential.credentialId(), now);
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
