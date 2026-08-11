# Architecture Challenge: Assistant private files versus governed Knowledge

You are an independent architecture reviewer. Attack this proposal; do not
validate it by default. Verify every claim in the repository. This is a
read-only review: do not edit files, create plans, commit, or mutate external
state.

Read `AGENTS.md`, `docs/conventions.md`, `docs/guidelines/agent-safety.md`,
`docs/specs/domains/assistant-and-mcp.md`,
`docs/tests/domains/assistant-and-mcp.md`, decision filenames under
`docs/decisions`, and the full proposal at
`docs/increments/active/2026-08-11-assistant-private-files/design.md`.

## What OrgMemory is

OrgMemory is a governed organizational memory layer for enterprise AI. Its
promise is that tenant, lifecycle, classification, and current authorization
filter evidence before ranking, graph traversal, context assembly, model egress,
citations, and replay. Uploaded text is untrusted evidence. Organizational
Knowledge has immutable Source/revision processing and Space-governed audience.

## Exact rule under review

The proposal states:

> Make the Assistant paperclip attach an actor-private file for chat use instead
> of publishing organizational Knowledge. Preserve the shipped governed upload
> as an explicit `Publish to Knowledge` action and preserve every existing
> governed evidence binding unchanged.

It further proposes an Assistant-owned, S3-backed, retention-bounded file
lifecycle with a worker-only processor that reuses the existing parser/chunker
engine, private chunks/embeddings, exact message bindings, use-time owner checks,
and no Source/Asset/Space/classification identity.

Current enforcement to inspect:

- `apps/web/src/features/assistant/components/assistant-page.tsx`
- `apps/web/src/features/sources/components/source-upload-dialog.tsx`
- `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantController.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantEvidenceUploadService.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantEvidenceService.java`
- `core/src/main/java/com/orgmemory/core/knowledge/search/KnowledgeEvidenceSelection.java`
- `apps/worker/src/main/java/com/orgmemory/worker/ingestion/DocumentProcessingEngine.java`
- `apps/worker/src/main/java/com/orgmemory/worker/ingestion/SourceIngestionProcessor.java`
- `core/src/main/java/com/orgmemory/core/knowledge/storage/ObjectStoragePort.java`
- `docs/decisions/0038-use-governed-source-bindings-for-assistant-files.md`

## Comparable-system evidence

Verify the pins and files rather than trusting this table.

| System | Observed behavior | Files |
|---|---|---|
| Onyx at `618b5031bf21463f44e3bed9eb9d5073b806fec0` | Paperclip offers upload/recent user files. Chat upload supplies no project. Durable `UserFile` and per-message `FileDescriptor` are separate. Use-time ownership/project checks and explicit deletion exist; no automatic UserFile TTL was found. | `tmp/onyx/web/src/refresh-components/popovers/FilePickerPopover.tsx`, `tmp/onyx/web/src/hooks/useChatController.ts`, `tmp/onyx/backend/onyx/db/projects.py`, `tmp/onyx/backend/onyx/db/models.py`, `tmp/onyx/backend/onyx/file_store/utils.py`, `tmp/onyx/backend/onyx/chat/process_message.py`, `tmp/onyx/backend/onyx/server/features/projects/api.py` |
| Northstar at `caef9a9a55e60e8bc99b47275b4840d6cd940372` | Immutable hash-addressed attachments, async indexing, at-most-three selection, and untrusted evidence fencing exist. Attachment rows have no tenant/owner; APIs load by UUID; bytes are currently PostgreSQL-backed. | `D:/northstar/core/src/main/java/com/northstar/core/attachment/Attachment.java`, `D:/northstar/core/src/main/java/com/northstar/core/attachment/AttachmentService.java`, `D:/northstar/apps/api/src/main/java/com/northstar/api/attachment/AttachmentController.java`, `D:/northstar/apps/api/src/main/java/com/northstar/api/assistant/AssistantController.java`, `D:/northstar/core/src/main/resources/db/migration/V16__attachment.sql` |
| OrgMemory at `f8e30f0fac52ddba3ebcf1fc7ee79bbe91d5077f` | Paperclip opens the full Source dialog. Upload requires writable Space and classification, creates Source/revision/blob/job, and binds exact governed evidence. | current enforcement paths above |

## Operational and product cost motivating the change

The shipped paperclip presents a normal attachment affordance but opens a modal
that says the file is durable governed Knowledge, asks for classification and a
Knowledge Space, and blocks actors without `can_create_asset`. A chat-local task
therefore changes organizational audience/lifecycle or is unavailable. The user
identified this mismatch from the released UI. The existing solution is secure
but solves publication rather than ordinary chat attachment.

## Strongest counterargument

The Source lane already provides immutable processing, active-engine GraphRAG
answerability, citations, current OpenFGA authorization, retention/deletion
integration, and one operational pipeline. The proposal duplicates persistence,
jobs, private indexing, cleanup, retry, monitoring, and evidence assembly. It may
create a shadow personal knowledge base whose content bypasses classification,
Space policy, graph governance, and established citation hydration. The product
could instead remove the paperclip, label the existing action `Publish to
Knowledge`, and decline private files entirely.

## Questions the verdict must answer

1. Is a reusable retention-bounded `AssistantFile` the correct product identity,
   or must the first lane be conversation/turn-scoped and non-reusable?
2. Does reusing `DocumentProcessingEngine` while owning separate jobs/chunks
   preserve the parser boundary, or does it create an unsafe second ingestion
   pipeline? Name the narrowest viable boundary.
3. Is private S3 object storage plus DB metadata sufficient? Specify tenant key
   construction, encryption/egress, malware/DLP gating, cleanup ordering, and
   failure behavior that must exist before READY.
4. Can private file retrieval safely join the existing Assistant answer/citation
   path without becoming organizational Knowledge? Identify exact authorization,
   cache partition, citation, retry, deletion, and transcript invariants.
5. Should `Publish to Knowledge` copy/re-register the same bytes as a new Source,
   or is promotion out of scope until a later challenge?
6. Which architecture should ship: the proposal, a narrower conversation-only
   file, Source-only with corrected UX, or another concrete alternative?

Return an explicit `ACCEPT`, `REVISE`, or `REJECT` verdict, a must-fix list, the
strongest surviving counterargument, repository evidence for every material
claim, a committed recommendation, and scope limits. If you agree too easily,
challenge your conclusion with at least three concrete contradictions before
committing.

