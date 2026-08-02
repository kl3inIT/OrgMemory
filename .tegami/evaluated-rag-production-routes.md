---
packages:
  orgmemory: patch
subject: Activate evaluated production RAG routes
---

## Improvements

Production now defaults Answer to `gpt-5.6-sol`, Keyword Planning to
`gpt-5.6-luna` with reasoning disabled, and Graph Extraction to
`gpt-5.4-mini`, preserving the independently evaluated quality and latency
split across deployments and shared ZM development.
