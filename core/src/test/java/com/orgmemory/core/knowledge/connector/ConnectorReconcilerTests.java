package com.orgmemory.core.knowledge.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private final KnowledgeIngestionService ingestion = mock(KnowledgeIngestionService.class);
    private final SourcePrincipalService principals = mock(SourcePrincipalService.class);
    private final SourceGroupMembershipService groupMemberships = mock(SourceGroupMembershipService.class);
    private final SourcePrincipalMappingService mappings = mock(SourcePrincipalMappingService.class);
    private final SourcePrincipalRepository principalRepository = mock(SourcePrincipalRepository.class);
    private final SourceConnectionRepository connections = mock(SourceConnectionRepository.class);
    private final KnowledgeAssetPublicationService publications = mock(KnowledgeAssetPublicationService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ConnectorTextEmbedder> embedder = mock(ObjectProvider.class);
    private final ObjectStoragePort objects = mock(ObjectStoragePort.class);
    private final SourceInventoryQuery sourceInventory = mock(SourceInventoryQuery.class);
    private final SourceLifecycleService sourceLifecycle = mock(SourceLifecycleService.class);
    private final ConnectorSourceRevisionCoordinator revisionCoordinator =
            mock(ConnectorSourceRevisionCoordinator.class);
    private final PermissionAuditService audit = mock(PermissionAuditService.class);
    private final ConnectorReconciler reconciler = new ConnectorReconciler(
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

    @Test
    void missingMaterializedContentMakesPermissionOnlyReconciliationBenign() {
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
        when(sourceInventory.hasRetrievalSurface(
                        organizationId, "google_drive", "example.com", "permission-only-tail"))
                .thenReturn(false);

        ConnectorReconciler.ObjectOutcome outcome = reconciler.reconcilePermissions(
                context,
                new ConnectorPermissionItem("permission-only-tail", List.of()),
                new SourcePrincipalResolution(Map.of()));

        assertEquals(ConnectorReconciler.ObjectOutcome.UNCHANGED, outcome);
        verify(ingestion).findSourceHead(
                organizationId, "google_drive", "example.com", "permission-only-tail");
        verify(sourceInventory)
                .hasRetrievalSurface(
                        organizationId, "google_drive", "example.com", "permission-only-tail");
        verifyNoInteractions(audit, principals, groupMemberships, mappings, principalRepository);
    }

    @Test
    void missingAclHeadForMaterializedObjectFailsClosed() {
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
                "cursor-orphaned-object");
        when(ingestion.findSourceHead(
                        organizationId, "google_drive", "example.com", "orphaned-materialized"))
                .thenReturn(Optional.empty());
        when(sourceInventory.hasRetrievalSurface(
                        organizationId, "google_drive", "example.com", "orphaned-materialized"))
                .thenReturn(true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> reconciler.reconcilePermissions(
                        context,
                        new ConnectorPermissionItem("orphaned-materialized", List.of()),
                        new SourcePrincipalResolution(Map.of())));

        assertEquals(
                "an active source object exists without its ACL head: orphaned-materialized",
                failure.getMessage());
        verifyNoInteractions(audit, principals, groupMemberships, mappings, principalRepository);
    }
}
