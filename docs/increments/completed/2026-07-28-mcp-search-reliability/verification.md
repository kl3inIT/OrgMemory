# MCP Search Reliability Verification

## Repository Evidence

- PR [#98](https://github.com/kl3inIT/OrgMemory/pull/98) separated the
  five-second connection timeout from the bounded 75-second MCP/API response
  budget and merged as `7d9c3bf73eed1466309eb2f88821a3fda6c51e6f`.
- PR [#99](https://github.com/kl3inIT/OrgMemory/pull/99) made citation metadata
  that is not applicable optional in the MCP result schema and merged as
  `b0d5639e312ea7c04e054fa5d9278760157cad7d`.
- Focused MCP tests and the terminating repository test suite passed for the
  timeout repair. PR #99 additionally exercised the published schema with the
  MCP JSON Schema validator.

## Production Evidence

- CI run `30346732393`, immutable image build `30346830457`, and production
  deployment `30347092952` completed successfully for the final repair.
- Production ran the MCP image tagged with the exact merge SHA
  `b0d5639e312ea7c04e054fa5d9278760157cad7d`, and the container was healthy.
- A retry from the connected Claude client invoked `search_knowledge`,
  completed permission-aware retrieval over 21 evidence items, and returned
  an evidence-backed answer.
- Logs for the verified request contained no `Broken pipe`,
  `Tool output validation failed`, or `HttpMessageNotWritableException`.

This closes the live production gate without changing OAuth, OpenFGA, ACL, or
canonical retrieval semantics.
