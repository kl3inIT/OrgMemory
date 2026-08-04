# Assistant Citation Evidence Continuity Reference Study

## Pins

- OrgMemory baseline: `bc59ddb4daf6faaa0a6296fdb2959e2d08838545`.
- Onyx checkout: `618b5031bf21463f44e3bed9eb9d5073b806fec0`.
- AI Elements: current official `/vercel/ai-elements` documentation resolved
  through Context7 on 2026-08-04.

## Comparable-System Evidence

| System | File or official source | Observed behavior | OrgMemory decision |
| --- | --- | --- | --- |
| Onyx | `SourceTag.tsx`, `SourceTagDetailsCard.tsx`, `DocumentsSidebar.tsx`, `useTimelineHeader.ts` at the pinned checkout below | compact source chips, dedicated ordered source surface, activity labels derived from real packet types | keep the interaction separation and truthfulness rule; do not port packet/document state |
| AI Elements | official inline-citation, Chain of Thought, Tool, and Reasoning component references | composable presentation; the application supplies citation evidence and real tool/reasoning states | reuse local primitives only; OrgMemory owns evidence, activity, tool, and authorization contracts |
| AI SDK | official current chat-status and streaming-data documentation | `submitted` waits for response, `streaming` consumes chunks, transient typed data parts stay out of history, stop aborts both waiting states | use transient server activity parts before first token; reserve tool parts for real tool calls |
| Streamdown | official current security documentation for 2.5 behavior | raw HTML can be omitted; sanitize/hardening and URL/resource policies are application-configured | create a restricted citation Markdown renderer, not the richer answer renderer |

## Onyx

Onyx keeps inline citations compact and treats richer source exploration as a
separate surface:

- `web/src/refresh-components/buttons/source-tag/SourceTag.tsx:430-451`
  gives the citation chip one direct source action;
- `web/src/refresh-components/buttons/source-tag/SourceTag.tsx:536-552`
  composes the optional hover card around that same chip;
- `web/src/refresh-components/buttons/source-tag/SourceTagDetailsCard.tsx:87-179`
  limits the card to navigation, title, metadata, and a short description;
- `web/src/sections/document-sidebar/DocumentsSidebar.tsx:102-182` orders
  cited documents separately from other retrieved documents;
- `web/src/sections/document-sidebar/ChatDocumentDisplay.tsx:81-105` makes a
  source row actionable and includes a short matched summary;
- `web/src/lib/search/utils.ts:29-42` opens linked evidence directly and uses
  an in-product presenter only for file-backed documents.

The useful product pattern is compact inline identity followed by a dedicated
source surface. OrgMemory should not copy Onyx's document or packet model: its
current-authorization recheck, canonical ledger, actor-owned transcript, and
opaque failure behavior are stricter boundaries.

Onyx's activity timeline derives the active label from real packet types:
search becomes `Reading` or `Searching`, file retrieval becomes `Reading file`,
and actual tools receive their own execution labels
(`web/src/app/app/message/messageComponents/timeline/hooks/useTimelineHeader.ts:20-123`).
When no packet exists it falls back to `Thinking...`. OrgMemory should adopt
the truthfulness rule, not the packet architecture: server-owned retrieval
phases are activity events, while only real model tool invocations are tool
parts.

### Citation continuity

Onyx persists citation-number-to-search-document mappings as JSON on the chat
message (`backend/onyx/db/models.py:3122-3137`). Session loading reconstructs
search and citation packets after reload and sorts citations by their order in
the answer (`backend/onyx/server/query_and_chat/session_loading.py:478-525,819-870`).

This proves the product value of reload continuity, but not OrgMemory's storage
shape. Onyx can replay stored documents directly; OrgMemory must persist only
the minimal chunk mapping and hydrate display metadata through current
authorization without making transcript delivery depend on OpenFGA.

### File presenter

Onyx resolves a full-screen preview variant from name and MIME, fetches content
only after the presenter opens, revokes blob URLs, and keeps copy/download
actions in a common footer
(`web/src/sections/modals/PreviewModal/PreviewModal.tsx:26-160,196-245`). Its
variants currently include PDF, images, Markdown, text/code, CSV, XLSX, and
DOCX (`web/src/sections/modals/PreviewModal/variants/index.ts:13-33`).

Useful mechanics:

- PDF uses a full-height iframe plus download
  (`variants/pdfVariant.tsx:5-26`);
- Markdown is fetched as text and rendered through a minimal Markdown
  composition with copy/download (`variants/markdownVariant.tsx:8-41` and
  `variants/CodePreview.tsx:14-35`);
- XLSX is parsed on the server into a bounded presentation payload instead of
  giving the browser the binary workbook (`PreviewModal.tsx:88-129`,
  `variants/xlsxVariant.tsx:21-68`);
- failed or unsupported previews retain a download path
  (`PreviewModal.tsx:138-149,219-245` and
  `variants/unsupportedVariant.tsx:5-28`).

