package com.orgmemory.core.organization;

import com.orgmemory.core.shared.error.BusinessConflictException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalIdentityBindingService {

    private static final String CONFLICT_MESSAGE =
            "The sign-in identity cannot be linked to this account";

    private final ExternalIdentityRepository identities;

    public ExternalIdentityBindingService(ExternalIdentityRepository identities) {
        this.identities = Objects.requireNonNull(identities, "identities");
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findUserId(String issuer, String subject) {
        return identities.findByIssuerAndSubject(issuer, subject)
                .map(ExternalIdentity::getAppUserId);
    }

    @Transactional
    public ExternalIdentity bind(UUID appUserId, String issuer, String subject) {
        Objects.requireNonNull(appUserId, "appUserId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");

        Optional<ExternalIdentity> subjectBinding =
                identities.findByIssuerAndSubject(issuer, subject);
        if (subjectBinding.isPresent()) {
            return requireSameUser(subjectBinding.get(), appUserId);
        }

        Optional<ExternalIdentity> userBinding =
                identities.findByAppUserIdAndIssuer(appUserId, issuer);
        if (userBinding.isPresent()) {
            return requireSameSubject(userBinding.get(), subject);
        }

        identities.insertIfAbsent(UUID.randomUUID(), appUserId, issuer, subject);

        ExternalIdentity winner = identities.findByIssuerAndSubject(issuer, subject)
                .orElseThrow(() -> conflict("identity.binding-race-unresolved"));
        requireSameUser(winner, appUserId);
        return identities.findByAppUserIdAndIssuer(appUserId, issuer)
                .map(binding -> requireSameSubject(binding, subject))
                .orElseThrow(() -> conflict("identity.binding-race-unresolved"));
    }

    private static ExternalIdentity requireSameUser(
            ExternalIdentity binding, UUID expectedUserId) {
        if (!binding.getAppUserId().equals(expectedUserId)) {
            throw conflict("identity.binding-subject-conflict");
        }
        return binding;
    }

    private static ExternalIdentity requireSameSubject(
            ExternalIdentity binding, String expectedSubject) {
        if (!binding.getSubject().equals(expectedSubject)) {
            throw conflict("identity.binding-user-conflict");
        }
        return binding;
    }

    private static BusinessConflictException conflict(String code) {
        return new BusinessConflictException(code, CONFLICT_MESSAGE);
    }
}
