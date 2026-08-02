package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.consumption.AssetAvailability;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AssetCatalogReadModelRepository extends Repository<AssetDraft, UUID> {

    String CATALOG_FROM_AND_PREDICATES = """
            from Asset asset
            join AssetRelease release
              on release.assetId = asset.id
             and release.organizationId = asset.organizationId
            join AssetReleaseAvailabilityEvent availabilityEvent
              on availabilityEvent.releaseId = release.id
            where asset.organizationId = :organizationId
              and asset.id in :ids
              and asset.authorizationReady = true
              and (:type is null or asset.type = :type)
              and (
                    :query = ''
                    or lower(concat(
                        asset.namespace, ' ', asset.slug, ' ',
                        release.title, ' ', release.summary))
                       like concat('%', :query, '%')
              )
              and availabilityEvent.availability <> :withdrawn
              and not exists (
                    select laterAvailability.id
                    from AssetReleaseAvailabilityEvent laterAvailability
                    where laterAvailability.releaseId = availabilityEvent.releaseId
                      and (
                        laterAvailability.effectiveAt > availabilityEvent.effectiveAt
                        or (
                          laterAvailability.effectiveAt = availabilityEvent.effectiveAt
                          and laterAvailability.createdAt > availabilityEvent.createdAt
                        )
                      )
              )
              and not exists (
                    select newerRelease.id
                    from AssetRelease newerRelease
                    where newerRelease.assetId = asset.id
                      and newerRelease.organizationId = asset.organizationId
                      and newerRelease.sequence > release.sequence
                      and exists (
                            select newerAvailability.id
                            from AssetReleaseAvailabilityEvent newerAvailability
                            where newerAvailability.releaseId = newerRelease.id
                              and newerAvailability.availability <> :withdrawn
                              and not exists (
                                    select laterNewerAvailability.id
                                    from AssetReleaseAvailabilityEvent laterNewerAvailability
                                    where laterNewerAvailability.releaseId =
                                        newerAvailability.releaseId
                                      and (
                                        laterNewerAvailability.effectiveAt >
                                            newerAvailability.effectiveAt
                                        or (
                                          laterNewerAvailability.effectiveAt =
                                              newerAvailability.effectiveAt
                                          and laterNewerAvailability.createdAt >
                                              newerAvailability.createdAt
                                        )
                                      )
                              )
                      )
              )
            """;

    String CATALOG_ORDER = """
            order by
              case when :sort = 'NAME' then lower(release.title) end asc,
              case when :sort = 'RECENTLY_RELEASED' then release.releasedAt end desc,
              asset.id asc
            """;

    String SUMMARY_FROM_AND_PREDICATES = """
            from Asset asset
            join AssetDraft draft
              on draft.assetId = asset.id
             and draft.organizationId = asset.organizationId
            where asset.organizationId = :organizationId
              and asset.id in :ids
              and asset.authorizationReady = true
              and (:type is null or asset.type = :type)
              and (
                    :query = ''
                    or lower(concat(
                        asset.namespace, ' ', asset.slug, ' ',
                        draft.title, ' ', draft.summary))
                       like concat('%', :query, '%')
              )
            """;

    String OWNED_SUMMARY_ORDER = """
            order by
              case when :sort = 'NAME' then lower(draft.title) end asc,
              case when :sort = 'RECENTLY_UPDATED' then asset.updatedAt end desc,
              asset.id asc
            """;

    @Query("""
            select new com.orgmemory.core.assetregistry.AssetSummary(
                asset.id,
                asset.type,
                asset.namespace,
                asset.slug,
                draft.title,
                draft.summary,
                asset.knowledgeSpaceId,
                asset.portfolioState,
                asset.updatedAt)
            """
            + SUMMARY_FROM_AND_PREDICATES
            + "order by asset.namespace, asset.slug")
    List<AssetSummary> searchAuthorized(
            @Param("organizationId") UUID organizationId,
            @Param("ids") Collection<UUID> ids,
            @Param("query") String query,
            @Param("type") AssetType type,
            Pageable pageable);

    @Query(
            value = """
                    select new com.orgmemory.core.assetregistry.AssetSummary(
                        asset.id,
                        asset.type,
                        asset.namespace,
                        asset.slug,
                        draft.title,
                        draft.summary,
                        asset.knowledgeSpaceId,
                        asset.portfolioState,
                        asset.updatedAt)
                    """
                    + SUMMARY_FROM_AND_PREDICATES
                    + OWNED_SUMMARY_ORDER,
            countQuery = "select count(asset.id)\n" + SUMMARY_FROM_AND_PREDICATES)
    Page<AssetSummary> searchOwnedSummaries(
            @Param("organizationId") UUID organizationId,
            @Param("ids") Collection<UUID> ids,
            @Param("query") String query,
            @Param("type") AssetType type,
            @Param("sort") String sort,
            Pageable pageable);

    @Query(
            value = """
                    select new com.orgmemory.core.assetregistry.AssetRecommendation(
                        asset.id,
                        asset.type,
                        asset.namespace,
                        asset.slug,
                        release.title,
                        release.summary,
                        asset.knowledgeSpaceId,
                        asset.portfolioState,
                        release.id,
                        release.versionLabel,
                        release.digest,
                        availabilityEvent.availability,
                        release.releasedAt)
                    """
                    + CATALOG_FROM_AND_PREDICATES
                    + CATALOG_ORDER,
            countQuery = "select count(asset.id)\n" + CATALOG_FROM_AND_PREDICATES)
    Page<AssetRecommendation> searchAuthorizedRecommendations(
            @Param("organizationId") UUID organizationId,
            @Param("ids") Collection<UUID> ids,
            @Param("query") String query,
            @Param("type") AssetType type,
            @Param("withdrawn") AssetAvailability withdrawn,
            @Param("sort") String sort,
            Pageable pageable);
}
