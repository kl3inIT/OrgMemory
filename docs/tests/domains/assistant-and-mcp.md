# Assistant And MCP Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Assistant sends only permission-verified evidence to the model | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel` | covered |
| Empty authorized retrieval does not call the model | `AssistantServiceTests#doesNotCallTheModelWhenNoAccessibleEvidenceExists` | covered |
| Provider failure is surfaced as unavailable | `AssistantServiceTests#asynchronousProviderFailureIsReportedAsUnavailable` | covered |
| Citation numbers are assigned with the exact prompt evidence order | `AssistantServiceTests#exposesCitationsOnlyForEvidenceIncludedInThePromptBudget`, `AssistantControllerStreamingTests`, `UiMessageStreamTests` | covered |
| Only server-declared citation markers become interactive | `assistant-pipeline.spec.ts#anchors only server-declared citations and opens the matching source` | covered |
| Text and PDF sources are fetched once through the protected endpoint | `assistant-pipeline.spec.ts` text and PDF preview scenarios | covered |
| Revoked citations produce one opaque 404 without leaking the backend detail | `assistant-pipeline.spec.ts#shows an opaque citation error after access is revoked`, `CitationContentWebMvcTests` | covered |
| Hostile upload media types cannot make citation content execute inline | `SourceUploadServiceTests#derivesTheStoredMediaTypeFromTheAllowlistedExtension`, `CitationContentControllerTests` | covered |
| Empty evidence, provider retry, and user abort are browser-tested | `assistant-pipeline.spec.ts` | covered |
| GraphRAG is selected explicitly with no silent fallback | `AssistantConfigurationTests` | covered |
| MCP publishes only the read-only permission-aware search tool | `OrgMemoryMcpContextTests` | covered |
| MCP forwards caller bearer identity to canonical REST search | `KnowledgeSearchApiClientTests`, `KnowledgeSearchToolTests` | covered |
| Durable conversation/turn claim/tool trace | none | not implemented |
