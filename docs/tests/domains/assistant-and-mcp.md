# Assistant And MCP Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Assistant sends only permission-verified evidence to the model | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel` | covered |
| Empty authorized retrieval does not call the model | `AssistantServiceTests#doesNotCallTheModelWhenNoAccessibleEvidenceExists` | covered |
| Provider failure is surfaced as unavailable | `AssistantServiceTests#asynchronousProviderFailureIsReportedAsUnavailable` | covered |
| GraphRAG is selected explicitly with no silent fallback | `AssistantConfigurationTests` | covered |
| MCP publishes only the read-only permission-aware search tool | `OrgMemoryMcpContextTests` | covered |
| MCP forwards caller bearer identity to canonical REST search | `KnowledgeSearchApiClientTests`, `KnowledgeSearchToolTests` | covered |
| Durable conversation/turn claim/tool trace | none | not implemented |
