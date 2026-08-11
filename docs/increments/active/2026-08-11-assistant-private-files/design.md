# Assistant Private Files

## Status

Proposed. Implementation is blocked on the independent architecture challenge.

## Intent

Make the Assistant paperclip attach an actor-private file for chat use instead
of publishing organizational Knowledge. Preserve the shipped governed upload as
an explicit `Publish to Knowledge` action and preserve every existing governed
evidence binding unchanged.

The first private-file delivery follows Onyx's separation between a reusable
user file and an immutable message reference, strengthened for OrgMemory with
tenant ownership, mandatory server-owned retention, fail-closed use-time
authorization, immutable processing identity, and storage/index deletion.

## Product contract

The default paperclip offers `Upload files` and `Recent files`. Uploading does
not ask for a Knowledge Space or classification, does not create a Source or
Knowledge Asset, and does not make the file visible in organizational search.
The file remains private to the authenticated actor and reusable across that
actor's conversations until explicit deletion or policy expiry.

`Publish to Knowledge` remains a separate explicit action using the current
Source upload flow. Publishing creates a new Source identity and provenance; it
never changes an `AssistantFile` into a Source in place. Selecting existing
Knowledge is a later increment.

## Ownership and boundaries

### Assistant owns the private lifecycle

Assistant owns:

- immutable `AssistantFile` identity and actor/organization ownership;
- server-derived filename, detected media type, size, content digest, object
  key, processing state, pinned processing profiles, expiry, and deletion state;
- immutable ordered message bindings;
- upload/list/status/download/delete use cases and opaque cross-owner failure;
- exact private-file selection for one turn; and
- retention cleanup coordination.

Possession of an Assistant file UUID grants no authority. Upload, list, status,
download, delete, turn claim, retry, and replay all resolve the authenticated
actor and recheck organization, owner, lifecycle, and expiry.

### Shared mechanics stay below product lifecycle

The existing channel-neutral parser, canonical document IR, chunker registry,
`structured-block-v1` policy, tokenizer, and immutable requested/resolved
processing-profile contracts remain reusable. The worker remains the only
production parser caller. A private-file processor and the Source processor may
call the same `DocumentProcessingEngine`; they do not share lifecycle records,
authorization rules, publication state, or retrieval projections.

Object storage remains infrastructure. Assistant uploads raw bytes under a
private Assistant object-key namespace through a narrow storage port. The
database stores metadata, processing state, bindings, and object references,
not file bytes. No public or durable presigned URL is exposed.

### Knowledge remains governed publication

Source Ledger continues to own Space, classification, Source/revision,
Knowledge Asset publication, canonical ACL, and organization-wide retrieval.
The default paperclip no longer calls `SourceUploadService`. The current
governed evidence path remains readable and retryable for old conversations and
is presented only as explicit publication for new uploads.

## Persistence model

Each accepted upload creates a new immutable file identity even when its digest
matches another actor's file. Cross-actor deduplication must not expose identity,
timing, filename, or lifecycle. Physical blob deduplication, if ever added,
stays opaque below the authorization boundary.

`assistant_file` contains the file ID, organization ID, owner actor ID,
sanitized filename, detected content type, size, SHA-256, object key, processing
state, requested and resolved processing-profile snapshots and hashes,
`expires_at`, timestamps, and a deletion marker. `expires_at` is calculated
from server-owned organization policy and cannot be supplied by the browser.

`assistant_message_file` contains message ID, file ID, ordinal, and a bounded
filename/media-type snapshot. A restrictive foreign key preserves the binding
while the file is live. Expiry/deletion removes bytes and private retrieval
projections but keeps the message snapshot so transcript shape remains stable
and content becomes uniformly unavailable.

## Processing and retrieval

Upload admission is channel-specific. Parser capability does not automatically
admit a format. The API sniffs type, enforces Assistant limits, registers the
blob and immutable requested processing profile, then enqueues work; it never
parses, chunks, embeds, or indexes on the request thread.

The worker claims one Assistant file, loads the object, invokes the shared
document-processing engine, pins the resolved profile, and writes private
chunks/embeddings keyed by organization, owner, file ID, and processing
generation. The first delivery does not publish an Asset or graph projection.

Turn submission accepts at most three total selected file/evidence references.
Assistant-file turns are exact-selection-only: only chunks for the claimed
private file IDs may be ranked or reach the model. Every selected file must
yield usable evidence. A selected file that is processing, failed, expired,
deleted, foreign, or empty fails closed before model generation. Uploaded text
is untrusted evidence and existing prompt-injection fencing applies.

Existing `KnowledgeEvidenceSelection` remains the ceiling for governed Source
bindings. Private-file evidence receives a distinct internal selection and
retrieval port; the two identities are not coerced into one ID namespace.

## Lifecycle

An Assistant file is reusable by its owner across conversations only while it
is live. The server returns recent live files ordered by last use. Removing a
composer chip removes only the pending selection. Explicit delete and expiry
transition the file to deletion, prevent new claims immediately, and enqueue
idempotent removal of object bytes and private projections.

Existing message bindings survive as metadata-only references. A retry after
deletion or expiry fails closed rather than silently searching organizational
Knowledge. The first delivery does not implement legal hold, sharing, project
association, promotion, images/OCR, archives, email recursion, or provider file
handles.

## Strongest counterargument

The shipped Source binding already provides immutable processing, citations,
GraphRAG answerability, authorization, deletion semantics, and one ingestion
pipeline. A private lane duplicates persistence, background processing,
retention, deletion, retrieval, and operational monitoring while weakening the
central governed-Knowledge thesis. The safer product could keep durable Source
publication and merely replace the paperclip with an explicitly labeled
`Publish to Knowledge` action.

That alternative is materially simpler. It is rejected provisionally because
the ordinary paperclip use case is actor-private conversational evidence; making
publication a prerequisite changes audience and lifecycle, blocks actors
without Space publication authority, and creates organizational records as an
unexpected side effect. The architecture challenge must decide whether that
product mismatch justifies the second lifecycle and which controls are
mandatory before implementation.

## Compatibility and sequencing

- Never migrate or mutate existing governed evidence bindings.
- Keep current Source/revision selection and citations functional.
- Add private-file tables and APIs before switching the composer.
- Switch the paperclip only after owner-negative, expiry, deletion, exact-
  selection, retry, and parser-profile tests pass.
- Keep `Publish to Knowledge` explicit and visually distinct.
- A later increment may add `Add from Knowledge` and provenance-preserving
  promotion after a separate authorization/publication review.

