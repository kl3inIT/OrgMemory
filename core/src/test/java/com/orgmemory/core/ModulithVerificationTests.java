package com.orgmemory.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
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
    void knowledgeRootPackageContainsNoDomainTypes() {
        var rootTypes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge")
                .stream()
                .filter(type -> type.getPackageName().equals("com.orgmemory.core.knowledge"))
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(Set.of(), rootTypes);
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
    void sourceLedgerDoesNotDependOnRetrievalImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.sourceledger");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.retrieval..")
                .check(classes);
    }

    @Test
    void sourceLedgerDoesNotDependOnAssetImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.sourceledger");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.asset..")
                .check(classes);
    }

    @Test
    void sourceLedgerDoesNotDependOnSpaceImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.sourceledger");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.space..")
                .check(classes);
    }

    @Test
    void sourceLedgerDoesNotDependOnConnectorImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.sourceledger");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.connector..")
                .check(classes);
    }

    @Test
    void knowledgeAclIsAnOpenNestedModuleDuringTheRefactor() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();

        assertTrue(acl.isOpen());
    }

    @Test
    void knowledgeAclDoesNotDependOnConnectorImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.acl");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.acl..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.connector..")
                .check(classes);
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
    void knowledgeRetrievalIsAnOpenNestedModuleDuringTheRefactor() {
        var retrieval = modules.getModuleByName("knowledge.retrieval").orElseThrow();

        assertTrue(retrieval.isOpen());
    }

    @Test
    void knowledgeRetrievalTemporaryOpenBoundaryDoesNotGainNewConsumers() {
        var retrieval = modules.getModuleByName("knowledge.retrieval").orElseThrow();
        var dependencies = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(retrieval))
                .toList();
        var consumerTypes = dependencies.stream()
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
        var consumedInternalTypes = dependencies.stream()
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.assetregistry.AssetDeliveryService",
                        "com.orgmemory.core.assetregistry.CapabilityPackService",
                        "com.orgmemory.core.assetregistry.PromptExecutionService",
                        "com.orgmemory.core.assistant.AssistantAssetToolService",
                        "com.orgmemory.core.assistant.AssistantCitation",
                        "com.orgmemory.core.assistant.AssistantPromptFactory",
                        "com.orgmemory.core.assistant.AssistantService",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetLifecycleService",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetPublicationOutbox",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkDraftAssembler",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkProjectionStore",
                        "com.orgmemory.core.knowledge.asset.PublishKnowledgeAssetCommand",
                        "com.orgmemory.core.knowledge.connector.ConnectorEmbeddingResult",
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler",
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator",
                        "com.orgmemory.core.knowledge.graph.ClaimedGraphIndex",
                        "com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExplorerConfiguration",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExplorerService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExportService"),
                consumerTypes);
        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfile",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRepository",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogItem",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeProjectionNamespaces",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeTextChunk",
                        "com.orgmemory.core.knowledge.retrieval.PermissionAwareKnowledgeSearch",
                        "com.orgmemory.core.knowledge.retrieval.PgVectorLiteral",
                        "com.orgmemory.core.knowledge.retrieval.ResolvedKnowledgeEvidenceScope",
                        "com.orgmemory.core.knowledge.retrieval.RetrievedKnowledgeEvidence",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore$RetrievalScope",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeSearchResult",
                        "com.orgmemory.core.knowledge.retrieval.SecureRetrievalCandidate",
                        "com.orgmemory.core.knowledge.retrieval.VerifiedKnowledgeGrounding"),
                consumedInternalTypes);
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
                        "com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator",
                        "com.orgmemory.core.knowledge.graph.GraphIndexJobQueue",
                        "com.orgmemory.core.knowledge.graph.GraphIndexLifecycleService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationService",
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler",
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator"),
                consumerTypes);
        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.asset.KnowledgeAsset",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetAuthorizationScope",
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
