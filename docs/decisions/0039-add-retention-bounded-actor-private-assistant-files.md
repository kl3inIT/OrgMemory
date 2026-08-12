# 0039 — Add retention-bounded actor-private Assistant files

Status: accepted
Date: 2026-08-12
Supersedes: [0038](0038-use-governed-source-bindings-for-assistant-files.md)

## Context

The governed Source binding delivered by 0038 remains correct for publishing a
file as organizational Knowledge, but it is the wrong paperclip default for a
user asking about a private working file. Space/classification selection changes
the audience and lifecycle, while a chat attachment should remain private to its
uploader and expire without becoming Knowledge. Pinned Onyx separates reusable
user files from project/Knowledge publication and verifies ownership at use
time. It does not provide an antivirus, ClamAV, malware, or DLP upload gate.

## Decision

Keep the governed flow as explicit `Publish to Knowledge` and add a separate
reusable actor-private `AssistantFile` lane. A file has immutable object metadata,
a fixed non-renewing 30-day TTL, a private chunk projection, a pinned requested
and resolved processing profile, and an exact processing generation. Worker is
the only parser caller and reuses the same `structured-block-v1` engine, but the
private lane creates no Source, revision, Asset, ACL snapshot, or publication.

A turn selects at most three private files or three governed bindings, never
both. Private retrieval requires every exact actor/organization/file/generation
selection to remain READY and unexpired, uses only the private projection, and
requires one active matching embedding profile. Citation identity distinguishes
private evidence and repeats owner, TTL, lifecycle, and generation checks during
hydration and content access.

Delete and expiry first mark the file unusable, then remove extracted chunks,
then retry object deletion idempotently. The file tombstone and citation identity
remain; old answers show a non-clickable unavailable marker without retaining
the extracted content. Malware/DLP is an explicit project-owner-waived non-goal
for this increment after the pinned Onyx path was verified. Size, format,
signature, parser-resource, object-integrity, and safe-serving controls remain
mandatory and must not be described as malware inspection.

## Independent challenge

One Fable 5 response returned `REVISE` and selected reusable,
retention-bounded private files over turn-only attachments. The accepted
revision added immutable citation identity, no mixed evidence lanes, fixed TTL,
deny-before-cleanup ordering, bounded Recent Files, private retrieval/cache
isolation, worker-only parsing, fresh download authorization, and closed failure
contracts. No follow-up debate round or separate judge was run.

## Rejected alternatives

- Keep governed publication as the paperclip default. It changes audience and
  lifecycle for a private-chat use case.
- Use turn-only ephemeral bytes. It prevents Recent Files reuse and creates a
  second attachment identity on retry/replay without reducing parser risk.
- Reuse Source/Knowledge tables with a private flag. This mixes retention,
  authorization, publication, and retrieval contracts across two products.
- Send raw bytes or provider file handles to the model. This bypasses canonical
  parsing, deterministic evidence, citations, retention, and egress control.
- Add a placeholder scan status without a real scanner. It falsely represents a
  security property and blocks READY state on an unimplemented dependency.

## Consequences

- Paperclip upload/recent files are private to the actor; governed publication
  remains a separate explicit action.
- Object storage is durable but retention-bounded. A fixed TTL and retryable
  cleanup replace conversation lifetime as the deletion trigger.
- Private and Knowledge retrieval, cache, citation, and lifecycle state remain
  separate while sharing the parser/chunker engine.
- Images/OCR, declared archives, email recursion, provider-native files,
  sharing, promotion, legal hold, malware scanning, and DLP remain closed.
