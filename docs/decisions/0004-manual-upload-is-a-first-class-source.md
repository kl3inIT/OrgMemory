# 0004 — Manual Upload Is A First-Class Source

## Status

Accepted on 2026-07-20.

## Context

A platform that only imports connector data cannot prove its own end-to-end trust
boundary. Manual content must not bypass ingestion or permission controls.

## Decision

`ORGMEMORY_UPLOAD` follows upload session, authorization, quarantine blob,
signature/size/malware/DLP checks, hashing, sandbox parsing/OCR, immutable source
revision, ACL generation, normalization, indexing, review, and publication.
OrgMemory is the native source of truth for upload ACLs.

## Consequences

New uploads are private draft/quarantined evidence by default. Uploader, content
owner, reviewer, and publisher remain distinct. Operations admins do not gain
implicit content access.
