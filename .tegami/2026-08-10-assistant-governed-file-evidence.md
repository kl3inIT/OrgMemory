---
packages:
  orgmemory: minor
subject: Use governed files as Assistant evidence
---

## Features

- Upload up to three supported documents from the Assistant composer, publish
  them to a chosen Knowledge Space, and wait for governed ingestion before use.
- Keep the exact ordered file selection across a failed retry and cite the same
  permission-verified evidence used for the answer.

## Security

- Recheck conversation ownership, current Source revision, actor access, and
  active retrieval-engine readiness before each selected-file turn.
- Keep selected files as a hard retrieval ceiling through graph expansion and
  citation output; direct provider files and transient attachment bypasses
  remain unavailable.
