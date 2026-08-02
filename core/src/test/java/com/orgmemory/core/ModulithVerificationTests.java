package com.orgmemory.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assetregistry.api.AssetAuthorizationProjectionCommand;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTarget;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTargetQuery;
import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioCommand;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetRegistrationCommand;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetRoleCommand;
import com.orgmemory.core.assetregistry.api.AssetRoleQuery;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogEntry;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogQuery;
import com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.retrieval.CitationContentService;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.retrieval.KnowledgeAssetAccessInspector;
import com.orgmemory.core.knowledge.retrieval.SourceContentService;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
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
    void retrievalAdapterContractsAreInterfaces() throws ClassNotFoundException {
        assertTrue(AuthorizationResourceDirectory.class.isInterface());
        assertTrue(CanonicalHybridKnowledgeSearch.class.isInterface());
        assertTrue(CitationContentService.class.isInterface());
        assertTrue(EmbeddingProfileRegistry.class.isInterface());
        assertTrue(GraphRagKnowledgeRetrievalService.class.isInterface());
        assertTrue(KnowledgeAssetAccessInspector.class.isInterface());
        assertTrue(SourceContentService.class.isInterface());

        for (String implementation : Set.of(
                "com.orgmemory.core.knowledge.retrieval.DefaultAuthorizationResourceDirectory",
                "com.orgmemory.core.knowledge.retrieval.DefaultCanonicalHybridKnowledgeSearch",
                "com.orgmemory.core.knowledge.retrieval.DefaultCitationContentService",
                "com.orgmemory.core.knowledge.retrieval.DefaultGraphRagKnowledgeRetrievalService",
                "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                "com.orgmemory.core.knowledge.retrieval.DefaultSourceContentService",
                "com.orgmemory.core.knowledge.retrieval.JdbcEmbeddingProfileRegistry")) {
            assertFalse(Modifier.isPublic(Class.forName(implementation).getModifiers()));
        }
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
                        "com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope"),
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
    void knowledgeAssetIsAClosedNestedModule() {
        var asset = modules.getModuleByName("knowledge.asset").orElseThrow();
        var declaredDependencies = ApplicationModuleInformation.of(asset.getBasePackage())
                .getDeclaredDependencies()
                .stream()
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(asset.isOpen());
        assertEquals(
                Set.of(
                        "authorization",
                        "knowledge.sourceledger",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                declaredDependencies);
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
                        "com.orgmemory.core.knowledge.sourceledger.ReadyManualUploadRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceFailureMessage",
                        "com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeAssetRef",
                        "com.orgmemory.core.knowledge.sourceledger.SourceRetirementPort",
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
    void knowledgeRetrievalIsAClosedNestedModule() {
        var retrieval = modules.getModuleByName("knowledge.retrieval").orElseThrow();
        var allowedDependencies = retrieval.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(retrieval.isOpen());
        assertEquals(
                Set.of(
                        "ai",
                        "authorization",
                        "knowledge.acl",
                        "knowledge.asset",
                        "knowledge::catalog",
                        "knowledge::search",
                        "knowledge.sourceledger",
                        "knowledge.space",
                        "knowledge::storage",
                        "organization",
                        "permission",
                        "shared",
                        "shared::error"),
                allowedDependencies);
    }

    @Test
    void knowledgeRetrievalExposesOnlyIntentionalRootApi() {
        var publicRootTypes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.core.knowledge.retrieval")
                .stream()
                .filter(type -> type.getPackageName().equals(
                        "com.orgmemory.core.knowledge.retrieval"))
                .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.AuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch",
                        "com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearchConfiguration",
                        "com.orgmemory.core.knowledge.retrieval.CitationContent",
                        "com.orgmemory.core.knowledge.retrieval.CitationContentService",
                        "com.orgmemory.core.knowledge.retrieval.CitationNotFoundException",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry",
                        "com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec",
                        "com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagRetrievalPolicy",
                        "com.orgmemory.core.knowledge.retrieval.GraphRagRetrievalPolicy$RerankPolicy",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeAssetAccessInspector",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeAssetAccessInspector$AssetInspection",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalProperties",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.QueryEmbedding",
                        "com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort",
                        "com.orgmemory.core.knowledge.retrieval.SourceContent",
                        "com.orgmemory.core.knowledge.retrieval.SourceContentService",
                        "com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope"),
                publicRootTypes);
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
                        "com.orgmemory.core.knowledge.connector.ConnectorEmbeddingResult",
                        "com.orgmemory.core.knowledge.connector.ConnectorReconciler",
                        "com.orgmemory.core.knowledge.connector.ConnectorSourceRevisionCoordinator",
                        "com.orgmemory.core.knowledge.graph.ClaimedGraphIndex",
                        "com.orgmemory.core.knowledge.graph.GraphEvidenceScopeAccess",
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
                        "com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException",
                        "com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope"),
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
    void knowledgeAssetConsumerSurfaceDoesNotGainNewTypes() {
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
                        "com.orgmemory.core.knowledge.retrieval.DefaultAuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.graph.GraphIndexingCoordinator",
                        "com.orgmemory.core.knowledge.graph.GraphIndexJobQueue",
                        "com.orgmemory.core.knowledge.graph.GraphIndexLifecycleService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExplorerService",
                        "com.orgmemory.core.knowledge.graph.KnowledgeGraphExportService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.DefaultGraphRagKnowledgeRetrievalService",
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
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery",
                        "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionGraphRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem",
                        "com.orgmemory.core.knowledge.asset.KnowledgeChunkDraft",
                        "com.orgmemory.core.knowledge.asset.KnowledgeEmbeddingProfileRef",
                        "com.orgmemory.core.knowledge.asset.KnowledgeProjectionNamespaces",
                        "com.orgmemory.core.knowledge.asset.PgVectorLiteral",
                        "com.orgmemory.core.knowledge.asset.PublishKnowledgeAssetCommand"),
                consumedInternalTypes);
    }

    @Test
    void retrievalDoesNotDependOnAssetRepositories() {
        var assetRepositoryTypes = Set.of(
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetRepository",
                "com.orgmemory.core.knowledge.asset.KnowledgeAssetVersionRepository");
        var consumers = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .filter(dependency -> assetRepositoryTypes.contains(
                        dependency.getTargetType().getName()))
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(Set.of(), consumers);
    }

    @Test
    void retrievalAssetReadsUseOnlyTheOwnerQuery() {
        var consumers = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetType()
                        .getName()
                        .equals("com.orgmemory.core.knowledge.asset.KnowledgeAssetRetrievalQuery"))
                .map(dependency -> dependency.getSourceType().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.knowledge.retrieval"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.DefaultAuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver"),
                consumers);
    }

    @Test
    void retrievalDoesNotDependOnOrganizationPersistenceOrRoleTypes() {
        var forbiddenTypes = Set.of(
                "com.orgmemory.core.organization.AppUser",
                "com.orgmemory.core.organization.AppUserRepository",
                "com.orgmemory.core.organization.DepartmentRepository",
                "com.orgmemory.core.organization.OrganizationRepository",
                "com.orgmemory.core.organization.UserRole");
        var consumers = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .filter(dependency -> forbiddenTypes.contains(
                        dependency.getTargetType().getName()))
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(Set.of(), consumers);
    }

    @Test
    void retrievalOrganizationReadsUseOnlyOwnerQueries() {
        var ownerQueryTypes = Set.of(
                "com.orgmemory.core.organization.KnowledgeAccessSubject",
                "com.orgmemory.core.organization.KnowledgeAccessSubjectQuery",
                "com.orgmemory.core.organization.OrganizationResourceQuery");
        var dependencies = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .filter(dependency -> ownerQueryTypes.contains(
                        dependency.getTargetType().getName()))
                .toList();
        var consumers = dependencies.stream()
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
        var consumedTypes = dependencies.stream()
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.retrieval.DefaultAuthorizationResourceDirectory",
                        "com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver",
                        "com.orgmemory.core.knowledge.retrieval.SecureSourceVisibilityAdapter"),
                consumers);
        assertEquals(ownerQueryTypes, consumedTypes);
    }

    @Test
    void retrievalDoesNotDependOnSourceLedgerCitationPersistenceOrStatusTypes() {
        var forbiddenTypes = Set.of(
                "com.orgmemory.core.knowledge.sourceledger.EvidenceBlob",
                "com.orgmemory.core.knowledge.sourceledger.EvidenceBlobRepository",
                "com.orgmemory.core.knowledge.sourceledger.EvidenceScanStatus",
                "com.orgmemory.core.knowledge.sourceledger.SourceRevision",
                "com.orgmemory.core.knowledge.sourceledger.SourceRevisionRepository",
                "com.orgmemory.core.knowledge.sourceledger.SourceRevisionStatus");
        var consumers = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getSourceType()
                        .getPackageName()
                        .startsWith("com.orgmemory.core.knowledge.retrieval"))
                .filter(dependency -> forbiddenTypes.contains(
                        dependency.getTargetType().getName()))
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(Set.of(), consumers);
    }

    @Test
    void citationContentUsesTheSourceLedgerOwnerQuery() {
        var consumers = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> dependency.getTargetType()
                        .getName()
                        .equals("com.orgmemory.core.knowledge.sourceledger.SourceCitationEvidenceQuery"))
                .map(dependency -> dependency.getSourceType().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.knowledge.retrieval"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of("com.orgmemory.core.knowledge.retrieval.DefaultCitationContentService"),
                consumers);
    }

    @Test
    void objectStorageIsAnExplicitKnowledgeInterface() {
        var knowledge = modules.getModuleByName("knowledge").orElseThrow();
        var storage = knowledge.getNamedInterfaces().getByName("storage").orElseThrow();

        assertTrue(storage.contains(ObjectStoragePort.class));
    }

    @Test
    void catalogIsAnExactExplicitKnowledgeInterface() {
        var knowledge = modules.getModuleByName("knowledge").orElseThrow();
        var catalog = knowledge.getNamedInterfaces().getByName("catalog").orElseThrow();
        var exposedTypes = catalog.asJavaClasses()
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        KnowledgeCatalogEntry.class.getName(),
                        KnowledgeCatalogQuery.class.getName()),
                exposedTypes);
    }

    @Test
    void searchIsAnExactExplicitKnowledgeInterface() {
        var knowledge = modules.getModuleByName("knowledge").orElseThrow();
        var search = knowledge.getNamedInterfaces().getByName("search").orElseThrow();
        var exposedTypes = search.asJavaClasses()
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch",
                        "com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence",
                        "com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult",
                        "com.orgmemory.core.knowledge.search.VerifiedKnowledgeGrounding"),
                exposedTypes);
    }

    @Test
    void topLevelSearchConsumersUseOnlyTheParentSearchInterface() {
        var searchConsumerTypes = Set.of(
                "com.orgmemory.core.assetregistry.PromptExecutionService",
                "com.orgmemory.core.assistant.AssistantAssetToolService",
                "com.orgmemory.core.assistant.AssistantCitation",
                "com.orgmemory.core.assistant.AssistantPromptFactory",
                "com.orgmemory.core.assistant.AssistantService");
        var dependencies = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> searchConsumerTypes.contains(
                        dependency.getSourceType().getName()))
                .filter(dependency -> dependency.getTargetType()
                        .getPackageName()
                        .equals("com.orgmemory.core.knowledge.search"))
                .toList();
        var actualConsumers = dependencies.stream()
                .map(dependency -> dependency.getSourceType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);
        var consumedTypes = dependencies.stream()
                .map(dependency -> dependency.getTargetType().getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(searchConsumerTypes, actualConsumers);
        assertEquals(
                Set.of(
                        "com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch",
                        "com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence",
                        "com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult",
                        "com.orgmemory.core.knowledge.search.VerifiedKnowledgeGrounding"),
                consumedTypes);
    }

    @Test
    void assistantAndAssetRegistryDoNotDependOnRetrievalImplementation() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.orgmemory.core.assistant..",
                        "com.orgmemory.core.assetregistry..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.retrieval..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(
                                "com.orgmemory.core.assistant",
                                "com.orgmemory.core.assetregistry"));
    }

    @Test
    void assetRegistryCatalogConsumersUseOnlyTheParentCatalogInterface() {
        var catalogConsumerTypes = Set.of(
                "com.orgmemory.core.assetregistry.AssetDeliveryService",
                "com.orgmemory.core.assetregistry.CapabilityPackService");
        var consumedTypes = modules.stream()
                .flatMap(module -> module.getDirectDependencies(modules).stream())
                .filter(dependency -> catalogConsumerTypes.contains(
                        dependency.getSourceType().getName()))
                .map(dependency -> dependency.getTargetType().getName())
                .filter(name -> name.startsWith(
                        "com.orgmemory.core.knowledge.catalog."))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        KnowledgeCatalogEntry.class.getName(),
                        KnowledgeCatalogQuery.class.getName()),
                consumedTypes);
    }

    @Test
    void assetRegistryDoesNotDependOnKnowledgeAssetInternals() {
        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.assetregistry..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.orgmemory.core.knowledge.asset..")
                .check(new ClassFileImporter()
                        .importPackages("com.orgmemory.core.assetregistry"));
    }

    @Test
    void assetRegistryApiIsAnExactExplicitNamedInterface() {
        var assetRegistry = modules.getModuleByName("assetregistry").orElseThrow();
        var api = assetRegistry.getNamedInterfaces().getByName("api").orElseThrow();
        var exposedTypes = api.asJavaClasses()
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        AssetConflictException.class.getName(),
                        AssetNotFoundException.class.getName(),
                        AssetPortfolioState.class.getName(),
                        AssetRole.class.getName(),
                        AssetType.class.getName(),
                        AssetUnavailableException.class.getName(),
                        AssetAuthorizationProjectionCommand.class.getName(),
                        AssetAuthorizationTarget.class.getName(),
                        AssetAuthorizationTargetQuery.class.getName(),
                        AssetIdentity.class.getName(),
                        AssetIdentityQuery.class.getName(),
                        AssetPortfolioCommand.class.getName(),
                        AssetRegistrationCommand.class.getName(),
                        AssetRegistrationCommand.NewAsset.class.getName(),
                        AssetRoleCommand.class.getName(),
                        AssetRoleCommand.Assignment.class.getName(),
                        AssetRoleQuery.class.getName(),
                        AssetRoleQuery.OwnershipHealth.class.getName(),
                        AssetRoleQuery.RoleAssignment.class.getName(),
                        AssetRoleQuery.RoleHistory.class.getName()),
                exposedTypes);
    }

    @Test
    void assetRegistryKernelIsAClosedNestedModule() {
        var kernel = modules.getModuleByName("assetregistry.kernel").orElseThrow();
        var allowedDependencies = kernel.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(kernel.isOpen());
        assertEquals(
                Set.of("assetregistry::api", "authorization", "shared"),
                allowedDependencies);
    }

    @Test
    void assetRegistryKernelExposesOnlyProjectionQueueContracts() {
        var publicRootTypes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orgmemory.core.assetregistry.kernel")
                .stream()
                .filter(type -> type.getPackageName().equals(
                        "com.orgmemory.core.assetregistry.kernel"))
                .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
                .map(type -> type.getName())
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertEquals(
                Set.of(
                        "com.orgmemory.core.assetregistry.kernel.AssetAuthorizationBatch",
                        "com.orgmemory.core.assetregistry.kernel.AssetAuthorizationProjectionQueue"),
                publicRootTypes);
    }

    @Test
    void assetRegistryKernelDoesNotDependOnParentPersistenceOrProjection() {
        noClasses()
                .that()
                .resideInAPackage("com.orgmemory.core.assetregistry.kernel..")
                .should()
                .dependOnClassesThat()
                // Exact parent-package match keeps assetregistry.api available to the kernel.
                .resideInAnyPackage(
                        "com.orgmemory.core.assetregistry",
                        "com.orgmemory.core.assetregistry.authorization..")
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.orgmemory.core.assetregistry.kernel"));
    }

    @Test
    void assetRegistryAuthorizationIsAClosedProjectionModule() {
        var authorization = modules.getModuleByName("assetregistry.authorization").orElseThrow();
        var allowedDependencies = authorization.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(dependency -> dependency.replace(" :: ", "::"))
                .collect(TreeSet::new, Set::add, Set::addAll);

        assertFalse(authorization.isOpen());
        assertEquals(
                Set.of("assetregistry.kernel", "assetregistry::api", "authorization"),
                allowedDependencies);
    }
}
