# Assistant And MCP Coverage

Source: `core/src/test/java/com/orgmemory/core/assistant`,
`core/src/test/java/com/orgmemory/core/ai`,
`integrations/ai-model-gateways/src/test`,
`apps/api/src/test/java/com/orgmemory/api/assistant`,
`apps/mcp/src/test/java/com/orgmemory/mcp`, and
`apps/web/src/features/assistant`, plus
`apps/web/test/e2e/assistant-pipeline.spec.ts`.

Reconciled: `2026-08-04-assistant-citation-evidence-continuity (9ec76d52)`.

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Assistant sends only permission-verified evidence to the model | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel` | covered |
| Assistant consumes only the parent `knowledge::search` contract and never imports Retrieval implementation | `ModulithVerificationTests#topLevelSearchConsumersUseOnlyTheParentSearchInterface`, `#assistantAndAssetRegistryDoNotDependOnRetrievalImplementation` | covered |
| Empty authorized retrieval does not call the model | `AssistantServiceTests#doesNotCallTheModelWhenNoAccessibleEvidenceExists` | covered |
| Provider failure is surfaced as unavailable | `AssistantServiceTests#asynchronousProviderFailureIsReportedAsUnavailable` | covered |
| Assistant and Prompt calls carry organization identity into route resolution | `AssistantServiceTests`, `PromptExecutionServiceTests` org-scoped model-port assertions | covered |
| Ordinary users receive only server-governed model options and selection writes an authorized opaque conversation reference | `AssistantControllerStreamingTests#exposesSafeModelChoicesAndPersistsOnlyAnAuthorizedSelectionReference`, `assistant-pipeline.spec.ts` governed-model scenarios | covered |
| Deployment default is synthetic; catalog authority binds exact route identity/version and rejects explicit-reasoning alternates | `AssistantModelAuthorityServiceTests` | covered |
| Disabled catalog activations cannot revive through concurrent disable, select, or turn races | `AssistantModelSelectionConcurrencyIntegrationTests#concurrentDisableSelectAndTurnNeverRevivesTheDisabledActivation` | covered |
| Conversation selection is complete and tenant-safe at the database boundary | `AssistantModelSelectionMigrationTests#conversationSelectionRequiresACompleteSameTenantActivationTuple` | covered |
| Direct LightRAG answer and Keyword planning resolve independent organization routes at request time, including changes within one process | `OrganizationAwareQueryAnswerModelTests`, `OrganizationAwareKeywordPlanningModelTests` | covered |
| Keyword cache identity changes with the organization route and explicit reasoning effort | `LightRagKeywordPlannerCacheTests`, `OrganizationAwareKeywordPlanningModelTests` | covered |
| An explicit organization route never silently falls back to the deployment provider | `AiGatewayPropertiesTests#anExplicitOrganizationRouteFailsClosedWhenItsGatewayIsUnavailable` | covered |
| Citation numbers are assigned with the exact prompt evidence order | `AssistantServiceTests#exposesCitationsOnlyForEvidenceIncludedInThePromptBudget`, `AssistantControllerStreamingTests`, `UiMessageStreamTests` | covered |
| Assistant uses the already-verified LightRAG prompt instead of rebuilding chunk context | `AssistantServiceTests#usesTheAlreadyVerifiedLightRagPromptWithoutRebuildingIt` | covered |
| Bounded model memory receives the raw question while current authorized evidence and safe user context stay in the current system message | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel`, `#escapesEvidenceAndProfileValuesWhileKeepingTheQuestionAsTheUserMessage` | covered |
| Only server-declared citation markers become interactive | `assistant-pipeline.spec.ts#anchors only server-declared citations and opens the matching source` | covered |
| Text, PDF, image, and Office download-only presentation follows the server kind and uses protected endpoints | `assistant-pipeline.spec.ts` text, PDF, image, and Office scenarios; `CitationEvidenceServiceTests` | covered |
| Markdown strips active HTML, blocks remote/data resources and dangerous URLs, renders Mermaid as inert code, and retains raw view | `assistant-pipeline.spec.ts#renders governed Markdown without active HTML or remote resource loads` | covered |
| Revoked citations produce one opaque 404 without leaking the backend detail | `assistant-pipeline.spec.ts#shows an opaque citation error after access is revoked`, `CitationContentWebMvcTests` | covered |
| Hostile upload media types cannot make citation content execute inline | `SourceUploadServiceTests#derivesTheStoredMediaTypeFromTheAllowlistedExtension`, `CitationContentControllerTests` | covered |
| Empty evidence, provider retry, and user abort are browser-tested | `assistant-pipeline.spec.ts` | covered |
| GraphRAG is selected explicitly with no silent fallback | `AssistantConfigurationTests` | covered |
| Application configuration declares no bean condition Boot evaluates before auto-configuration, so an observation handler cannot be silently dropped | `ConfigurationConditionTests#applicationConfigurationDoesNotUseBeanConditions` | covered |
| MCP publishes only read-only permission-aware contracts with object output schemas | `OrgMemoryMcpContextTests#publishesOnlyThePermissionAwareReadOnlyContracts` | covered |
| Completion is published for the released Prompt argument and both Asset resource templates | `OrgMemoryMcpContextTests#completesEveryPublishedPromptArgumentAndResourceTemplate` | covered |
| Completion suggests only authorized values, narrows releases to the resolved Asset, and stays empty when delivery refuses the identity | `AssetCompletionAdapterTests` | covered |
| A downstream failure surfaces as a sanitized tool error without internal host or request detail | `McpToolErrorSurfaceTests#downstreamFailureBecomesASanitizedToolErrorWithoutTransportDetail` | covered |
| Per-actor filtering of tool, prompt, and resource listings | none | not supported by the stateless annotation runtime |
| MCP exchanges caller identity and forwards only the API-audience bearer to canonical REST search | `McpApiAuthorizationTests`, `KnowledgeSearchApiClientTests`, `KnowledgeSearchToolTests` | covered |
| Asset recommendations are actor-scoped and pin an exact usable release | `AssetRegistryIntegrationTests#recommendationsAreActorScopedAndPinExactUsableReleases` | covered |
| Assistant-proposed external provider and state-changing Asset actions require confirmation | `AssistantAssetToolServiceTests` | covered |
| Asset tool traces contain exact release refs without raw Prompt secrets/output | `AssistantAssetToolServiceTests#promptTraceStoresShapeAndDigestButNoRawSecretOrOutput` | covered |
| Asset Assistant has no approval/publication/withdrawal/permission/arbitrary-execution action | `AssistantAssetToolServiceTests#assistantActionRegistryHasNoGovernanceOrArbitraryExecutionPath` | covered |
| Full transcript is actor-owned, replayed in order, and rejects another actor before writing | `AssistantConversationServiceTests` | covered |
| Completed answers atomically persist ordered citation mappings with composite ownership, uniqueness, and cascade deletion | `AssistantConversationServiceTests#persistsServerDeclaredCitationReferencesWithTheCompletedAnswer`, `AssistantMessageCitationMigrationTests` | covered |
| Citation hydration is transcript-independent, reloadable, actor-owned, bounded to 100, deduplicated, and current-authorization filtered | `AssistantConversationServiceTests` citation-reference scenarios, `CanonicalEvidenceAuthorizationServiceTests`, `CitationEvidenceServiceTests`, `assistant-pipeline.spec.ts#rehydrates currently authorized citations after transcript reload` | covered |
| Excerpts reauthorize current evidence, cap Unicode content, audit allow/deny, and keep missing/revoked/stale outcomes opaque | `CitationEvidenceServiceTests`, `CitationContentControllerTests`, `CitationContentWebMvcTests`, `assistant-pipeline.spec.ts` revocation scenario | covered |
| One server-owned answer UUID is shared by the stream and persisted transcript | `AssistantControllerStreamingTests#usesOneServerOwnedIdentityForTheStreamAndPersistedAnswer`, `AssistantConversationServiceTests#persistsTheServerAllocatedAssistantMessageIdentity`, `UiMessageStreamTests` | covered |
| Answer feedback creates, replaces, removes, and replays only against an owned assistant message | `AssistantConversationServiceTests`, `AssistantControllerStreamingTests#delegatesFeedbackThroughTheAuthenticatedActor`, `assistant-pipeline.spec.ts#creates, replaces, removes, and replays answer feedback` | covered |
| Feedback ownership and deletion are database-enforced across the message tenant/actor tuple | `AssistantAnswerFeedbackMigrationTests` | covered |
| Concurrent feedback set/set and set/delete mutations serialize on the owned assistant message | `AssistantAnswerFeedbackConcurrencyIntegrationTests` against PostgreSQL | covered |
| Starter prompts come from the server rather than a browser constant | `AssistantControllerStreamingTests#publishesClosedServerOwnedStarters`, `assistant-pipeline.spec.ts#loads server-owned starters and restores a session-scoped draft with focus` | covered |
| Drafts are actor/conversation scoped, capped, restored in-session, and cleared through their lifecycle | `assistant-draft-storage.test.ts`, `assistant-pipeline.spec.ts#loads server-owned starters and restores a session-scoped draft with focus` | covered |
| Completed-answer retry starts exactly one fresh turn without consuming the current composer draft | `assistant-pipeline.spec.ts#retries a completed answer as one fresh turn and preserves the composer draft` | covered |
| Composer focus and leave-bottom scroll recovery remain keyboard/browser reachable | `assistant-pipeline.spec.ts` focus and scroll-recovery scenarios | covered |
| Empty-state hierarchy removes decorative permission copy and the searchable model dialog sends only an opaque activation UUID | `assistant-pipeline.spec.ts#chooses a governed model in the composer and sends only its opaque activation` | covered |
| An in-place actor change hides and clears the prior actor's transcript, feedback, and source state before new history renders | `assistant-pipeline.spec.ts#clears conversation state before rendering a different actor's history` | covered |
| Time to first token counts the permission-scoped retrieval the user waits through | `AssistantTurnObservationTests#countsTheWaitBeforeTheModelIsEvenAsked` | covered |
| Retrieval has its own latency distribution, while transient activity does not become TTFT | `AssistantTurnObservationTests#recordsPermissionScopedRetrievalSeparatelyFromModelLatency`, `UiMessageStreamTests` | covered |
| Stream start and retrieval-active reach the client while blocking retrieval is still running | `AssistantControllerStreamingTests#emitsStreamStartAndRetrievalActivityWhileRetrievalIsStillBlocked` | covered |
| Blocking retrieval uses a bounded scheduler whose overload is sanitized and whose cancellation interrupts active work | `AssistantRetrievalSchedulerTests` | covered |
| Time to first token stops at the first token, not the last | `AssistantTurnObservationTests#stopsAtTheFirstTokenRatherThanTheLast` | covered |
| A turn that emitted nothing records no latency sample | `AssistantTurnObservationTests#recordsNothingWhenNoTokenEverReachedTheCaller`, `#recordsNothingWhenThereWasNoAccessibleEvidenceToAnswerFrom` | covered |
| Assistant meters carry no tenant, request or conversation identifier | `AssistantTurnObservationTests#carriesNoIdentifierThatWouldGrowASeriesPerTenantOrRequest` | covered |
| The turn event structurally refuses free text where a failure code belongs | `AssistantTurnEventTests` | covered |
| Every row above about time to first token holds against the handler in isolation and held while the handler was never registered in a running application; only `ConfigurationConditionTests` and production traffic distinguish the two | `AssistantTurnObservationTests` construct the handler directly | partial |
| Every meter a dashboard charts as a quantile publishes a bounded percentile histogram | `MetricsDistributionTests` | covered |
| General chat-turn idempotency | none | not implemented |
