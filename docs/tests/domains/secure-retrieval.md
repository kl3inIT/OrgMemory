# Secure Retrieval Coverage

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

Request-boundary missing control role/incomplete actor returns `403`; generic
resource `404` does not claim otherwise. Hybrid/vector, OpenFGA, agent, MCP,
graph, citation, and export leak tests remain gaps.
