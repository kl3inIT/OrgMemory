# Asset Registry Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| Prompt, Work Instruction, and Pack schemas reject invalid payloads | `AssetProfileValidationTests` | covered |
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
| Prompt provider execution requires explicit confirmation | `AssistantAssetToolServiceTests#promptRunRequiresExplicitProviderConfirmation` | covered |
| Assistant traces retain shapes/digests but not raw secrets or output | `AssistantAssetToolServiceTests#promptTraceStoresShapeAndDigestButNoRawSecretOrOutput` | covered |
| Assistant action registry has no governance or arbitrary-execution path | `AssistantAssetToolServiceTests#assistantActionRegistryHasNoGovernanceOrArbitraryExecutionPath` | covered |
| Delivery returns immutable release metadata and keeps denied IDs opaque | `AssetRegistryIntegrationTests#deliveryReturnsOnlyImmutableReleaseDataAndKeepsDeniedIdsOpaque` | covered |
| Read-only Pack description creates no assignment or progress | `AssetRegistryIntegrationTests#workInstructionAcknowledgementAndPackProgressAreIdempotentAndPinned` | covered |
| Delivery endpoints require `assets:read` or `assets:use` independently | `AssetDeliveryControllerSecurityTests` | covered |
| MCP publishes six read-only Asset tools, two resource templates, and one Prompt adapter | `OrgMemoryMcpContextTests`, `AssetDeliveryToolsTests` | covered |
| MCP forwards the actor bearer only to the canonical delivery API and hides denial bodies | `AssetDeliveryApiClientTests` | covered |
| MCP rejects wrong audience, issuer, and expired tokens | `McpTokenValidationTests` | covered |
| MCP advertises RFC 9728 metadata and challenges unauthenticated requests with its location | `OrgMemoryMcpContextTests` | covered |
| MCP applies a per-subject request limit without logging or returning tokens | `McpRateLimitFilterTests` | covered |
