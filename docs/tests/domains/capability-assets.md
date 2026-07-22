# Capability Assets Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| Create persists current version and usage | `CapabilityAssetServiceIntegrationTests#createAssetCreatesVersionAndCanTrackUsage` | covered |
| Draft can be reviewed and approved | `#reviewWorkflowMovesDraftToApproved` | covered |
| Server actor overrides client identity | `#createUsesLinkedActorInsteadOfClientSuppliedIdentity` | covered |
| Contributor cannot approve; reviewer identity is derived | `#contributorCannotApproveButReviewerCanAndReviewerIdIsDerived` | covered |
| Viewer create/team visibility denial | `#viewerCannotCreateAndCannotReadAnotherTeamsAsset` | covered |
| Collection authorization and usage avoid per-row calls | `#listUsesSetAuthorizationInsteadOfPerAssetChecks`, `OpenFgaRelationshipAuthorizationSetAdapterTests#batchCheckSendsResourceSpecificContextualRelationships` | covered |
| Invalid lifecycle transitions are rejected | no entity guard or automated test | gap — transition methods currently assign unconditionally |
| Full asset-quality contract is enforced | no validator/test | gap — product intent only |
