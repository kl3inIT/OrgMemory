# Secure Retrieval Coverage

Source: `core/src/test/java/com/orgmemory/core/knowledge`,
`core/src/test/java/com/orgmemory/core/permission`,
`apps/api/src/test/java/com/orgmemory/api/knowledge`, and
`integrations/authorization-openfga/src/test`.

Reconciled: `2026-08-02-knowledge-space-audience-main-sync (405fa212)`.

Primary evidence: `apps/api/src/test/java/com/orgmemory/api/knowledge/KnowledgeRetrievalIntegrationTests.java` and `core/src/test/java/com/orgmemory/core/permission/KnowledgePermissionPolicyTests.java`.

| Behavior | Automated evidence |
| --- | --- |
| SQL authorization before keyword/limit | `listAndKeywordSearchReturnOnlySqlAuthorizedAssets` |
| Eligible reader gets generic missing/denied 404 | `detailAllowsVisibleContentAndUsesGenericNotFoundForEveryDenial` |
| Admin is not Executive | `controlPlaneAdminIsNotExecutiveButBusinessExecutiveCanReadRestricted` |
| Confidential missing/foreign department fails closed | `nullDepartmentExecutiveCannotUseRoleToBypassConfidentialDepartmentRequirement`, policy tests |
| Department Space rejects an OpenFGA-authorized actor from another current persisted department | `ExternalPrincipalRetrievalIntegrationTests#departmentAudienceRejectsAnOtherwiseAuthorizedCrossDepartmentCandidate` |
| Restricted custom Space requires both OpenFGA and the PostgreSQL audience ledger, so a rogue tuple alone grants nothing | `ExternalPrincipalRetrievalIntegrationTests#restrictedCustomAudienceRequiresItsPostgresGrantInAdditionToOpenFgaEligibility` |
| Visible Space discovery applies the persisted department audience after OpenFGA candidate selection | `KnowledgeSpaceServiceTests#visibleSpaceListingRejectsAnOpenFgaResultOutsideTheDepartmentAudience` |
| Restriction revokes before retrieval | `aclHeadRotationRevokesAssetBeforeKeywordAndContentRetrieval` |
| Later expansion cannot exceed ingestion ceiling | `widerCurrentAclCannotOverrideTheIngestionSnapshotDeny` |
| Refreshed head preserves availability without widening | `refreshedHeadKeepsAssetAvailableAfterHistoricalSnapshotExpiresWithoutWideningIt` |
| Denial and query/source decisions are audited without raw query | `controlPlaneRoleDenialIsAudited`, `searchAuditsQueryAndEveryReturnedSourceWithoutRawQuery` |
| Graph entity/relation/chunk evidence closure is rechecked before prompt rendering | `GraphRagKnowledgeRetrievalServiceTests#verifiesTheCompleteGraphGroundingBeforeCreatingTheModelInput` |
| OpenFGA model mismatch cannot reach the renderer | `GraphRagKnowledgeRetrievalServiceTests#authorizationModelMismatchCannotReachTheVerifiedRenderer` |
| Authorization scope changing during retrieval retries without egress | `GraphRagKnowledgeRetrievalServiceTests#revocationBetweenRetrievalAndCitationCausesAFullRetryWithoutEgress` |
| Parent Knowledge exposes only the exact four-type `knowledge::search` contract, and Assistant plus Asset Registry do not import Retrieval implementation | `ModulithVerificationTests#searchIsAnExactExplicitKnowledgeInterface`, `#topLevelSearchConsumersUseOnlyTheParentSearchInterface`, `#assistantAndAssetRegistryDoNotDependOnRetrievalImplementation` |
| One-asset admin inspection independently requires relationship allowance before canonical retrieval eligibility | `KnowledgeEvidenceScopeResolverTests#assetInspectionRequiresRelationshipAuthorizationBeforeCanonicalVisibility`, `PermissionsAdminIntegrationTests#effectiveContentAccessSeparatesRelationshipGrantFromCanonicalDenial` |
| Retrieval crosses one Asset-owned query for existence, active authorization scopes, and current catalog projection without importing Asset repositories | `JpaKnowledgeAssetRetrievalQueryTests`, `ModulithVerificationTests#retrievalDoesNotDependOnAssetRepositories`, `#retrievalAssetReadsUseOnlyTheOwnerQuery`, `ExternalPrincipalRetrievalIntegrationTests`, `PermissionsAdminIntegrationTests` |
| Retrieval reloads active persisted department and Executive facts through Organization-owned queries and imports no Organization persistence or role types | `JpaKnowledgeAccessSubjectQueryTests`, `JpaOrganizationResourceQueryTests`, `ModulithVerificationTests#retrievalDoesNotDependOnOrganizationPersistenceOrRoleTypes`, `#retrievalOrganizationReadsUseOnlyOwnerQueries`, `SecureSourceVisibilityAdapterTests`, `KnowledgeRetrievalIntegrationTests` |
| Citation opening consumes Source Ledger-owned immutable evidence without revision/blob persistence leakage, preserves typed unavailable audit reasons, and closes integrity-mismatched content with a deny audit before any allow audit | `SourceCitationEvidenceQueryTests`, `CitationContentServiceTests#storageIntegrityMismatchClosesContentAndRecordsDenyAudit`, `ModulithVerificationTests#retrievalDoesNotDependOnSourceLedgerCitationPersistenceOrStatusTypes`, `#citationContentUsesTheSourceLedgerOwnerQuery` |
| Graph exploration, export, and curation consume only Retrieval's verifier and immutable snapshot; unknown Spaces fail closed, rechecks carry only the requested Space's assets, and the verifier accepts only one exact current governing-evidence candidate | `CanonicalGraphEvidenceVerifierTests`, `KnowledgeGraphExplorerServiceTests`, `KnowledgeGraphExportServiceTests`, `KnowledgeGraphCurationServiceTests`, `ModulithVerificationTests#graphConsumesOnlyRetrievalGraphContracts` |
| API and Worker adapter contracts are interfaces, bounded Asset inspection does not expose the full evidence scope, default/JDBC implementations cannot be imported, and each deployable pins its exact Retrieval dependency surface | `ModulithVerificationTests#retrievalAdapterContractsAreInterfaces`, `apps/api/.../RetrievalAdapterBoundaryTests`, `apps/worker/.../RetrievalAdapterBoundaryTests`, focused engine/content/scope/registry tests, API controller/configuration tests, Worker connector and ingestion integration tests |
| Retrieval is closed with an exact outgoing allowlist and exposes only its intentional root API; persistence/runtime types remain non-public | `ModulithVerificationTests#knowledgeRetrievalIsAClosedNestedModule`, `#knowledgeRetrievalExposesOnlyIntentionalRootApi`, `#modulesAreWellFormed`, focused registry/retrieval tests and API/Worker integration tests |

Request-boundary missing control role/incomplete actor returns `403`; generic
resource `404` does not claim otherwise. Provider-backed evaluation,
high-concurrency revocation latency, and export leak tests remain gaps.
