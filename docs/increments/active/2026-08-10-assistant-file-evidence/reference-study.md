# Assistant File Evidence Reference Study

## Pins

The study uses local, clean, immutable reference checkouts rather than product
documentation or recollection.

| Reference | Checkout | Commit |
| --- | --- | --- |
| Northstar | `D:\northstar` | `caef9a9a55e60e8bc99b47275b4840d6cd940372` |
| Onyx | `D:\OrgMemory\tmp\onyx` | `618b5031bf21463f44e3bed9eb9d5073b806fec0` |

## Northstar

### Mechanism

- `AttachmentService` stores immutable bytes, deduplicates by SHA-256, limits an
  upload to 25 MB, strips path segments from filenames, and publishes a stored
  event. `Attachment` has no organization, user, project, or conversation owner.
- `AttachmentController` admits a bounded type set after byte inspection, exposes
  an index-status poll, allows at most three IDs in that poll, serves only
  magic-byte-verified raster images inline, and forces other content through a
  sandboxed download response.
- `SearchIndexingWorker` keeps Tika, embedding, and vision work off request
  threads. `SearchService.indexAttachment` keys idempotency on immutable file
  SHA-256 plus an index version, records processing status, and atomically replaces
  indexed chunks.
- `AssistantController.ChatRequest` carries at most three attachment IDs. A turn
  refuses document context until every requested document is ready, bounds the
  selected context, emits source parts, and inserts excerpts under an
  `UNTRUSTED_ATTACHMENT_EVIDENCE_JSON` marker. The system-level trust policy is
  repeated on every turn because conversation memory can replay old attachment
  evidence.

Evidence:

- `core/src/main/java/com/northstar/core/attachment/AttachmentService.java`
  (`store`, `safeFilename`)
- `apps/api/src/main/java/com/northstar/api/attachment/AttachmentController.java`
  (`upload`, `indexStatus`, `serve`)
- `apps/worker/src/main/java/com/northstar/worker/search/SearchIndexingWorker.java`
- `core/src/main/java/com/northstar/core/search/SearchService.java`
  (`indexAttachment`, `indexAttachmentLocked`)
- `apps/api/src/main/java/com/northstar/api/assistant/AssistantController.java`
  (`chat`, `streamTurn`, `userMessageWithEvidence`, `ChatRequest`)

### What OrgMemory should learn

1. Preparation is asynchronous and visible; Tika and embedding do not belong on
   the interactive request thread.
2. Immutable bytes plus content hash and processing-version identity make retries
   cheap and deterministic.
3. A small attachment-count limit, explicit terminal states, bounded context, and
   prompt-injection fencing are useful UX and safety contracts.
4. Serving is a separate security boundary from parsing: authenticated access,
   `nosniff`, sandboxing, safe disposition, and immutable private caching all
   matter.

### What OrgMemory must not copy

An attachment ID is effectively sufficient authority inside Northstar's local
model. There is no tenant or caller ownership on the entity and no governed
publication/classification ceiling. OrgMemory cannot make a stored file usable by
Assistant merely because a client submits its UUID.

## Onyx

### Mechanism

Onyx separates two identities:

- a chat file descriptor points at content-addressed file-store bytes and is
  carried by chat history;
- a durable `UserFile` is owned by a user, has a processing/indexing lifecycle,
  and may be associated with a `UserProject` or persona.

`upload_user_files` checks project ownership before creating user files and
queues per-file background processing. Statuses include `PROCESSING`, `INDEXING`,
`COMPLETED`, `SKIPPED`, `FAILED`, `CANCELED`, and `DELETING`. Deletion refuses to
proceed silently while project or assistant associations remain.

At turn time, `verify_user_files` checks user ownership or project membership of
every submitted descriptor. `resolve_context_user_files` makes project-versus-
persona precedence explicit. `extract_context_files` injects text only when the
whole set fits a bounded fraction of the model context; otherwise it uses vector
search or metadata plus a file-reader tool. Tabular data is metadata-only. Chat
files and durable user files remain separate allowlists for tools. File download
returns 404 across ownership boundaries.

Evidence:

- `backend/onyx/file_store/models.py` (`ChatFileType`, `FileDescriptor`)
- `backend/onyx/db/models.py` (`UserFile`)
- `backend/onyx/db/enums.py` (`UserFileStatus`)
- `backend/onyx/server/features/projects/api.py` (`upload_user_files`,
  `delete_user_file`, `get_user_file`)
- `backend/onyx/file_store/utils.py` (`verify_user_files`)
- `backend/onyx/chat/process_message.py` (`resolve_context_user_files`,
  `extract_context_files`, and their verified turn-setup call sites)
- `backend/onyx/tools/tool_implementations/file_reader/file_reader_tool.py`

### What OrgMemory should learn

1. Transient chat files and durable reusable files are different product
   identities, not two flags on the same row.
2. File availability to a model/tool is assembled from an explicit allowlist and
   verified again at use time.
3. Processing state, project/persona association, deletion state, context-budget
   decisions, and file-type-specific treatment are explicit contracts.
4. Large or structured files should not be flattened blindly into the prompt;
   retrieval and file tools are distinct fallback mechanisms.

### What OrgMemory must not copy

Onyx's primary durable boundary is user/project ownership. OrgMemory must also
intersect organization, Knowledge Space audience, classification, source ACL,
publication/readiness, and current evidence integrity. A `UserFile`-style private
library would introduce a second authorization universe unless it is designed as
a governed Knowledge Space.

## Consequence for OrgMemory

Use Northstar's asynchronous, bounded, hash-idempotent preparation discipline and
Onyx's separation of identities and use-time verification. Do not copy either
authorization model. The first OrgMemory composer upload should create governed
Knowledge evidence through the existing Source Ledger. A separate transient-turn
lane remains a future architecture decision, not an implementation shortcut.
