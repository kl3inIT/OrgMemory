package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.knowledge.SourceIdentityObservation;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers principals observed from a source. Observation is not authorization and group
 * membership is captured independently by {@link SourceGroupMembershipService}.
 */
@Service
public class SourcePrincipalService {

    private final SourcePrincipalRepository principals;

    SourcePrincipalService(SourcePrincipalRepository principals) {
        this.principals = principals;
    }

    @Transactional
    public SourcePrincipal observe(SourceIdentityObservation observation) {
        return principals
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndKindAndNativePrincipalId(
                        observation.organizationId(),
                        observation.sourceSystem(),
                        observation.sourceConnectionKey(),
                        observation.kind(),
                        observation.nativePrincipalId())
                .map(existing -> {
                    existing.observe(
                            observation.observedEmail(),
                            observation.observedDisplayName(),
                            observation.ssoVerified(),
                            observation.observedAt());
                    return principals.save(existing);
                })
                .orElseGet(() -> principals.save(new SourcePrincipal(
                        UUID.randomUUID(),
                        observation.organizationId(),
                        observation.sourceSystem(),
                        observation.sourceConnectionKey(),
                        observation.nativePrincipalId(),
                        observation.kind(),
                        observation.observedEmail(),
                        observation.observedDisplayName(),
                        observation.ssoVerified(),
                        observation.observedAt())));
    }

}
