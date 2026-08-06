---
packages:
  orgmemory: patch
subject: Keep Assistant tool calls compatible with OpenAI
---

## Fixes

Fresh production deployments now set Answer reasoning to `none` so the
Assistant's governed Skill tools work with `gpt-5.6-sol` on OpenAI Chat
Completions without requiring an organization route workaround.
