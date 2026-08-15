package com.orgmemory.core.knowledge.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.acl.SourceGroupMembershipService;
import com.orgmemory.core.knowledge.acl.SourcePrincipalMappingService;
import com.orgmemory.core.knowledge.acl.SourcePrincipalRepository;
import com.orgmemory.core.knowledge.acl.SourcePrincipalResolution;
import com.orgmemory.core.knowledge.acl.SourcePrincipalService;
import com.orgmemory.core.knowledge.asset.KnowledgeAssetPublicationService;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionService;
import com.orgmemory.core.knowledge.sourceledger.SourceInventoryQuery;
import com.orgmemory.core.knowledge.sourceledger.SourceLifecycleService;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConnectorReconcilerTests {

    @Test
    void permissionForUnmaterializedObjectIsBenign() {
        KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);
        SourcePrincipalService principals = mock(SourcePrincipalService.class);
        SourceGroupMembershipService groupMemberships = mock(SourceGroupMembershipService.class);
        SourcePrincipalMappingService mappings = mock(SourcePrincipalMappingService.class);
        SourcePrincipalRepository principalRepository = mock(SourcePrincipalRepository.class);
        SourceConnectionRepository connections = mock(SourceConnectionRepository.class);
        KnowledgeAssetPublicationService publications = mock(KnowledgeAssetPublicationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ConnectorTextEmbedder> embedder = mock(ObjectProvider.class);
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        SourceInventoryQuery sourceInventory = mock(SourceInventoryQuery.class);
        SourceLifecycleService sourceLifecycle = mock(SourceLifecycleService.class);
        ConnectorSourceRevisionCoordinator revisionCoordinator =
                mock(ConnectorSourceRevisionCoordinator.class);
        PermissionAuditService audit = mock(PermissionAuditService.class);
        ConnectorReconciler reconciler = new ConnectorReconciler(
                ingestion,
                principals,
                groupMemberships,
                mappings,
                principalRepository,
                connections,
                publications,
                embedder,
                objects,
                sourceInventory,
                sourceLifecycle,
                revisionCoordinator,
                audit);
        UUID organizationId = UUID.fromString("fa000000-0000-4000-8000-000000000001");
        ConnectorSourceProfile profile = new ConnectorSourceProfile(
                "google_drive",
                "Google Drive",
                KnowledgeClassification.INTERNAL,
                DeclaredAccessScope.ALL_EMPLOYEES,
                "document",
                "text/plain");
        ConnectorIngestionContext context = new ConnectorIngestionContext(
                organizationId,
                profile,
                "example.com",
                UUID.fromString("fa000000-0000-4000-8000-000000000002"),
                UUID.fromString("fa000000-0000-4000-8000-000000000003"),
                "cursor-budget-hit");
        when(ingestion.findSourceHead(
                        organizationId, "google_drive", "example.com", "permission-only-tail"))
                .thenReturn(Optional.empty());

        ConnectorReconciler.ObjectOutcome outcome = reconciler.reconcilePermissions(
                context,
                new ConnectorPermissionItem("permission-only-tail", List.of()),
                new SourcePrincipalResolution(Map.of()));

        assertEquals(ConnectorReconciler.ObjectOutcome.UNCHANGED, outcome);
        verify(ingestion).findSourceHead(
                organizationId, "google_drive", "example.com", "permission-only-tail");
        verifyNoInteractions(audit, principals, groupMemberships, mappings, principalRepository);
    }
}
