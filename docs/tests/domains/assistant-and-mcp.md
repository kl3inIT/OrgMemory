# Assistant And MCP Coverage

| Behavior | Evidence | Status |
| --- | --- | --- |
| API context boots without provider key | `OrgMemoryApiContextLoadTests` | covered |
| Authenticated identity without OrgMemory role cannot chat | `CapabilityAssetServiceIntegrationTests#chatRejectsAuthenticatedIdentityWithoutAnOrgMemoryRole` | covered |
| Demo fallback cannot mutate approved knowledge | none | gap |
| Durable conversation/turn claim/tool trace | none | not implemented |
| Knowledge evidence filtered before model/citation | none | not implemented |
| MCP and in-app tools share authorized use cases | none | MCP is a scaffold |
