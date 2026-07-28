# Assistant And MCP Coverage

Source: `core/src/test/java/com/orgmemory/core/assistant`,
`apps/api/src/test/java/com/orgmemory/api/assistant`,
`apps/mcp/src/test/java/com/orgmemory/mcp`, and
`apps/web/src/features/assistant`.

Reconciled: `2026-07-29-repository-operating-model-refresh (7cf1c8a)`.

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Assistant sends only permission-verified evidence to the model | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel` | covered |
| Empty authorized retrieval does not call the model | `AssistantServiceTests#doesNotCallTheModelWhenNoAccessibleEvidenceExists` | covered |
| Provider failure is surfaced as unavailable | `AssistantServiceTests#asynchronousProviderFailureIsReportedAsUnavailable` | covered |
| Citation numbers are assigned with the exact prompt evidence order | `AssistantServiceTests#exposesCitationsOnlyForEvidenceIncludedInThePromptBudget`, `AssistantControllerStreamingTests`, `UiMessageStreamTests` | covered |
| Assistant uses the already-verified LightRAG prompt instead of rebuilding chunk context | `AssistantServiceTests#usesTheAlreadyVerifiedLightRagPromptWithoutRebuildingIt` | covered |
| Bounded model memory receives the raw question while current authorized evidence and safe user context stay in the current system message | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel`, `#escapesEvidenceAndProfileValuesWhileKeepingTheQuestionAsTheUserMessage` | covered |
| Only server-declared citation markers become interactive | `assistant-pipeline.spec.ts#anchors only server-declared citations and opens the matching source` | covered |
| Text and PDF sources are fetched once through the protected endpoint | `assistant-pipeline.spec.ts` text and PDF preview scenarios | covered |
| Revoked citations produce one opaque 404 without leaking the backend detail | `assistant-pipeline.spec.ts#shows an opaque citation error after access is revoked`, `CitationContentWebMvcTests` | covered |
| Hostile upload media types cannot make citation content execute inline | `SourceUploadServiceTests#derivesTheStoredMediaTypeFromTheAllowlistedExtension`, `CitationContentControllerTests` | covered |
| Empty evidence, provider retry, and user abort are browser-tested | `assistant-pipeline.spec.ts` | covered |
| GraphRAG is selected explicitly with no silent fallback | `AssistantConfigurationTests` | covered |
| MCP publishes only the read-only permission-aware search tool | `OrgMemoryMcpContextTests` | covered |
| MCP exchanges caller identity and forwards only the API-audience bearer to canonical REST search | `McpApiAuthorizationTests`, `KnowledgeSearchApiClientTests`, `KnowledgeSearchToolTests` | covered |
| Asset recommendations are actor-scoped and pin an exact usable release | `AssetRegistryIntegrationTests#recommendationsAreActorScopedAndPinExactUsableReleases` | covered |
| Assistant-proposed external provider and state-changing Asset actions require confirmation | `AssistantAssetToolServiceTests` | covered |
| Asset tool traces contain exact release refs without raw Prompt secrets/output | `AssistantAssetToolServiceTests#promptTraceStoresShapeAndDigestButNoRawSecretOrOutput` | covered |
| Asset Assistant has no approval/publication/withdrawal/permission/arbitrary-execution action | `AssistantAssetToolServiceTests#assistantActionRegistryHasNoGovernanceOrArbitraryExecutionPath` | covered |
| Full transcript is actor-owned, replayed in order, and rejects another actor before writing | `AssistantConversationServiceTests` | covered |
| General chat-turn idempotency | none | not implemented |
