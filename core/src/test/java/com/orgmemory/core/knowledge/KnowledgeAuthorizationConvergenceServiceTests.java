package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class KnowledgeAuthorizationConvergenceServiceTests {

    private final KnowledgeAssetPublicationCoordinator publications =
            mock(KnowledgeAssetPublicationCoordinator.class);
    private final RelationshipTupleWritePort writer = mock(RelationshipTupleWritePort.class);
    private final RelationshipTupleReconciliationPort reconciliation =
            mock(RelationshipTupleReconciliationPort.class);
    private final KnowledgeAuthorizationConvergenceService service =
            new KnowledgeAuthorizationConvergenceService(publications, writer, reconciliation);

    @Test
    void repairsModelDriftAndDeletesOnlyManagedUuidOrphans() {
        UUID existingAsset = UUID.randomUUID();
        UUID orphanAsset = UUID.randomUUID();
        KnowledgeAssetPublicationState drift = publication(existingAsset);
        when(reconciliation.policyVersion()).thenReturn("model-2");
        when(publications.findAuthorizationModelDrift("model-2", 50))
                .thenReturn(List.of(drift));
        when(writer.write(any())).thenReturn(RelationshipTupleWriteResult.applied("model-2"));
        when(reconciliation.read(100, null)).thenReturn(RelationshipTuplePage.resolved(
                List.of(
                        RelationshipTuple.of(
                                "user:" + UUID.randomUUID(),
                                "owner",
                                "knowledge_asset:" + existingAsset),
                        RelationshipTuple.of(
                                "knowledge_space:" + UUID.randomUUID(),
                                "space",
                                "knowledge_asset:" + orphanAsset),
                        RelationshipTuple.of(
                                "user:fixture",
                                "owner",
                                "knowledge_asset:employee-handbook")),
                null,
                "model-2"));
        when(publications.existingAssetIds(Set.of(existingAsset, orphanAsset)))
                .thenReturn(Set.of(existingAsset));
        when(reconciliation.delete(any()))
                .thenReturn(RelationshipTupleWriteResult.applied("model-2"));

        KnowledgeAuthorizationConvergenceReport report =
                service.reconcile(50, 100, 10);

        assertTrue(report.complete());
        assertEquals(1, report.modelDriftDetected());
        assertEquals(1, report.modelDriftRepaired());
        assertEquals(3, report.tuplesScanned());
        assertEquals(1, report.orphanTuplesDeleted());
        verify(publications).recordAuthorizationModel(
                drift.organizationId(), drift.publicationId(), "model-2");
        var deletion = ArgumentCaptor.forClass(RelationshipTupleWriteRequest.class);
        verify(reconciliation).delete(deletion.capture());
        assertEquals(
                "knowledge_asset:" + orphanAsset,
                deletion.getValue().tuples().getFirst().object());
    }

    @Test
    void readFailureStopsWithoutDeletingTuples() {
        when(reconciliation.policyVersion()).thenReturn("model-2");
        when(publications.findAuthorizationModelDrift("model-2", 50))
                .thenReturn(List.of());
        when(reconciliation.read(100, null)).thenReturn(
                RelationshipTuplePage.indeterminate("OPENFGA_READ_TIMEOUT", "model-2"));

        KnowledgeAuthorizationConvergenceReport report =
                service.reconcile(50, 100, 10);

        assertFalse(report.complete());
        assertEquals("OPENFGA_READ_TIMEOUT", report.reasonCode());
        verify(reconciliation, never()).delete(any());
    }

    @Test
    void scansEveryPageBeforeDeletingSoContinuationTokensStayStable() {
        UUID firstOrphan = UUID.randomUUID();
        UUID secondOrphan = UUID.randomUUID();
        RelationshipTuple first = RelationshipTuple.of(
                "user:" + UUID.randomUUID(),
                "owner",
                "knowledge_asset:" + firstOrphan);
        RelationshipTuple second = RelationshipTuple.of(
                "knowledge_space:" + UUID.randomUUID(),
                "space",
                "knowledge_asset:" + secondOrphan);
        when(reconciliation.policyVersion()).thenReturn("model-2");
        when(publications.findAuthorizationModelDrift("model-2", 50))
                .thenReturn(List.of());
        when(reconciliation.read(100, null)).thenReturn(
                RelationshipTuplePage.resolved(List.of(first), "next", "model-2"));
        when(reconciliation.read(100, "next")).thenReturn(
                RelationshipTuplePage.resolved(List.of(second), null, "model-2"));
        when(publications.existingAssetIds(Set.of(firstOrphan))).thenReturn(Set.of());
        when(publications.existingAssetIds(Set.of(secondOrphan))).thenReturn(Set.of());
        when(reconciliation.delete(any()))
                .thenReturn(RelationshipTupleWriteResult.applied("model-2"));

        KnowledgeAuthorizationConvergenceReport report =
                service.reconcile(50, 100, 10);

        assertTrue(report.complete());
        assertEquals(2, report.tuplesScanned());
        assertEquals(2, report.orphanTuplesDeleted());
        InOrder calls = inOrder(reconciliation);
        calls.verify(reconciliation).read(100, null);
        calls.verify(reconciliation).read(100, "next");
        calls.verify(reconciliation, times(2)).delete(any());
    }

    private static KnowledgeAssetPublicationState publication(UUID assetId) {
        return new KnowledgeAssetPublicationState(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                assetId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                KnowledgeAssetPublicationStatus.APPLIED);
    }
}
