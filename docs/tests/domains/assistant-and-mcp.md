# Assistant And MCP Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Assistant sends only permission-verified evidence to the model | `AssistantServiceTests#streamsOnlyPermissionVerifiedEvidenceToTheModel` | covered |
| Empty authorized retrieval does not call the model | `AssistantServiceTests#doesNotCallTheModelWhenNoAccessibleEvidenceExists` | covered |
| Provider failure is surfaced as unavailable | `AssistantServiceTests#asynchronousProviderFailureIsReportedAsUnavailable` | covered |
| Durable conversation/turn claim/tool trace | none | not implemented |
| MCP and in-app tools share authorized use cases | none | MCP is a scaffold |
