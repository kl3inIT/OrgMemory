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

## Architecture Challenge Verdict

The 2026-08-02 adversarial review ran two independent architecture positions
for two rounds and then gave the verbatim record to a fresh judge that could
not inspect the repository. Fable had no remaining tokens, while the available
Grok, OMP, and Pi launchers were not installed, so both positions used separate
Codex `gpt-5.6-sol` high-reasoning sessions. The debate transcript remains an
untracked `tmp/` artifact; this section is its durable outcome.

The judge selected typed, policy-constrained Knowledge Spaces:

- every Space persists one versioned audience mode: `ORGANIZATION`,
  `DEPARTMENT`, or `RESTRICTED/CUSTOM`;
- only `ORGANIZATION` may admit `organization#member` as a Space audience;
- `DEPARTMENT` requires one owning department, implicitly admits that
  department, and rejects organization-wide viewer grants in the backend;
- mixed collaboration uses `RESTRICTED/CUSTOM`, which starts closed and admits
  only explicitly approved audiences;
- Space eligibility remains necessary but never sufficient for document
  access. Canonical visibility is the intersection of tenant, valid Space
  policy, relationship eligibility, Source ACL, classification, lifecycle,
  and other retrieval eligibility gates;
- `can_manage_acl`, `can_publish`, `can_create_asset`, and `can_view` must be
  independent. Operational administration never grants implicit content read;
- a Space mode change is a versioned governed transition with an impact
  preview, immutable preview hash, reason, independent approval, reversible
  tuple diff, audit record, post-change verification, and bounded rollback;
- widening fails closed until policy and tuple projection agree. Narrowing is
  effective at the policy gate before asynchronous tuple cleanup completes;
- changing a Space audience never rewrites sealed native-upload ACL evidence or
  bypasses a live source's authoritative ACL. Broader native-document access
  requires a separately authorized ACL generation;
- emergency content access is explicit, narrowly scoped, time-bound,
  independently approved, automatically expired, and unable to bypass tenant,
  Source ACL, classification, lifecycle, legal, or evidence-integrity gates.

The rejected alternative was to treat current OpenFGA tuples as the complete
Space business definition. Tuple-only state cannot distinguish a legitimate
audience from drift, preserve a durable department promise, or protect Space
metadata and future content when a downstream gate is absent. Its useful
mechanics are retained as implementation details: versioned mutation APIs,
grant provenance, impact previews, reversible diffs, cache invalidation, and a
layered inspector.

This verdict expands the increment's design dependency but not its immediate
implementation scope. The inspector repair may expose mode validity and the
layered final decision, while persistence, model changes, tuple migration, and
administrator-capability separation require a subsequent authorization
increment and their own characterization tests.
