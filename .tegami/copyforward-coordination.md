---
packages:
  orgmemory: patch
subject: Safer, leaner knowledge publication on OpenSearch
---

# Safer, leaner knowledge publication on OpenSearch

## Improvements

Publishing a new knowledge generation on OpenSearch now runs through one
coordinated copy-forward protocol with durable ownership: a publisher can no
longer take over a copy that another process is still performing, failed
copies leave an explicit durable failure state and clean up their partial
output, and the previous generation streams across in bounded pages instead
of being loaded into memory whole. Stored documents are preserved
byte-for-byte.
