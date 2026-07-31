# Asset Registry Coverage

Source: `core/src/test/java/com/orgmemory/core/assetregistry`,
`apps/api/src/test/java/com/orgmemory/api/assetregistry`,
`apps/mcp/src/test/java/com/orgmemory/mcp`, `apps/cli/src/*.test.ts`, and
`apps/web/src/features/assets/**/*.test.ts`.

Reconciled: `2026-08-01-browser-skill-authoring (250e1705)`.

| Behavior | Evidence | Status |
| --- | --- | --- |
| Prompt, Work Instruction, Pack, and Skill schemas reject invalid payloads | `AssetProfileValidationTests` | covered |
| Skill ZIP inspection rejects traversal, case collisions, symlinks, invalid frontmatter, invalid UTF-8, and bounded-size violations without extraction | `SkillPackageInspectorTests` | covered |
| Unauthorized Skill import is rejected before object storage and pre-identity failures clean up staged objects | `SkillRegistryServiceTests` | covered |
| Stateless Skill inspection returns canonical bounded metadata without storage; Scratch, raw `SKILL.md`, ZIP, and folder packaging converge on the same server validator | `SkillRegistryServiceTests#inspectionIsStatelessAndReturnsOnlyValidatedPackageFacts`, `skill-package-browser.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
| GitHub Skill preview and private-connection discovery require Skill-create permission on the selected Knowledge Space, pin a full commit SHA, discover nearest bounded `SKILL.md` roots, reject unsafe/link/colliding archives, and keep invalid candidates independently visible | `GitHubSkillArchiveReaderTests`, `GitHubSkillSourceAdapterTests`, `SkillGitHubImportServiceTests`, `asset-registry-golden-poc.spec.ts#GitHub Skill import pins preview, supports private access, and reports partial results` | covered |
| Private GitHub import requires an administrator opt-in and selected GitHub App repository, fails closed on missing repository identifiers, audits allow/deny credential use, distinguishes rate limits from private repositories, applies HTTP timeouts, disables generic redirects, validates the single codeload redirect, strips Authorization before archive download, and enforces archive-size bounds | `GitHubSkillSourceAdapterTests`, `connector-github.test.ts` | covered |
| Selected GitHub Skills import in independent transactions, persist server-derived repository/SHA/path provenance, and return partial success without rolling back completed Drafts | `SkillGitHubImportServiceTests#importsEachSelectedSkillIndependentlyAndPreservesPinnedProvenance`, `asset-registry-golden-poc.spec.ts#GitHub Skill import pins preview, supports private access, and reports partial results` | covered |
| Skill Draft replacement requires live edit authorization plus the expected Draft version, compensates fresh storage on transaction failure, and never mutates an immutable package reference | `SkillRegistryServiceTests`, `AssetRegistryIntegrationTests#replacingAReleasedSkillDraftKeepsTheImmutablePackageAndClearsTheCleanupRow`, `AssetRegistryIntegrationTests#replacingAnUnreleasedSkillDraftDeletesItsUnreferencedOldPackage` | covered |
| Database mutation guards allow only Draft-reference deletion; payload-reference update and Revision/Release deletion remain rejected | `AssetRegistryIntegrationTests#onlyDraftPayloadReferencesMayBeDeletedWhileAllReferenceUpdatesStayRejected` | covered |
| Post-commit supersession cleanup deletes only an exact unreferenced object, retains immutable pins, and durably schedules bounded retries after storage failure | `SkillPackageSupersessionCleanupCoordinatorTests` | covered |
| A projection retry retains the already-referenced Skill object rather than deleting it | `SkillRegistryServiceTests#retainsReferencedBytesWhenAuthorizationProjectionNeedsRetry` | covered |
| Skill storage uses an organization-scoped object key and verifies the stored SHA-256 | `MinioSkillPackageStorageAdapterTests` | covered |
| Direct Skill publication atomically creates one Revision and Release, pins the exact validated blob through Draft, Revision, and Release, records `DIRECT` provenance, and emits the dedicated audit policy | `AssetRegistryIntegrationTests#skillImportPublishesDirectlyAndPinsTheValidatedBlob` | covered |
| An active Skill review blocks direct publication rather than becoming an approval bypass | `AssetRegistryIntegrationTests#directSkillPublicationDoesNotBypassAnActiveReview` | covered |
| The direct command rejects every non-Skill Asset profile | `AssetRegistryIntegrationTests#directSkillPublicationRejectsEveryOtherAssetProfile` | covered |
| Exact Skill manifests omit storage keys and package streaming rejects payload, release-reference, and stored-object mismatches | `SkillDistributionServiceTests`, `SkillDistributionControllerTests`, `MinioSkillPackageStorageAdapterTests` | covered |
| Browser Skill detail reads the exact manifest through an OIDC-session-only endpoint without weakening bearer `assets:read` admission | `AssetConsumptionControllerTests`, `asset-registry-golden-poc.spec.ts` | covered |
| Method-level authorization denial returns a stable opaque HTTP 403 instead of an internal HTTP 500 | `ApiExceptionHandlerTests#methodAuthorizationDenialUsesTheStableForbiddenContract` | covered |
| MCP Skill discovery and binary proxy retain bearer admission and exchanged API authorization | `SkillPackageControllerTests`, `AssetDeliveryControllerSecurityTests` | covered |
| CLI installation verifies package and per-file digests, promotes atomically, writes a token-free receipt, and preserves an active install on tampering | `install.test.ts` | covered |
| CLI authoring validates a root Skill folder and produces deterministic bounded ZIP bytes before authentication | `skill-package.test.ts` | covered |
| CLI Draft publication uses the same-origin companion route, separate write-scoped OAuth state, bounded errors, and no network access for dry-run | `publish.test.ts`, `FileOAuthClientProvider`, CLI command contract | covered |
| CLI Draft publication returns an exact same-origin Governance URL for the created Asset | `publish.test.ts` | covered |
| Skill publication requires `assets:write`, uses the publisher token-exchange registration, bounds multipart size, and delegates only to canonical Skill import | `OrgMemoryMcpContextTests`, `SkillPublicationControllerTests`, `SkillPublicationApiClientTests`, `McpApiAuthorizationTests`, `McpRateLimitFilterTests` | covered |
| Governance action discovery first requires Asset visibility and reports actor-specific edit, submit, review, reviewed publish, direct Skill publish, and withdrawal affordances without granting authority | `AssetRegistryServiceTests#governanceActionsUseLivePermissionsAfterRequiringAssetView` | covered |
| A newly imported Skill Draft exposes bounded package identity and the accountable author publishes a version directly; the UI records and displays `DIRECT` provenance and hides an empty review journey | `governance-policy.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
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
| The owned-Asset workspace includes Drafts, requires an active direct `OWNER` assignment, intersects ownership with live `can_view`, and remains server-paged | `AssetRegistryServiceTests#ownedWorkspaceResolvesVisibilityAndCanonicalOwnerAssignments`, `AssetRegistryIntegrationTests#ownedWorkspaceIncludesDraftsAndUsesOnlyActiveOwnerAssignments` | covered |
| Asset type projection exposes every governed profile, reports the active filter, clears to the shared catalog, and preserves the grid-default URL plus explicit list state | `asset-type-filter.test.tsx`, `asset-catalog-state.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
| The Assets page keeps search and `All Assets | My Assets` as its primary navigation, encodes only the non-default owned scope in the URL, and stacks the controls without horizontal overflow on mobile | `asset-catalog-state.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
| Shared collection pagination hides a one-page collection and emits server page changes with an accessible current page | `collection-pagination.test.tsx` | covered |
| The Asset catalog exposes one category-aware Add asset menu, keeps unsupported profiles non-interactive, and routes Skill into a creation-only surface rather than a second catalog | `asset-registry-golden-poc.spec.ts#asset catalog defaults to a grid and keeps list state in the URL` | covered |
| Browser Skill Scratch and Upload require fresh server inspection, invalidate stale scratch previews, preflight package/namespace input, load live authorized Space targets, retain same-origin multipart CSRF protection, create a private Draft through the canonical endpoint, report public server rejection details without leaving the form, and navigate success to Governance | `api-error.test.ts`, `skill-upload-validation.test.ts`, `skill-package-browser.test.ts`, `asset-registry-golden-poc.spec.ts` | covered |
| The Governance Draft surface exposes package replacement only with live `can_edit`; successful replacement returns to the same Asset workspace while optimistic conflicts remain explicit | `asset-registry-golden-poc.spec.ts#Skill publication hands the author to capability-aware Governance` | covered |
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
