package com.orgmemory.core.knowledge.acl;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only ACL projection for connection administration. */
@Service
public class SourceConnectionPrincipalQuery {

    private final SourcePrincipalRepository principals;
    private final SourcePrincipalMappingRepository mappings;

    SourceConnectionPrincipalQuery(
            SourcePrincipalRepository principals,
            SourcePrincipalMappingRepository mappings) {
        this.principals = principals;
        this.mappings = mappings;
    }

    @Transactional(readOnly = true)
    public List<SourceConnectionPrincipalSummary> list(UUID organizationId) {
        Set<UUID> mappedPrincipalIds = mappings
                .findByOrganizationIdAndStatus(
                        organizationId, SourcePrincipalMappingStatus.ACTIVE)
                .stream()
                .map(SourcePrincipalMapping::getSourcePrincipalId)
                .collect(Collectors.toUnmodifiableSet());
        return principals.findByOrganizationId(organizationId).stream()
                .map(principal -> new SourceConnectionPrincipalSummary(
                        principal.getSourceSystem(),
                        principal.getSourceConnectionKey(),
                        principal.getKind(),
                        mappedPrincipalIds.contains(principal.getId()),
                        principal.getLastSeenAt()))
                .toList();
    }
}
