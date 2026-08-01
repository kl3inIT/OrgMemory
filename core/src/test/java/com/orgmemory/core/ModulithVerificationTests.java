package com.orgmemory.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModuleInformation;
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
    void knowledgeSpaceIsAClosedNestedModule() {
        var space = modules.getModuleByName("knowledge.space").orElseThrow();
        var allowedDependencies = space.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(space.isOpen());
        assertEquals(
                Set.of(
                        "authorization",
                        "knowledge.sourceledger",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                allowedDependencies);
    }

    @Test
    void graphConsumesOnlySpaceQueryContract() {
        var space = modules.getModuleByName("knowledge.space").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(space))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.graph"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of("com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery"),
                consumedTypes);
    }

    @Test
    void retrievalConsumesOnlySpaceQueryContract() {
        var space = modules.getModuleByName("knowledge.space").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(space))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of("com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery"),
                consumedTypes);
    }

    @Test
    void graphAndRetrievalDoNotDependOnSpacePersistence() {
        var classes = new ClassFileImporter()
                .importPackages(
                        "com.orgmemory.core.knowledge.graph",
                        "com.orgmemory.core.knowledge.retrieval");

        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.orgmemory.core.knowledge.graph..",
                        "com.orgmemory.core.knowledge.retrieval..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.space.KnowledgeSpaceRepository")
                .check(classes);
    }

    @Test
    void sourceLedgerIsAClosedNestedModule() {
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var allowedDependencies = sourceLedger.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(sourceLedger.isOpen());
        assertEquals(
                Set.of(
                        "knowledge.acl",
                        "knowledge::storage",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                allowedDependencies);
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
    void sourceLedgerDoesNotDependOnGraphImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.sourceledger");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.graph..")
                .check(classes);
    }

    @Test
    void knowledgeAclIsAClosedNestedModule() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();
        var allowedDependencies = acl.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(acl.isOpen());
        assertEquals(
                Set.of(
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                allowedDependencies);
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
    void knowledgeAclDoesNotDependOnSourceLedgerImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.acl");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.acl..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.sourceledger..")
                .check(classes);
    }

    @Test
    void knowledgeAclDoesNotDependOnSpaceImplementation() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.acl");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.acl..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.space..")
                .check(classes);
    }

    @Test
    void retrievalConsumesOnlyAclQueryContracts() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(acl))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.acl.KnowledgeSpaceAclGenerationRef",
                        "com.orgmemory.core.knowledge.acl.SourceAclQuery"),
                consumedTypes);
    }

    @Test
    void graphConsumesOnlyAclQueryContracts() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(acl))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.graph"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.acl.SourceAclQuery",
                        "com.orgmemory.core.knowledge.acl.SourceAclSnapshotRef"),
                consumedTypes);
    }

    @Test
    void graphConsumesOnlyAssetGraphQueryContracts() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(asset))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.graph"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphChunk",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces"),
                consumedTypes);
    }

    @Test
    void graphDoesNotDependOnAssetPersistence() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.graph");

        for (String persistenceType : Set.of(
                "com.orgmemory.core.knowledge.asset.KnowledgeAsset",
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository",
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersion",
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository",
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionStatus",
                "com.orgmemory.core.knowledge.asset.KnowledgeChunkProjection",
                "com.orgmemory.core.knowledge.asset.KnowledgeChunkProjectionStore")) {
            noClasses()
                    .that()
                    .resideInAPackage("com.orgmemory.core.knowledge.graph..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(persistenceType)
                    .check(classes);
        }
    }

    @Test
    void graphConsumesOnlySourceLedgerGraphContracts() {
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(sourceLedger))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.graph"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.sourceledger.SourceFailureMessage",
                        "com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexPort",
                        "com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexQuery",
                        "com.orgmemory.core.knowledge.sourceledger.SourceGraphIndexRevisionRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceIngestionProperties"),
                consumedTypes);
    }

    @Test
    void graphDoesNotDependOnSourceLedgerPersistence() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.graph");

        for (String persistenceType : Set.of(
                "com.orgmemory.core.knowledge.sourceledger.SourceRevision",
                "com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository",
                "com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus")) {
            noClasses()
                    .that()
                    .resideInAPackage("com.orgmemory.core.knowledge.graph..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(persistenceType)
                    .check(classes);
        }
    }

    @Test
    void graphConsumesOnlyRetrievalGraphContracts() {
        var retrieval = modules.getModuleByName("knowledge.retrieval").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(retrieval))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.graph"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.ResolvedKnowledgeEvidenceScope",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore$RetrievalScope",
                        "com.orgmemory.core.knowledge.retrieval.SecureRetrievalCandidate"),
                consumedTypes);
    }

    @Test
    void graphDoesNotDependOnRetrievalProfilePersistence() {
        var classes = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.graph");

        for (String persistenceType : Set.of(
                "com.orgmemory.core.knowledge.retrieval.EmbeddingProfile",
                "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRepository")) {
            noClasses()
                    .that()
                    .resideInAPackage("com.orgmemory.core.knowledge.graph..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(persistenceType)
                    .check(classes);
        }
    }

    @Test
    void sourceLedgerConsumesOnlyAclFacadeContracts() {
        var acl = modules.getModuleByName("knowledge.acl").orElseThrow();
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetModule().equals(acl))
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.sourceledger"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.acl.AclAuthority",
                        "com.orgmemory.core.knowledge.acl.AclCaptureStatus",
                        "com.orgmemory.core.knowledge.acl.RotateSourceAclCommand",
                        "com.orgmemory.core.knowledge.acl.SourceAclEntryCommand",
                        "com.orgmemory.core.knowledge.acl.SourceAclFacade",
                        "com.orgmemory.core.knowledge.acl.SourceAclHeadRef",
                        "com.orgmemory.core.knowledge.acl.SourceAclRotationRef",
                        "com.orgmemory.core.knowledge.acl.SourceAclSnapshotRef",
                        "com.orgmemory.core.knowledge.acl.SourceAclTarget"),
                consumedTypes);
    }

    @Test
    void knowledgeConnectorIsAClosedNestedModule() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();
        var declaredDependencies = ApplicationModuleInformation.of(connector.getBasePackage())
                .getDeclaredDependencies()
                .stream()
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(connector.isOpen());
        assertEquals(
                Set.of(
                        "knowledge.acl",
                        "knowledge.asset",
                        "knowledge.retrieval",
                        "knowledge.sourceledger",
                        "knowledge.space",
                        "knowledge::storage",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error",
                        "shared::secret"),
                declaredDependencies);
    }

    @Test
    void connectorReadViewsUseOnlySourceLedgerInventoryContracts() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var readViewTypes = Set.of(
                "com.orgmemory.core.knowledge.connector.ConnectorObjectDirectory",
                "com.orgmemory.core.knowledge.connector.SourceConnectionActivityService");
        var consumedTypes = connector.getDirectDependencies(modules).stream()
                .filter(dependency -> dependency.getTargetModule().equals(sourceLedger))
                .filter(dependency -> readViewTypes.contains(
                        dependency.getSourceType().getName()))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.sourceledger.SourceInventoryQuery",
                        "com.orgmemory.core.knowledge.sourceledger.SourceInventorySummary"),
                consumedTypes);
    }

    @Test
    void connectorReconcilerUsesOnlySourceLedgerPublicLifecycleContracts() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var consumedTypes = connector.getDirectDependencies(modules).stream()
                .filter(dependency -> dependency.getTargetModule().equals(sourceLedger))
                .filter(dependency -> dependency.getSourceType().getName().equals(
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot",
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionService",
                        "com.orgmemory.core.knowledge.sourceledger.NormalizeRawSourceCommand",
                        "com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRef",
                        "com.orgmemory.core.knowledge.sourceledger.RawSourceRef",
                        "com.orgmemory.core.knowledge.sourceledger.RegisterRawSourceCommand",
                        "com.orgmemory.core.knowledge.sourceledger.SourceHeadView",
                        "com.orgmemory.core.knowledge.sourceledger.SourceInventoryQuery",
                        "com.orgmemory.core.knowledge.sourceledger.SourceInventoryRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceLifecycleService",
                        "com.orgmemory.core.knowledge.sourceledger.SourceRevisionDraftRef"),
                consumedTypes);
    }

    @Test
    void connectorRevisionAdapterUsesOnlySourceLedgerRevisionContracts() {
        var connector = modules.getModuleByName("knowledge.connector").orElseThrow();
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var consumedTypes = connector.getDirectDependencies(modules).stream()
                .filter(dependency -> dependency.getTargetModule().equals(sourceLedger))
                .filter(dependency -> dependency.getSourceType().getName().equals(
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator"))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.sourceledger.CompleteSourceRevisionCommand",
                        "com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot",
                        "com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRef",
                        "com.orgmemory.core.knowledge.sourceledger.RawSourceRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceEmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceRevisionDraftRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceRevisionService",
                        "com.orgmemory.core.knowledge.sourceledger.StageSourceRevisionCommand"),
                consumedTypes);
    }

    @Test
    void knowledgeAssetIsAnOpenNestedModuleDuringTheRefactor() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();

        assertTrue(asset.isOpen());
    }

    @Test
    void knowledgeAssetDoesNotDependOnSourceLedgerPersistence() {
        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.asset..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.sourceledger.NormalizedRecord")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.sourceledger.NormalizedRecordRepository")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.sourceledger.SourceObject")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.sourceledger.SourceObjectRepository")
                .check(new ClassFileImporter()
                        .importPackages("com.orgmemory.core.knowledge.asset"));
    }

    @Test
    void knowledgeAssetConsumesOnlySourceLedgerPublicContracts() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();
        var sourceLedger = modules.getModuleByName("knowledge.sourceledger").orElseThrow();
        var consumedTypes = asset.getDirectDependencies(modules).stream()
                .filter(dependency -> dependency.getTargetModule().equals(sourceLedger))
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeAssetPromotionPort",
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeAssetPromotionRequest",
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionConflictException",
                        "com.orgmemory.core.knowledge.sourceledger.KnowledgeIngestionService",
                        "com.orgmemory.core.knowledge.sourceledger.PromoteNormalizedRecordCommand",
                        "com.orgmemory.core.knowledge.sourceledger.PublishSourceRevisionCommand",
                        "com.orgmemory.core.knowledge.sourceledger.SourceFailureMessage",
                        "com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourcePublicationService"),
                consumedTypes);
    }

    @Test
    void knowledgeAssetOwnsItsCatalogAndChunkValues() {
        var assetClasses = new ClassFileImporter()
                .importPackages("com.orgmemory.core.knowledge.asset");
        var expectedOwnedTypes = Set.of(
                "com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem",
                "com.orgmemory.core.knowledge.asset.KnowledgeTextChunk",
                "com.orgmemory.core.knowledge.asset.PgVectorLiteral");

        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.asset..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogItem")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeTextChunk")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(
                        "com.orgmemory.core.knowledge.retrieval.PgVectorLiteral")
                .check(assetClasses);

        var ownedTypes = assetClasses.stream()
                .map(type -> type.getName())
                .filter(expectedOwnedTypes::contains)
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(expectedOwnedTypes, ownedTypes);
    }

    @Test
    void knowledgeAssetDoesNotDependOnRetrieval() {
        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.knowledge.asset..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.retrieval..")
                .check(new ClassFileImporter()
                        .importPackages("com.orgmemory.core.knowledge.asset"));
    }

    @Test
    void knowledgeGraphIsAClosedNestedModule() {
        var graph = modules.getModuleByName("knowledge.graph").orElseThrow();
        var allowedDependencies = graph.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(graph.isOpen());
        assertEquals(
                Set.of(
                        "ai",
                        "authorization",
                        "knowledge.acl",
                        "knowledge.asset",
                        "knowledge.retrieval",
                        "knowledge.sourceledger",
                        "knowledge.space",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                allowedDependencies);
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
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.PermissionAwareKnowledgeSearch",
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
    void knowledgeGraphHasNoDirectKnowledgeSiblingConsumers() {
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

        assertEquals(Set.of(), consumerTypes);
        assertEquals(Set.of(), consumedInternalTypes);
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
                        "com.orgmemory.core.assetregistry.AssetDeliveryService",
                        "com.orgmemory.core.assetregistry.CapabilityPackService",
                        "com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator",
                        "com.orgmemory.core.knowledge.graph.GraphIndexJobQueue",
                        "com.orgmemory.core.knowledge.graph.GraphIndexLifecycleService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExplorerService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExportService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService",
                        "com.orgmemory.core.knowledge.retrieval.SecureKnowledgeRetrievalStore",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphCurationService",
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler",
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator"),
                consumerTypes);
        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetAuthorizationScope",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphChunk",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphQuery",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetGraphRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetPublicationService",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersion",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository",
                        "com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkDraft",
                        "com.orgmemory.core.knowledge.asset.KnowledgeEmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces",
                        "com.orgmemory.core.knowledge.asset.PgVectorLiteral",
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
