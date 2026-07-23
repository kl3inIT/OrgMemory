package com.orgmemory.core.organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);

    /** Which users have a linked identity and can therefore actually sign in. */
    List<ExternalIdentity> findByAppUserIdIn(Collection<UUID> appUserIds);

    @Modifying
    @Query(value = """
            INSERT INTO external_identities (
                id, app_user_id, issuer, subject, created_at, updated_at, version
            ) VALUES (
                :id, :appUserId, :issuer, :subject, now(), now(), 0
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int linkIfAbsent(
            @Param("id") UUID id,
            @Param("appUserId") UUID appUserId,
            @Param("issuer") String issuer,
            @Param("subject") String subject);
}
