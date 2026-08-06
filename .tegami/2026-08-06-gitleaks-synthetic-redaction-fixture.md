---
packages:
  orgmemory: patch
subject: Keep synthetic redaction fixtures in secret scanning
---

## Fixes

Secret scanning now narrowly recognizes the API-key-shaped value in the
assistant redaction regression as synthetic test data while continuing to scan
all other files and generic API-key findings.
