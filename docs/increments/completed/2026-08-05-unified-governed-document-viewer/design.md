# Unified Governed Document Viewer

## Problem

OrgMemory currently has two document-reading implementations:

- the Knowledge workspace uses a persistent desktop master-detail panel and a
  mobile Sheet;
- Assistant citations use a separate centered Dialog with their own fetch,
  loading, format dispatch, Markdown, download, and error UI.

The duplication creates inconsistent behavior and constrains long PDF,
Markdown, and image evidence to a narrow reader in the Knowledge workspace.
The previous master-detail choice optimized rapid list scanning, but the actual
product need is sustained reading of governed evidence.

## Pinned Reference

Onyx revision `618b5031bf21463f44e3bed9eb9d5073b806fec0` uses one document-opening
contract across chat citations, source cards, search results, and user files:

- `web/src/lib/search/utils.ts` routes a file document into a shared
  `presentingDocument` state;
- `web/src/app/app/message/MemoizedTextComponents.tsx` sends inline citations
  through that same `openDocument` function;
- `web/src/sections/document-sidebar/ChatDocumentDisplay.tsx` does the same for
  source-list rows;
- `web/src/views/AppPage.tsx` renders one `PreviewModal` from the shared state;
- `web/src/sections/modals/PreviewModal/PreviewModal.tsx` owns loading, failure,
  header, body, and footer while format variants own content rendering;
- PDF, Markdown, DOCX, image, CSV, and XLSX variants choose their own useful
  viewport size, with long-document formats using the full modal.

OrgMemory adopts this interaction architecture, not Onyx's data or trust
model. Citation and source content continue to use OrgMemory's separate
permission-rechecked endpoints and opaque failure behavior.

## Design

Create one shared `GovernedDocumentViewer` with a discriminated target:

- a Knowledge document target carries the selected `SourceResponse` and reads
  the current permission-verified original through `readSourceContent`;
- an Assistant citation target carries its public source reference and reads
  the permission-rechecked excerpt first, then the original only when the
  server authorizes a previewable presentation kind.

The viewer owns the common shell and presentation states:

- centered responsive Dialog, up to `92vw` by `86vh`, and effectively
  full-screen on narrow viewports;
- compact title, provenance/status metadata, close action, content area, and
  footer actions;
- loading, access-changed, excerpt-only, unsupported/download-only, and empty
  states;
- one renderer for PDF, image, safe rendered Markdown with raw toggle, and
  plain text;
- object URL creation/revocation and download behavior.

Callers own only selection and product-specific actions. The Knowledge table
remains full width when a document is selected. The Assistant's source sidebar
remains useful for comparing cited/found sources, but selecting a source opens
the shared centered viewer over the workspace. Inline citations and sidebar
rows therefore converge on the same reading surface.

## Security Boundary

- The browser never converts a source-list item into authorization. It opens
  only the canonical source or citation endpoint already returned by the API.
- Citation loading remains excerpt-first. A denied or stale citation exposes no
  title, alternative source, or original bytes beyond data already present in
  the answer contract.
- Markdown continues through `RestrictedMarkdown`; arbitrary source HTML is not
  rendered.
- Blob URLs are local, revoked on target change/unmount, and never persisted.
- External sources continue to open as external links rather than being fetched
  through the governed viewer.

## Strongest Counterargument

A persistent master-detail reader keeps the document list visible and reduces
click cost during rapid triage. That is useful for short metadata inspection,
but OrgMemory's selected surface renders original evidence, and common evidence
formats need substantially more width and height. The table already carries
the operational metadata needed for triage; the reader should optimize the
different task of reading. The centered viewer therefore wins for Documents,
while the graph keeps its right inspector because preserving canvas context is
part of that interaction.

## Scope

- Shared viewer shell, loaders, format renderers, and states.
- Knowledge document and Assistant citation integration.
- Focused unit/browser regression coverage and current-spec reconciliation.
- No backend, authorization, generated-client, document-parser, graph-inspector,
  pagination, or lifecycle change.

