package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers external principals observed from a source and their sealed group membership.
 * Every write here is observation only and grants no access on its own.
 */
@Service
class SourcePrincipalService {

    private final SourcePrincipalRepository principals;
    private final SourceAclGroupMemberRepository groupMembers;

    SourcePrincipalService(
            SourcePrincipalRepository principals,
            SourceAclGroupMemberRepository groupMembers) {
        this.principals = principals;
        this.groupMembers = groupMembers;
    }

    @Transactional
    SourcePrincipal observe(SourceIdentityObservation observation) {
        return principals
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalKey(
                        observation.organizationId(),
                        observation.sourceSystem(),
                        observation.sourceConnectionKey(),
                        observation.externalKey())
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
                        observation.externalKey(),
                        observation.kind(),
                        observation.observedEmail(),
                        observation.observedDisplayName(),
                        observation.ssoVerified(),
                        observation.observedAt())));
    }

    /**
     * Records the members of a source group for one ACL snapshot generation. Must run
     * before the snapshot is sealed; the database rejects inserts afterwards.
     */
    @Transactional
    void recordGroupMembership(
            UUID organizationId,
            UUID sourceAclSnapshotId,
            UUID groupPrincipalId,
            Collection<UUID> memberPrincipalIds,
            Instant recordedAt) {
        for (UUID memberPrincipalId : memberPrincipalIds) {
            groupMembers.save(new SourceAclGroupMember(
                    organizationId,
                    sourceAclSnapshotId,
                    groupPrincipalId,
                    memberPrincipalId,
                    recordedAt));
        }
    }
}
