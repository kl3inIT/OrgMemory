# Effective Access Inspector

## Problem

The administrator user-permissions page describes its single-resource result as
effective access, but `AdminPermissionController` currently calls
`AccessExplanationService.explain(...)` without source ACL provenance. The
service therefore answers only the OpenFGA relationship layer and defaults the
provenance to `ORGMEMORY`.

This is materially misleading for governed Knowledge Assets. Production on
2026-08-02 returned `ALLOWED` for Vũ Thị Lan on Knowledge Asset
`64d065f5-093e-4c88-910d-161b95134a90` through this relationship path:

1. the user is an organization member;
2. `organization#member` is a viewer of Human Resources Knowledge;
3. the asset inherits `can_view` from that Knowledge Space.

The canonical ledger independently proves the content is not visible to Lan:
the active asset is `DOC006 - Quy trình tuyển dụng.md`, its current and ingestion
ACL snapshot defaults to `DENY`, and its only allow entry names the Human
Resources department. Lan belongs to Executive Office. The secure retrieval
query therefore filters it out even though OpenFGA's relationship gate passes.

The same screen renders the stable UUID as the primary label at every hop,
repeats one Knowledge Space for its computed and direct relations, exposes the
raw authorization model ID without context, and places the unrelated
`app_users.role` value beside the person without saying that it is not an
OpenFGA grant.

## Proposed Design Under Challenge

For `knowledge_asset#can_view`, make the administrator result a layered answer:

- the primary verdict is final content visibility from the same
  `KnowledgeEvidenceScopeResolver` used by canonical retrieval;
- the OpenFGA relationship verdict and derivation remain visible as one
  diagnostic gate, never as the final answer;
- source ACL, publication, lifecycle, classification, and current-version
  eligibility are represented as the canonical content gate;
- an unavailable canonical gate yields `UNKNOWN`, never a guessed denial or
  allowance;
- other resource types and permissions remain explicitly labelled as
  relationship-policy checks.

Require the dedicated audit permission before returning content-access
diagnostics or resolved asset metadata. Resolve human labels only after that
guard succeeds. Render names first, UUIDs as secondary copyable details, and
merge adjacent steps that refer to the same object.

## Strongest Counterargument

The inspector should remain a pure OpenFGA debugger and merely be renamed.
`KnowledgeEvidenceScopeResolver` answers the retrieval/catalog contract, lists
the subject's whole OpenFGA-visible asset set, and applies publication and
index-readiness conditions that may be stricter than a generic meaning of
"may view this resource." Reusing it could make the diagnostic expensive,
could label an unindexed but otherwise authorized asset as denied, and could
hide the exact failing ACL/lifecycle gate behind one broad canonical result.

Resolved titles also create a metadata disclosure path. A member administrator
is not automatically entitled to learn the names of documents they cannot
read, and a tenant-wide resource picker would turn a one-ID diagnostic endpoint
into a document enumeration endpoint.

## Scope Limits

- No OpenFGA model, relation, or tuple changes.
- No retrieval enforcement changes.
- No tenant-wide document picker unless its metadata authorization contract is
  independently justified.
- No title, source URI, snippet, or count is returned before the diagnostic
  privilege and tenant ownership are verified.

## Architecture Challenge

See [challenge brief](challenge-brief.md) and the pending
`challenge-verdict.md`. Implementation starts only after the independent
verdict resolves the primary-verdict and metadata-boundary questions.

