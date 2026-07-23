package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceRevisionRepository extends JpaRepository<SourceRevision, UUID> {

    Optional<SourceRevision> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The highest ordinal an object has reached, or zero when it has no revision yet. */
    @Query("""
            SELECT COALESCE(MAX(revision.revisionNumber), 0)
            FROM SourceRevision revision
            WHERE revision.sourceObjectId = :sourceObjectId
            """)
    long findHighestRevisionNumber(@Param("sourceObjectId") UUID sourceObjectId);
}
