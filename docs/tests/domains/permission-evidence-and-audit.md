# Permission Evidence And Audit Coverage

Source: `core/src/test/java/com/orgmemory/core/permission`,
`core/src/test/java/com/orgmemory/core/authorization`,
`core/src/test/java/com/orgmemory/core/knowledge`,
`apps/api/src/test/java/com/orgmemory/api/admin`, and
`integrations/authorization-openfga/src/test`.

Reconciled: `2026-07-29-multi-provider-model-control-plane (d7ca979)`.

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
