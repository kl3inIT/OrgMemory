# Secure Retrieval Coverage

Source: `core/src/test/java/com/orgmemory/core/knowledge`,
`core/src/test/java/com/orgmemory/core/permission`,
`apps/api/src/test/java/com/orgmemory/api/knowledge`, and
`integrations/authorization-openfga/src/test`.

Reconciled: `2026-08-01-spring-modulith-package-refactor (7cef296c)`.

Primary evidence: `apps/api/src/test/java/com/orgmemory/api/knowledge/KnowledgeRetrievalIntegrationTests.java` and `core/src/test/java/com/orgmemory/core/permission/KnowledgePermissionPolicyTests.java`.

| Behavior | Automated evidence |
| --- | --- |
| SQL authorization before keyword/limit | `listAndKeywordSearchReturnOnlySqlAuthorizedAssets` |
| Eligible reader gets generic missing/denied 404 | `detailAllowsVisibleContentAndUsesGenericNotFoundForEveryDenial` |
| Admin is not Executive | `controlPlaneAdminIsNotExecutiveButBusinessExecutiveCanReadRestricted` |
| Confidential missing/foreign department fails closed | `nullDepartmentExecutiveCannotUseRoleToBypassConfidentialDepartmentRequirement`, policy tests |
| Restriction revokes before retrieval | `aclHeadRotationRevokesAssetBeforeKeywordAndContentRetrieval` |
| Later expansion cannot exceed ingestion ceiling | `widerCurrentAclCannotOverrideTheIngestionSnapshotDeny` |
| Refreshed head preserves availability without widening | `refreshedHeadKeepsAssetAvailableAfterHistoricalSnapshotExpiresWithoutWideningIt` |
| Denial and query/source decisions are audited without raw query | `controlPlaneRoleDenialIsAudited`, `searchAuditsQueryAndEveryReturnedSourceWithoutRawQuery` |
| Graph entity/relation/chunk evidence closure is rechecked before prompt rendering | `GraphRagKnowledgeRetrievalServiceTests#verifiesTheCompleteGraphGroundingBeforeCreatingTheModelInput` |
| OpenFGA model mismatch cannot reach the renderer | `GraphRagKnowledgeRetrievalServiceTests#authorizationModelMismatchCannotReachTheVerifiedRenderer` |
| Authorization scope changing during retrieval retries without egress | `GraphRagKnowledgeRetrievalServiceTests#revocationBetweenRetrievalAndCitationCausesAFullRetryWithoutEgress` |
| Parent Knowledge exposes only the exact four-type `knowledge::search` contract, and Assistant plus Asset Registry do not import Retrieval implementation | `ModulithVerificationTests#searchIsAnExactExplicitKnowledgeInterface`, `#topLevelSearchConsumersUseOnlyTheParentSearchInterface`, `#assistantAndAssetRegistryDoNotDependOnRetrievalImplementation` |
| Retrieval crosses one Asset-owned query for existence, active authorization scopes, and current catalog projection without importing Asset repositories | `JpaKnowledgeAssetRetrievalQueryTests`, `ModulithVerificationTests#retrievalDoesNotDependOnAssetRepositories`, `#retrievalAssetReadsUseOnlyTheOwnerQuery`, `ExternalPrincipalRetrievalIntegrationTests`, `PermissionsAdminIntegrationTests` |
| Retrieval reloads active persisted department and Executive facts through Organization-owned queries and imports no Organization persistence or role types | `JpaKnowledgeAccessSubjectQueryTests`, `JpaOrganizationResourceQueryTests`, `ModulithVerificationTests#retrievalDoesNotDependOnOrganizationPersistenceOrRoleTypes`, `#retrievalOrganizationReadsUseOnlyOwnerQueries`, `SecureSourceVisibilityAdapterTests`, `KnowledgeRetrievalIntegrationTests` |
| Citation opening consumes Source Ledger-owned immutable evidence without revision/blob persistence leakage, preserves typed unavailable audit reasons, and closes integrity-mismatched content before an allow audit | `SourceCitationEvidenceQueryTests`, `CitationContentServiceTests`, `ModulithVerificationTests#retrievalDoesNotDependOnSourceLedgerCitationPersistenceOrStatusTypes`, `#citationContentUsesTheSourceLedgerOwnerQuery` |

Request-boundary missing control role/incomplete actor returns `403`; generic
resource `404` does not claim otherwise. Provider-backed evaluation,
high-concurrency revocation latency, and export leak tests remain gaps.
