package com.orgmemory.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerificationTests {

    private final ApplicationModules modules = ApplicationModules.of(OrgMemoryModules.class);

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void knowledgeSpaceIsAnOpenNestedModuleDuringTheRefactor() {
        var space = modules.getModuleByName("knowledge.space").orElseThrow();

        assertTrue(space.isOpen());
    }

    @Test
    void sourceLedgerIsAnOpenNestedModuleDuringTheRefactor() {
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();

        assertTrue(sourceLedger.isOpen());
    }

    @Test
    void knowledgeAclIsAnOpenNestedModuleDuringTheRefactor() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();

        assertTrue(acl.isOpen());
    }

    @Test
    void knowledgeConnectorIsAnOpenNestedModuleDuringTheRefactor() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();

        assertTrue(connector.isOpen());
    }

    @Test
    void knowledgeAssetIsAnOpenNestedModuleDuringTheRefactor() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();

        assertTrue(asset.isOpen());
    }

    @Test
    void knowledgeGraphIsAnOpenNestedModuleDuringTheRefactor() {
        var graph = modules.getModuleByName("knowledge.graph").orElseThrow();

        assertTrue(graph.isOpen());
    }

    @Test
    void knowledgeGraphTemporaryOpenBoundaryDoesNotGainNewConsumers() {
        var graph = modules.getModuleByName("knowledge.graph").orElseThrow();
        var dependencies = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(graph))
                .toList();
        var consumerTypes = dependencies.stream()
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
        var consumedInternalTypes = dependencies.stream()
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator",
                        "com.orgmemory.core.knowledge.sourceledger.SourceIngestionCoordinator"),
                consumerTypes);
        assertEquals(
                Set.of("com.orgmemory.core.knowledge.graph.GraphIndexJobQueue"),
                consumedInternalTypes);
    }

    @Test
    void knowledgeAssetTemporaryOpenBoundaryDoesNotGainNewConsumers() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();
        var dependencies = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(asset))
                .toList();
        var consumerTypes = dependencies.stream()
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
        var consumedInternalTypes = dependencies.stream()
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.AuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator",
                        "com.orgmemory.core.knowledge.graph.GraphIndexJobQueue",
                        "com.orgmemory.core.knowledge.graph.GraphIndexLifecycleService",
                        "com.orgmemory.core.knowledge.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationService",
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler",
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator",
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionService",
                        "com.orgmemory.core.knowledge.sourceledger.SourceIngestionCoordinator",
                        "com.orgmemory.core.knowledge.sourceledger.SourceRevision",
                        "com.orgmemory.core.knowledge.sourceledger.SourceUploadService"),
                consumerTypes);
        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.asset.KnowledgeAsset",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetAuthorizationScope",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetEvidenceLink",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetEvidenceLinkRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetNotFoundException",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetPublicationService",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersion",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionStatus",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkDraft",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkProjection",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkProjectionStore",
                        "com.orgmemory.core.knowledge.asset.KnowledgeContentType",
                        "com.orgmemory.core.knowledge.asset.PublishKnowledgeAssetCommand"),
                consumedInternalTypes);
    }

    @Test
    void objectStorageIsAnExplicitKnowledgeInterface() {
        var knowledge = modules.getModuleByName("knowledge").orElseThrow();
        var storage = knowledge.getNamedInterfaces().getByName("storage").orElseThrow();

        assertTrue(storage.contains(ObjectStoragePort.class));
    }
}
