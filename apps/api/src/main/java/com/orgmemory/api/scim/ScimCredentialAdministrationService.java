package com.orgmemory.api.scim;

import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScimCredentialAdministrationService {

    private final ProvisioningLedgerService ledger;
    private final ScimTokenCodec tokens;
    private final ScimSecurityProperties properties;
    private final Clock clock = Clock.systemUTC();

    ScimCredentialAdministrationService(
            ProvisioningLedgerService ledger,
            ScimTokenCodec tokens,
            ScimSecurityProperties properties) {
        this.ledger = ledger;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional
    public IssuedCredential issue(
            UUID organizationId,
            UUID connectionId,
            UUID createdByUserId,
            boolean usersScope,
            boolean groupsScope) {
        requireSupportedScope(usersScope, groupsScope);
        var issued = tokens.issue();
        Instant expiresAt = clock.instant().plus(properties.tokenTtl());
        UUID credentialId = ledger.storeCredentialVerifier(
                command(
                        organizationId,
                        connectionId,
                        createdByUserId,
                        usersScope,
                        groupsScope,
                        expiresAt,
                        issued));
        return new IssuedCredential(
                credentialId,
                issued.rawToken(),
                issued.publicId(),
                usersScope,
                groupsScope,
                expiresAt);
    }

    @Transactional
    public IssuedCredential rotate(
            UUID organizationId,
            UUID connectionId,
            UUID credentialId,
            UUID createdByUserId,
            boolean usersScope,
            boolean groupsScope) {
        requireSupportedScope(usersScope, groupsScope);
        var issued = tokens.issue();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        UUID replacementId = ledger.rotateCredential(
                organizationId,
                connectionId,
                credentialId,
                now.plus(properties.rotationOverlap()),
                command(
                        organizationId,
                        connectionId,
                        createdByUserId,
                        usersScope,
                        groupsScope,
                        expiresAt,
                        issued));
        return new IssuedCredential(
                replacementId,
                issued.rawToken(),
                issued.publicId(),
                usersScope,
                groupsScope,
                expiresAt);
    }

    private ProvisioningLedgerService.CredentialVerifierCommand command(
            UUID organizationId,
            UUID connectionId,
            UUID createdByUserId,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            ScimTokenCodec.IssuedToken issued) {
        return new ProvisioningLedgerService.CredentialVerifierCommand(
                organizationId,
                connectionId,
                issued.publicId(),
                issued.verifierDigest(),
                issued.keyVersion(),
                usersScope,
                groupsScope,
                expiresAt,
                createdByUserId);
    }

    private static void requireSupportedScope(boolean usersScope, boolean groupsScope) {
        if (!usersScope || groupsScope) {
            throw new IllegalArgumentException(
                    "The foundation credential profile supports Users only");
        }
    }

    public record IssuedCredential(
            UUID credentialId,
            String token,
            String publicTokenId,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt) {
    }
}
