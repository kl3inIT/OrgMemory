package com.orgmemory.core.knowledge.sourceledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceRevisionRepository extends JpaRepository<SourceRevision, UUID> {

    Optional<SourceRevision> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<SourceRevision> findBySourceObjectIdAndContentSha256(
            UUID sourceObjectId, String contentSha256);

    @Query("""
            select revision
            from SourceRevision revision, SourceObject source
            where source.id = :sourceObjectId
              and source.organizationId = :organizationId
              and source.status = com.orgmemory.core.knowledge.sourceledger.SourceObjectStatus.ACTIVE
              and source.currentRevisionId = revision.id
              and revision.organizationId = :organizationId
              and revision.status = com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus.READY
            """)
    Optional<SourceRevision> findCurrentReadyBySourceObjectIdAndOrganizationId(
            @Param("sourceObjectId") UUID sourceObjectId,
            @Param("organizationId") UUID organizationId);

    /** The highest ordinal an object has reached, or zero when it has no revision yet. */
    @Query("""
            select coalesce(max(revision.revisionNumber), 0)
            from SourceRevision revision
            where revision.sourceObjectId = :sourceObjectId
            """)
    long maximumRevisionNumber(@Param("sourceObjectId") UUID sourceObjectId);
}
