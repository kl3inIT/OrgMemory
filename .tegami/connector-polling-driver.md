---
packages:
  orgmemory: patch
subject: Make live connector polling consistent and rotation-aware
---

# Make live connector polling consistent and rotation-aware

Slack, Google Drive, and GitHub now run through one connector polling driver
for connection isolation, content cadence, failure activity, and
credential-derived client lifecycle. Clients retain safe token and rate-limit
state across polls, rebuild after credential or client-identity changes, and
retire when a connection disappears. A crawl in which at least half of the
eligible source units cannot be read is reported as `mostly_failed` instead of
advancing a broadly degraded checkpoint, while all existing crawl and
component cursor bytes remain unchanged.
