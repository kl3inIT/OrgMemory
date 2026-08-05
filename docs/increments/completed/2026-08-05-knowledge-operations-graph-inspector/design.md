# Knowledge Operations And Graph Inspector

## Problem

The Knowledge workspace is readable but not yet operationally complete:

- the upload dialog presents classification shorthand as if it were the whole
  effective audience, even though Knowledge Space and source authorization are
  intersected at serve time;
- the desktop document reader is still a modal Sheet, so it dims and locks the
  list instead of supporting rapid master-detail review;
- terminal ingestion failures hide their reason in a hover-only badge and offer
  no safe next action;
- the graph detail panel exposes a technically correct but visually flat list
  of description, connections, and numbered sources. It makes identity,
  relation direction, and evidence provenance harder to scan than necessary.

## Reference Evidence

The product behavior is grounded in pinned source, not a visual imitation.

| System | Revision | Useful behavior | Evidence |
| --- | --- | --- | --- |
| LightRAG | `9a45b64c2ee25b1d806e90db926a8af37480bb16` (`v1.5.4` pin) | The inspector derives adjacent relationships from the selected node, lets a relationship select its neighbor, and keeps expand/prune beside node context. Its raw property buckets and generic floating card are not a visual target. | `tmp/upstream-lightrag-v1.5.4/lightrag_webui/src/components/graph/PropertiesView.tsx`, `FocusOnNode.tsx` |
| Onyx | `618b5031bf21463f44e3bed9eb9d5073b806fec0` | File UI keeps processing state explicit and exposes view/remove actions as file operations. Upload/process/delete remain distinct lifecycle states; the UI does not pretend a failed item is complete. | `tmp/onyx/web/src/sections/modals/UserFilesModal.tsx`, `sections/cards/FileCard.tsx`, `lib/projects/svc.ts` |
| OrgMemory | `876246da` | `SplitLayout` already owns responsive graph details; Source Ledger has bounded durable ingestion jobs and idempotent publication, while the previous challenge forbids pre-publication deletion without a publication fence. | `apps/web/src/components/layouts/split-layout.tsx`, `core/.../SourceIngestionJob.java`, `KnowledgeAssetPublicationCoordinator.java`, `docs/increments/completed/2026-08-02-document-view-delete/challenge-verdict.md` |

## Product Design

### Document workspace

Desktop uses a real master-detail composition: the list remains visible and
interactive while the selected document occupies a bounded right reader. Mobile
retains the accessible modal Sheet because a persistent second column would
leave neither surface usable. Selection stays local to this increment; URL
deep-linking and server-side pagination remain separate work.

The upload form labels values as classifications, then explains that effective
access is the intersection of classification, selected Knowledge Space, and
current organization policy. It must not claim that the label alone is the
complete audience.

Terminal failure is a composed state, not a badge tooltip:

- show the bounded failure message inline in the list and reader;
- a `FAILED` manual upload may expose `Retry processing` only when the server
  says the action is available;
- `QUARANTINED` evidence is never retried unchanged because its stored evidence
  was rejected; offer `Upload corrected document` instead;
- READY retirement continues to require `knowledge_asset can_delete`;
  pre-publication delete/cancel remains forbidden by the prior challenge.

### Graph inspector

Retain LightRAG's useful interaction model but replace its raw-properties card
with an OrgMemory-native entity inspector:

- identity header with entity/relation kind, readable title, and close action;
- visible text actions for Expand and Hide instead of unexplained icon buttons;
- an `About` section with readable measure and line length;
- compact facts for connection and evidence counts;
- connections ordered as navigable neighbor rows: neighbor name first,
  relation label and incoming/outgoing direction second;
- evidence hydrated through the existing permission-rechecked citation excerpt
  endpoint, showing document title, heading/page context, and an explicit open
  action instead of `Source 1`;
- curation remains capability-gated and retains its current evidence contract.

No LightRAG or Onyx source is copied. The reference changes information
hierarchy and interaction decisions only.

## Rejected Retry Contract

The independent challenge rejected `POST /api/sources/{sourceId}/retry` for
this increment. Source Ingestion lacks a never-reused claim epoch and does not
carry an exact live claim through the multi-transaction Asset publication
boundary. Existing publication identity also does not pin the complete parser,
chunker, processing-profile, and deterministic chunk-manifest identity.

Resetting a FAILED job could therefore admit a new worker cycle while a stale
producer can still publish, or reconcile newly recorded processing metadata
against chunks prepared by an older attempt. The accepted product behavior is
visible bounded failure information and a corrected-upload path for rejected
evidence. Retry remains absent until a separate claim-fencing and exact
publication-recovery increment is designed and proven. See
`challenge-verdict.md`.

## Strongest Counterargument

Any manual retry can conceal a deeper publication race: a revision may be
marked FAILED after the asset publication transaction applied but before the
ingestion coordinator recorded READY. A naive new job or new revision could
duplicate projections, ownership, or active versions. The answer is not to add
Delete or clone the work. Retry must reset the unique existing job and revision,
reuse the idempotent publication outbox, and prove the applied-publication
recovery path. If repository evidence cannot prove that path, the backend retry
must be rejected and the UI limited to a visible failure plus corrected upload.

## Rejected Alternatives

- Copy LightRAG's floating raw-property card: it preserves its technical density
  and weak evidence labels, which is the problem reported here.
- Make the desktop Sheet non-modal: Radix supports `modal={false}`, but a Dialog
  still represents transient dialog semantics. The established `SplitLayout`
  is the correct persistent master-detail primitive.
- Allow creator-only deletion of failed work: the prior independent verdict
  explicitly rejects creator-list visibility as deletion authority and requires
  publication fencing for pre-publication cancellation.
- Retry quarantined evidence: the evidence blob is rejected and the failure is
  not made correct by repeating it.

## Scope

- Document upload copy, desktop master-detail/mobile Sheet composition, visible
  failure details, and corrected upload for quarantined evidence.
- Graph entity/relation inspector hierarchy and permission-verified evidence
  labels.
- Focused core/API/web/browser coverage and current-spec reconciliation.
- No pre-publication delete/cancel, physical purge, connector retry, arbitrary
  reindex, pagination, multi-file upload, graph authorization expansion, or
  evidence-rich-content rendering.
