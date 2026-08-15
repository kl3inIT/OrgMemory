# Permission Evidence And Audit Coverage

Source: `core/src/test/java/com/orgmemory/core/permission`,
`core/src/test/java/com/orgmemory/core/authorization`,
`core/src/test/java/com/orgmemory/core/knowledge`,
`apps/api/src/test/java/com/orgmemory/api/admin`, and
`integrations/authorization-openfga/src/test`.

Reconciled: `admin-safe-deletion-controls (fcde1113)`.

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
| Relationship allowance cannot be presented as final document access when canonical retrieval denies, and the protected derivation carries tenant-owned document, Space, and department labels | `PermissionsAdminIntegrationTests#effectiveContentAccessSeparatesRelationshipGrantFromCanonicalDenial` |
| Member administration alone cannot inspect protected resource metadata | `PermissionsAdminIntegrationTests#memberAdministrationDoesNotRevealAccessInspectionMetadata` |
| Business-first current result, named assignment, document availability, evaluation time, technical-detail suppression, and unresolved-state semantics | `access-inspector.test.tsx` |
| Typed Space creation, built-in projection, immutable managed audiences, custom grant ledger, drift repair, and tenant isolation | `KnowledgeSpaceAdminIntegrationTests`, `KnowledgeSpaceAdministrationServiceTests` |
| Space retirement requires management permission, marks the row inactive without physical deletion, and removes it from the active administration list | `KnowledgeSpaceAdminIntegrationTests`, `KnowledgeSpaceAdministrationServiceTests` |
| Existing Space audience backfill and database-invalid mode/department combinations | `KnowledgeSpaceAudienceMigrationTests` |
| Space operational permissions do not imply Space or Knowledge Asset viewing | OpenFGA `store.fga.yaml` operational-independence checks |
