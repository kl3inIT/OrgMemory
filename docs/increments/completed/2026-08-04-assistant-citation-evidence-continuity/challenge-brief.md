# Architecture Challenge Brief: Assistant Citation Evidence Continuity

## Decision Required

Decide whether OrgMemory should persist minimal citation references with each
completed assistant message and add a separately authorized evidence-excerpt
read path, so citations remain actionable after transcript replay without
persisting raw retrieved evidence.

The reviewer must return `ACCEPT`, `REJECT`, or `ACCEPT WITH MUST-FIXES`, name
all blocking changes, and cite repository evidence for every material claim.

## Observed Cost

During the 2026-08-04 Assistant UI review, the current product showed a large
single-source `1/1` popup containing a title plus generic permission copy but no
evidence content. Opening content required another click in the Sources panel.
A Markdown source then appeared as literal Markdown syntax, and the current
single waiting label did not distinguish the reported slow retrieval interval
from provider time to first token.

These are direct product-review observations supported by the current paths
below. No latency duration or usage frequency has been measured yet; the
increment must add retrieval-versus-first-token timing rather than invent a
performance claim. Comparable-system evidence and exact pins are recorded in
[reference study](reference-study.md).

## Current Evidence

- The browser card in
  `apps/web/src/features/assistant/components/assistant-answer.tsx:61-106`
  contains only title plus hard-coded `OrgMemory document` and
  `Permission-verified evidence used for this answer.` metadata.
- A citation click currently selects the source panel. The actor must click a
  source row again before
  `apps/web/src/features/assistant/components/assistant-sources-panel.tsx:101-126`
  fetches the original source blob.
- `assistant-sources-panel.tsx:364-407` embeds PDF, images, and safe text;
  unsupported types become download-only. Markdown is delivered as
  `text/plain` and therefore falls into the raw `<pre>` path instead of being
  parsed.
- `KnowledgeContentType.java:14-33` allows direct upload of PDF, DOCX, PPTX,
  Markdown, and text. Only PDF, Markdown, and text are inline-previewable among
  those uploads. PNG, JPEG, GIF, and WebP are safe for inline delivery but are
  not direct-upload types. DOCX and PPTX are attachment-only.
- `CitationContentController.java:34-80` derives the safe browser media type
  and disposition from the closed filename policy, sends `no-store` and
  `nosniff`, and falls back to octet-stream attachment.
- `DefaultCitationContentService.java:41-143` rechecks current canonical
  authorization, current revision/blob availability, and integrity before
  streaming original bytes.
- `RetrievedKnowledgeEvidence.java:5-23` already carries the canonical chunk
  content, title, page range, and heading after secure retrieval.
- `AssistantConversationMessage.java:13-67` and
  `AssistantConversationMessageView.java:6-12` persist/replay only message
  text and feedback, not citation mappings. Live source parts therefore cannot
  be reconstructed after reload.
- `AssistantService.java:88-158` completes synchronous retrieval before it
  returns the token Flux. `AssistantController.java:297-317` can emit
  `start-step` and sources only after that call returns. The browser therefore
  has no server phase event during the longest pre-response interval.
- The browser currently derives one static `Searching permitted knowledge…`
  indicator from `submitted|streaming` plus absence of visible output
  (`assistant-page.tsx:479-490,771-777`). It cannot distinguish retrieval from
  provider time to first token.

## Proposed Boundary

1. Add a separate immutable assistant-message citation table containing only
   organization ID, actor user ID, assistant message ID, citation number, and
   canonical chunk ID. Repeat message ownership in a composite foreign key and
   cascade on conversation/message deletion. Do not persist excerpt text,
   source bytes, URL, heading, title, or page metadata.
2. Persist citation references only in the same successful transcript commit
   as the completed assistant answer. Failed, empty, or aborted streams leave
   no citation rows.