Boundaries OrgMemory must not copy:

- Onyx chooses variants from display name and stored MIME; OrgMemory requires
  one closed server-derived presentation enum after current authorization.
- Onyx's image variant accepts any `image/*`; OrgMemory keeps only
  PNG/JPEG/GIF/WebP.
- Onyx's Markdown renderer permits ordinary HTTP/HTTPS URLs and does not
  replace Markdown images, so a remote image can still create a browser
  request. OrgMemory must render all document images/media as inert
  placeholders.
- DOCX is rendered client-side with `docx-preview` and sanitized after library
  rendering (`variants/docxVariant.tsx:35-103`). That is a materially larger
  untrusted-document surface and remains out of this increment.
- Onyx's CSV parser splits on commas rather than implementing quoted CSV
  semantics (`variants/csvVariant.tsx:22-26`); it is not a parser to port.
- Onyx serves authorized immutable chat files with private one-year caching
  (`backend/onyx/server/query_and_chat/chat_backend.py:897-947`). OrgMemory
  citations must remain `no-store` because every open rechecks current access,
  current revision, and canonical availability.

### Live activity and tools

Onyx returns its streaming response before the generator runs
(`backend/onyx/server/query_and_chat/chat_backend.py:685-711`). Its search tool
emits `SearchToolStart` as a real model tool invocation before expansion and
retrieval (`backend/onyx/tools/tool_implementations/search/search_tool.py:561-636`).
The timeline maps real packet types to `Searching`, `Reading`, `Executing code`,
or a named custom tool, and falls back to `Thinking...` only without packets.

The active header uses text shimmer and duration; its surrounding timeline
adds an agent avatar (`AgentTimeline.tsx:38-55` and
`headers/StreamingHeader.tsx:21-79`). OrgMemory adopts dynamic text and terminal
state, but rejects the avatar/leading icon and large persistent timeline for
mandatory retrieval. Its waiting treatment is a single text-only line; actual
future model tools receive the richer AI Elements Tool treatment.

## AI Elements

The official inline-citation example composes `InlineCitationCard`, trigger,
carousel, source metadata, and an optional `InlineCitationQuote`. The example
application supplies every URL, title, description, and quote itself. The
documentation also says inline citations still require application-owned
parsing and rendering because neither standard Markdown nor the AI SDK
provides an out-of-the-box citation contract.

Therefore AI Elements is a local presentation source, not a citation data or
authorization layer. Its card is useful when the product has meaningful
metadata or a quote to show. OrgMemory's current card has neither: it repeats a
title and a generic permission sentence, so the carousel adds interaction cost
without evidence value.

## Streamdown

OrgMemory already pins Streamdown 2.5 and wraps it in the local AI Elements
`MessageResponse`. Current official Streamdown security documentation provides
the necessary boundary for stored, untrusted Markdown:

- omit `defaultRehypePlugins.raw` so embedded HTML is escaped instead of
  rendered;
- retain `defaultRehypePlugins.sanitize`;
- configure hardening explicitly rather than accepting the permissive default
  link/image prefixes and protocols;
- keep link-safety confirmation for any permitted outbound navigation.

The Assistant answer renderer enables code, math, Mermaid, and CJK plugins for
model output. A source-document preview has a narrower job and must not inherit
that plugin set automatically. In particular, remote images, raw HTML, Mermaid,
and custom executable renderers are unnecessary for headings, lists, tables,
quotes, and code in an uploaded Markdown document.

## OrgMemory Judgment

Adopt:

- Onyx's separation between compact inline citation and ordered source
  exploration;
- the AI Elements inline citation trigger styling and accessible composition;
- AI Elements source/quote primitives only where OrgMemory supplies real,
  currently authorized evidence;
- a dedicated restricted Streamdown composition for Markdown documents, with
  escaped raw HTML, sanitized output, no automatic remote resources, and a raw
  text fallback.

Reject:

- a metadata-only `1/1` carousel for one source;
- treating a browser URL or component-provided quote as authority;
- importing Onyx state, packet, document, or opening behavior;
- embedding Office or unknown files merely because the browser can be asked to
  render them;
- reusing the Assistant answer renderer unchanged for untrusted stored
  Markdown;
- Onyx's avatar or leading icon in the waiting row;
- Onyx's client DOCX renderer, broad image match, naive CSV parser, and
  immutable-file caching semantics.

## AI SDK Streaming Data

Current AI SDK documentation defines `submitted` as waiting for the response
stream and `streaming` as actively receiving chunks. It supports transient
typed `data-*` parts through `onData`; transient parts are not added to message
history. `stop()` works in both states through request abort.

That is the correct contract for pre-first-token progress. OrgMemory should
emit transient, server-owned Assistant activity parts rather than persist them
with the transcript or disguise retrieval as a model tool call. The local UI
can use AI Elements' step/status presentation, but `Reasoning` is reserved for
actual model reasoning and `Tool` for actual `tool-*` parts.
