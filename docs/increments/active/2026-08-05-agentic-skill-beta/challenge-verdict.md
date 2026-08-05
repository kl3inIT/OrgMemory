# Architecture Challenge Verdict

## Review record

Fable 5 completed the first adversarial round and defended the native Assistant
approach. Its second round could not run because the local Claude session was no
longer authenticated. The project owner then explicitly directed implementation
of the Agentic beta on 2026-08-05. This records the unavailable-reviewer fallback
required by repository guidance rather than presenting the debate as complete.

The strongest retained counterargument is the separate-runtime proposal in the
brief: it offers eventual script parity and a cleaner extraction seam.

## Verdict

Proceed with the native bounded loop.

Repository evidence is decisive for this increment:

- `SKILL` already has one governed identity, package validator, immutable release,
  live authorization path, and S3-compatible delivery boundary. A parallel
  registry would split truth without adding safe execution.
- The server has no sandbox. Adding shell, filesystem, or package-code execution
  would turn untrusted uploaded content into server authority.
- Spring AI 2.0 already supplies the recursive model/tool mechanism needed for
  a closed read-only loop.
- MCP/CLI already distribute an exact verified package to external agents, where
  execution belongs to the client's sandbox and policy.

## Rejected alternative

Do not port Spring AI Alibaba's ReactAgent/filesystem Skill registry or
`spring-ai-agent-utils` shell/filesystem tools into the server. Their useful
progressive-disclosure ideas are adapted to OrgMemory's actor-scoped storage and
authorization contracts; their execution assumptions are not.

## Revisit trigger

Reconsider a separate runtime only when OrgMemory has a concrete autonomous job
use case plus an isolated filesystem, process/network policy, resource quotas,
approval interrupts, resumable state, and audit/retention design.

