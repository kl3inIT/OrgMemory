# Permission Evidence And Audit Coverage

| Behavior | Evidence |
| --- | --- |
| ACL append/seal/rotation/stale-writer invariants | `KnowledgeIngestionIntegrationTests` rotation and mutation tests |
| Audit stores fingerprint, not raw query | `PermissionAuditIntegrationTests#appendsAuditEventAndStoresOnlyQueryFingerprint` |
| Audit survives outer rollback | `#requiresNewAuditCommitSurvivesOuterRollback` |
| Database rejects update/delete/truncate | `#databaseRejectsUpdateDeleteAndTruncate` |
| Database rejects free-form metadata | `#databaseRejectsFreeFormAuditMetadata` |
| Permission matrix/fail-closed rules | `KnowledgePermissionPolicyTests` |
| Multi-document all-sources-allowed rule | `PermissionDatasetValidatorTests#allDocumentsInMultiDocumentEvaluationMustBeAllowed` |
