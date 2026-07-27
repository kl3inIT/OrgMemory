package com.orgmemory.core.organization;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    /**
     * The open invitation for an address, across every organization.
     *
     * <p>Sign-in has no organization context — the identity provider supplies an address and
     * nothing else — so the invitation is what decides which organization the person joins. The
     * partial unique index keeps at most one open row per address per organization.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation FROM UserInvitation invitation
            WHERE lower(invitation.email) = lower(:email)
              AND invitation.acceptedAt IS NULL
              AND invitation.revokedAt IS NULL
            """)
    List<UserInvitation> findOpenByEmailForUpdate(@Param("email") String email);

    List<UserInvitation> findByOrganizationIdOrderByEmail(UUID organizationId);

    Optional<UserInvitation> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
