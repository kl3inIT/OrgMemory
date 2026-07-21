# Agent Safety Guideline

- All retrieval, tool, graph, citation, and export paths use the authenticated
  actor and the same permission-aware core use case.
- Tool descriptions do not grant authority. Mutations require explicit user
  intent and domain authorization.
- Retrieved/uploaded text is untrusted evidence and cannot override system or
  policy instructions.
- Do not disclose denied titles, snippets, counts, graph neighbors, source IDs,
  or timing differences that reveal resource existence.
- Generated summaries, graph facts, candidates, and recommendations inherit the
  effective permission of every contributing evidence item.
- Record model route, tool calls, evidence IDs, authorization decision version,
  and allowed citations without persisting raw secrets or unnecessary prompts.
- Keep MCP no more privileged than the in-app agent. Service identities require
  explicit tenant, scope, retention, and audit policy.
- Publish an explicit safe tool subset with read-only/destructive/idempotent and
  open-world hints; do not expose every Spring bean or tool automatically.