3. On history replay, batch-recheck current canonical authorization before
   returning citation metadata. Omit denied, missing, stale, or unavailable
   references without revealing title, excerpt, source identity, or denial
   reason. Their answer markers remain inert text.
4. Add `GET /api/citations/{chunkId}/excerpt`. It uses the same actor and
   canonical current-authorization boundary as full citation content, returns
   a bounded plain-text/JSON representation of title, heading, page range,
   excerpt, and truncation state, uses `no-store`, and collapses missing/denied
   state to the existing opaque not-found surface.
5. Keep `GET /api/citations/{chunkId}/content` as the explicit full-document
   action. Preserve the closed safe-representation policy: PDF, safe text, and
   safe image are inline; DOCX, PPTX, and unknown types are download-only.
6. Replace the metadata-only carousel with a compact accessible citation
   trigger. Clicking it selects the source and immediately exposes authorized
   evidence in the source surface. Full source preview/download is a secondary
   explicit action.
7. For a canonical `.md` filename, render the fetched safe text through a
   dedicated restricted Streamdown composition. Embedded HTML is escaped;
   output remains sanitized; remote images/resources and Mermaid/custom
   renderers are disabled; outbound links require the existing safety
   interaction. Offer a `Rendered | Raw` switch and keep copy/download actions.
   Do not infer Markdown from connector MIME metadata.
8. Make the turn response begin before blocking retrieval and emit typed,
   transient Assistant activity parts with a closed server-owned phase enum:
   retrieval active/completed, then generation active. The retrieval-complete
   event may contain only the count of evidence already authorized for the
   current actor. The first text delta ends the pre-token activity. These
   events are not persisted and are not model reasoning or tool calls.
9. Keep `stop` available in both submitted and streaming states. Cancellation,
   retrieval failure, provider failure, no-evidence completion, and delayed
   first token must each terminate or replace the active status without leaving
   a stale spinner. A client-only long-wait message may acknowledge elapsed
   time but must not invent percentage progress or a completion estimate.
10. Reserve AI SDK `tool-*` parts and the AI Elements `Tool` component for real
    model tool invocations. Future tool calls may join the same activity
    timeline, but retrieval remains a server-owned operation event until it is
    actually modeled and authorized as a tool.

## Invariants To Attack

- Revocation between answer generation and click/replay must reveal no current
  title, excerpt, source identity, content, or denial reason.
- Cross-actor and cross-tenant transcript or citation access must remain the
  same opaque not-found surface.
- Citation rows must never outlive their owning message or silently attach to
  another tenant's message.
- History authorization must be bounded and batch-oriented; it must not create
  one OpenFGA request per citation.
- Excerpts are untrusted plain text. They must never become executable HTML or
  be interpolated into same-origin active content.
- Stored Markdown is also untrusted. Parsing must not load remote images,
  execute embedded HTML, activate Mermaid/custom renderers, or bypass outbound
  link confirmation.
- A historical answer remains a transcript snapshot, while every evidence
  read reflects current authorization and current canonical availability.
- Format support must not be widened by trusting stored or connector-supplied
  MIME metadata.
- Pre-token activity must describe an observed server phase, reveal no denied
  source count/name, remain transient, honor cancellation, and never expose
  chain-of-thought.

## Strongest Counterargument

Citation persistence adds a migration, a new message-owned aggregate, replay
authorization work, and retention semantics for a usability problem. A smaller
increment could remove the empty popover and improve the live source panel
without changing storage. Users would lose citation actions after reload, but
the answer text would remain available and no new durable evidence linkage
would exist.

The reviewer must specifically challenge whether citation ownership belongs in
Assistant or secure retrieval, whether the history response should contain any
reference for a now-denied chunk, and whether one shared authorization service
can safely serve both excerpt and original-content reads without weakening
auditing or opaque failure behavior. Also challenge whether beginning the SSE
response before blocking retrieval preserves actor/security context,
transaction boundaries, cancellation, observation timing, and bounded
execution under concurrent slow searches.
