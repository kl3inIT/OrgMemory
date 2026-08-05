---
packages:
  orgmemory: patch
subject: Make Assistant no-answer guidance permission-safe
---

## Improvements

The Assistant now responds in the user's language when accessible documents do
not answer a question, offers one concise next step, labels nearby information
instead of presenting it as the requested answer, and cites every source it
uses without citing unrelated documents. Assistant answers also show a short
reminder that they rely only on documents the user can access.
