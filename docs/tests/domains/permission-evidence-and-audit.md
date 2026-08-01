# Permission Evidence And Audit Coverage

Source: `core/src/test/java/com/orgmemory/core/permission`,
`core/src/test/java/com/orgmemory/core/authorization`,
`core/src/test/java/com/orgmemory/core/knowledge`,
`apps/api/src/test/java/com/orgmemory/api/admin`, and
`integrations/authorization-openfga/src/test`.

Reconciled: `2026-08-02-effective-access-inspector (c57bea58)`.

| Behavior | Evidence |
| --- | --- |
| ACL append/seal/rotation/stale-writer invariants | `KnowledgeIngestionIntegrationTests` rotation and mutation tests |
| Audit stores fingerprint, not raw query | `PermissionAuditIntegrationTests#appendsAuditEventAndStoresOnlyQueryFingerprint` |
| Audit survives outer rollback | `#requiresNewAuditCommitSurvivesOuterRollback` |
| Database rejects update/delete/truncate | `#databaseRejectsUpdateDeleteAndTruncate` |
| Database rejects free-form metadata | `#databaseRejectsFreeFormAuditMetadata` |
| Permission matrix/fail-closed rules | `KnowledgePermissionPolicyTests` |
| Denied evidence is omitted at the final citation boundary | `CanonicalHybridKnowledgeSearchTests#citationMissingAtCanonicalRecheckIsOmitted` |
| AI administration is limited to organization administrators | OpenFGA `store.fga.yaml` AI management case and `PermissionsAdminIntegrationTests` |
| AI secret storage is encrypted/redacted and tenant scoped | `AiGatewayAdministrationServiceTests` |
| Relationship allowance cannot be presented as final document access when canonical retrieval denies | `PermissionsAdminIntegrationTests#effectiveContentAccessSeparatesRelationshipGrantFromCanonicalDenial` |
| Member administration alone cannot inspect protected resource metadata | `PermissionsAdminIntegrationTests#memberAdministrationDoesNotRevealAccessInspectionMetadata` |
| Layered result names, relationship-only identifier suppression, and unresolved-state semantics | `access-inspector.test.tsx` |
