# Assistant Private File Reference Study

Pins verified on 2026-08-11:

- Onyx: `618b5031bf21463f44e3bed9eb9d5073b806fec0`
- Northstar: `caef9a9a55e60e8bc99b47275b4840d6cd940372`
- OrgMemory proposal base: `f8e30f0fac52ddba3ebcf1fc7ee79bbe91d5077f`

| System | Behavior | Mechanism and source evidence | Judgment for OrgMemory |
|---|---|---|---|
| Onyx | The chat paperclip uploads or selects a recent user-private file, not organizational Knowledge. Chat upload passes no project. | `tmp/onyx/web/src/sections/input/AppInputBar.tsx`, `tmp/onyx/web/src/refresh-components/popovers/FilePickerPopover.tsx`, `tmp/onyx/web/src/hooks/useChatController.ts`, `tmp/onyx/web/src/lib/projects/svc.ts` | Adopt the paperclip semantics and recent-file reuse. |
| Onyx | A durable `UserFile` owns storage and async processing; a `FileDescriptor` is stored on the chat message. | `tmp/onyx/backend/onyx/db/projects.py`, `tmp/onyx/backend/onyx/db/models.py`, `tmp/onyx/backend/onyx/file_store/models.py`, `tmp/onyx/backend/onyx/chat/process_message.py` | Adopt separate file identity and immutable message binding. |
| Onyx | Use-time verification checks file ownership or project association. Explicit deletion marks `DELETING`; no automatic UserFile TTL was found at this pin. | `tmp/onyx/backend/onyx/file_store/utils.py`, `tmp/onyx/backend/onyx/server/features/projects/api.py` | Adopt repeated ownership checks and explicit deletion, but add mandatory policy expiry and organization isolation. |
| Northstar | Attachments are immutable, hash-addressed, limited, asynchronously indexed, and bounded to at most three IDs per turn. | `D:/northstar/core/src/main/java/com/northstar/core/attachment/AttachmentService.java`, `D:/northstar/apps/api/src/main/java/com/northstar/api/attachment/AttachmentController.java`, `D:/northstar/apps/api/src/main/java/com/northstar/api/assistant/AssistantController.java` | Adopt immutable content identity, visible readiness, small selection, and untrusted-evidence fencing. |
| Northstar | Attachment rows contain no tenant/owner and APIs load by UUID. Bytes currently live in PostgreSQL; S3 is only reserved. | `D:/northstar/core/src/main/java/com/northstar/core/attachment/Attachment.java`, `D:/northstar/core/src/main/resources/db/migration/V16__attachment.sql`, `D:/northstar/apps/api/src/main/java/com/northstar/api/attachment/AttachmentController.java` | Do not inherit UUID-sufficient access, global digest identity, or database blob storage. |
| OrgMemory today | The paperclip lazily opens `SourceUploadDialog`; upload requires Space and classification and creates a durable governed Source binding. | `apps/web/src/features/assistant/components/assistant-page.tsx`, `apps/web/src/features/sources/components/source-upload-dialog.tsx`, `core/src/main/java/com/orgmemory/core/assistant/AssistantEvidenceUploadService.java`, `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantController.java` | Preserve this as explicit publication, not the default attachment action. |

Onyx is the primary product reference. OrgMemory deliberately diverges by
requiring organization and actor ownership, server-controlled expiry,
fail-closed exact retrieval, pinned processing identity, and deletion of both
object bytes and private projections.

