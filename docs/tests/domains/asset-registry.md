# Asset Registry Coverage

Source: `core/src/test/java/com/orgmemory/core/assetregistry`,
`apps/api/src/test/java/com/orgmemory/api/assetregistry`,
`apps/mcp/src/test/java/com/orgmemory/mcp`, `apps/cli/src/*.test.ts`, and
`apps/web/src/features/assets/**/*.test.ts`.

Reconciled: `2026-07-29-repository-operating-model-refresh (7cf1c8a)`.

| Behavior | Evidence | Status |
| --- | --- | --- |
| Prompt, Work Instruction, Pack, and Skill schemas reject invalid payloads | `AssetProfileValidationTests` | covered |
| Skill ZIP inspection rejects traversal, case collisions, symlinks, invalid frontmatter, invalid UTF-8, and bounded-size violations without extraction | `SkillPackageInspectorTests` | covered |
| Unauthorized Skill import is rejected before object storage and pre-identity failures clean up staged objects | `SkillRegistryServiceTests` | covered |
| A projection retry retains the already-referenced Skill object rather than deleting it | `SkillRegistryServiceTests#retainsReferencedBytesWhenAuthorizationProjectionNeedsRetry` | covered |
| Skill storage uses an organization-scoped object key and verifies the stored SHA-256 | `MinioSkillPackageStorageAdapterTests` | covered |
| Skill submission and publication pin the same exact blob reference and digest | `AssetRegistryIntegrationTests#skillImportPinsTheValidatedBlobToRevisionAndRelease` | covered |
| Exact Skill manifests omit storage keys and package streaming rejects payload, release-reference, and stored-object mismatches | `SkillDistributionServiceTests`, `SkillDistributionControllerTests`, `MinioSkillPackageStorageAdapterTests` | covered |
| Browser Skill detail reads the exact manifest through an OIDC-session-only endpoint without weakening bearer `assets:read` admission | `AssetConsumptionControllerTests`, `asset-registry-golden-poc.spec.ts` | covered |
| Method-level authorization denial returns a stable opaque HTTP 403 instead of an internal HTTP 500 | `ApiExceptionHandlerTests#methodAuthorizationDenialUsesTheStableForbiddenContract` | covered |
| MCP Skill discovery and binary proxy retain bearer admission and exchanged API authorization | `SkillPackageControllerTests`, `AssetDeliveryControllerSecurityTests` | covered |
| CLI installation verifies package and per-file digests, promotes atomically, writes a token-free receipt, and preserves an active install on tampering | `install.test.ts` | covered |
| CLI authoring validates a root Skill folder and produces deterministic bounded ZIP bytes before authentication | `skill-package.test.ts` | covered |
| CLI Draft publication uses the same-origin companion route, separate write-scoped OAuth state, bounded errors, and no network access for dry-run | `publish.test.ts`, `FileOAuthClientProvider`, CLI command contract | covered |
| CLI Draft publication returns an exact same-origin Governance URL for the created Asset | `publish.test.ts` | covered |
| Skill publication requires `assets:write`, uses the publisher token-exchange registration, bounds multipart size, and delegates only to canonical Skill import | `OrgMemoryMcpContextTests`, `SkillPublicationControllerTests`, `SkillPublicationApiClientTests`, `McpApiAuthorizationTests`, `McpRateLimitFilterTests` | covered |
| Governance action discovery first requires Asset visibility and reports actor-specific submit, review, publish, and withdrawal affordances without granting authority | `AssetRegistryServiceTests#governanceActionsUseLivePermissionsAfterRequiringAssetView` | covered |
| A newly published Skill Draft exposes bounded package identity, submits with a required change note, and moves to review without offering author self-approval | `governance-policy.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
| Prompt rendering is deterministic and validates typed variables | `PromptTemplateRendererTests` | covered |
| Inserted variables and grounding remain untrusted data | `PromptTemplateRendererTests`, `PromptExecutionServiceTests` | covered |
| Prompt output contract and bounded evaluations expose pass/failure | `PromptExecutionServiceTests`, `AssetRegistryIntegrationTests` | covered |
| Prompt runs pin exact release, digest, and model route | `AssetRegistryIntegrationTests` | covered |
| Raw sensitive values and raw output are absent from default persistence | `AssetRegistryIntegrationTests` | covered |
| Withdrawn Prompt releases cannot start new runs | `AssetRegistryIntegrationTests` | covered |
| Prompt provider tests run without network access | `PromptExecutionServiceTests` | covered |
| Work Instruction acknowledgement is actor-derived and idempotent | `AssetRegistryIntegrationTests` | covered |
| Pack items preserve order and exact release/version pins | `CapabilityPackServiceTests`, `AssetRegistryIntegrationTests` | covered |
| Every Pack component is authorized independently | `CapabilityPackServiceTests` | covered |
| Denied Pack component metadata and count remain opaque | `CapabilityPackServiceTests` | covered |
| Knowledge catalog and Prompt grounding use canonical authorization | `KnowledgeCatalogServiceTests`, `PromptExecutionServiceTests` | covered |
| Pack progress is actor-derived and idempotent | `AssetRegistryIntegrationTests` | covered |
| Replacement releases do not mutate existing Pack pins | `AssetRegistryIntegrationTests` | covered |
| Recommendations are actor-scoped and contain exact usable release refs | `AssetRegistryIntegrationTests#recommendationsAreActorScopedAndPinExactUsableReleases`, `AssistantAssetToolServiceTests#recommendationsContainOnlyExactUsableReleaseRefs` | covered |
| Asset catalog total, filtering, stable name sorting, and pages are evaluated over the authorized latest-release set | `AssetRegistryIntegrationTests#catalogPagesAndSortsTheLatestAuthorizedReleasesOnTheServer` | covered |
| Shared collection pagination hides a one-page collection and emits server page changes with an accessible current page | `collection-pagination.test.tsx` | covered |
| Prompt provider execution requires explicit confirmation | `AssistantAssetToolServiceTests#promptRunRequiresExplicitProviderConfirmation` | covered |
| Assistant traces retain shapes/digests but not raw secrets or output | `AssistantAssetToolServiceTests#promptTraceStoresShapeAndDigestButNoRawSecretOrOutput` | covered |
| Assistant action registry has no governance or arbitrary-execution path | `AssistantAssetToolServiceTests#assistantActionRegistryHasNoGovernanceOrArbitraryExecutionPath` | covered |
| Delivery returns immutable release metadata and keeps denied IDs opaque | `AssetRegistryIntegrationTests#deliveryReturnsOnlyImmutableReleaseDataAndKeepsDeniedIdsOpaque` | covered |
| Read-only Pack description creates no assignment or progress | `AssetRegistryIntegrationTests#workInstructionAcknowledgementAndPackProgressAreIdempotentAndPinned` | covered |
| Every read-only delivery endpoint, including deterministic Prompt render, requires `assets:read` | `AssetDeliveryControllerSecurityTests` | covered |
| MCP publishes eight read-only Asset tools, two resource templates, and one Prompt adapter | `OrgMemoryMcpContextTests`, `AssetDeliveryToolsTests` | covered |
| Every MCP tool publishes an MCP 2025-11-25-compatible object-root output schema accepted by strict clients | `OrgMemoryMcpContextTests` | covered |
| MCP exchanges the actor bearer for an API-audience token and hides denial bodies | `McpApiAuthorizationTests`, `AssetDeliveryApiClientTests` | covered |
| MCP rejects wrong audience, issuer, and expired tokens | `McpTokenValidationTests` | covered |
| MCP advertises RFC 9728 read/write scope metadata and challenges unauthenticated MCP and publication requests with its location | `OrgMemoryMcpContextTests`, `smoke-production.sh` | covered |
| MCP applies bounded per-caller and process-wide token buckets plus known/chunked body limits without returning tokens | `McpRateLimitFilterTests` | covered |
| Public discovery reaches MCP through nginx and deployment smoke requires DCR authorization metadata without the incompatible CIMD advertisement | `test-web-forwarded-port.sh`, `smoke-production.sh`, `test-keycloak-mcp-onboarding.sh` | covered |
| Generic MCP onboarding renders Claude, Codex, and compatible-client instructions without a client secret | `mcp-connect.spec.ts` | covered |
| Golden L1 Support fixture passes eight bounded Prompt cases with permission-aware grounding | `AssetRegistryIntegrationTests#goldenPocTransfersAReleasedSupportCapabilityToASecondUser`, `demo/fixtures/asset-registry` | covered |
| Second-user discovery, exact-release Prompt use, Work Instruction acknowledgement, Pack completion, replacement pin stability, and withdrawal are one integrated flow | `AssetRegistryIntegrationTests#goldenPocTransfersAReleasedSupportCapabilityToASecondUser` | covered |
| Active owner/backup coverage derives an explicit orphaned and continuity-risk flag | `AssetRegistryIntegrationTests#goldenPocTransfersAReleasedSupportCapabilityToASecondUser`, `AssetIdentityHeader` | covered |
| Owner governance and second-user Pack completion render in separate real browser sessions | `asset-registry-golden-poc.spec.ts` | covered |
| Generic denial stays opaque across REST, Assistant, and MCP | `AssetRegistryIntegrationTests#unauthorizedAndCrossTenantIdsAreOpaqueWhileListIntersectsCanonicalRows`, `AssetDeliveryApiClientTests`, `CapabilityPackServiceTests` | covered |
