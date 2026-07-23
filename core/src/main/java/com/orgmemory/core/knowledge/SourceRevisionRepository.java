package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceRevisionRepository extends JpaRepository<SourceRevision, UUID> {

    Optional<SourceRevision> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<SourceRevision> findBySourceObjectIdAndContentSha256(
            UUID sourceObjectId, String contentSha256);

    /** The highest ordinal an object has reached, or zero when it has no revision yet. */
    @Query("""
            select coalesce(max(revision.revisionNumber), 0)
            from SourceRevision revision
            where revision.sourceObjectId = :sourceObjectId
            """)
    long maximumRevisionNumber(@Param("sourceObjectId") UUID sourceObjectId);
}
