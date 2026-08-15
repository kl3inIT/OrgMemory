# Business Access Explanation

## Problem

The effective-access inspector now computes the correct layered result for a
Knowledge Asset, but the User Detail surface still presents the implementation
instead of the administrator's task. Its primary copy exposes OpenFGA,
relationship gates, Source ACL terminology, canonical content policy, raw
resource IDs, reason codes, and an authorization model identifier.

The administrator's actual question is narrower:

> Can this user use this document in secure search, why or why not, and does a
> newly checked result reflect the current permission state?

Production evidence on 2026-08-15 proves that the existing explanation path can
answer this question. Vũ Thị Lan's access to `DOC037 - Dự báo tài chính
2026.md` resolves through Executive Office membership, a viewer grant on the
Executive Knowledge Space, inherited asset access, and the current canonical
content gate. The API currently returns that path as typed UUID strings rather
than tenant-checked business labels, so the browser cannot present the reason
without guessing or performing unsafe lookups.

## Product References

Authorization developer tools and administrator products solve different
problems:

- OpenFGA Playground and SpiceDB Playground expose models, tuples, assertions,
  traces, and relation graphs for development and debugging.
- Azure IAM Check access presents current and inherited role assignments at a
  resource scope.
- Google Cloud Policy Troubleshooter starts from principal, resource, and
  permission, then presents the current outcome and the relevant policy or role
  binding.

OrgMemory will follow the administrator pattern. Raw relationship graphs are
not the default User Detail experience.

## Selected Design

### Primary task

Rename the surface to **Check document access** and frame it as one current
question about the selected user, document, and secure-search use.

The result hierarchy is:

1. **Current access** — Allowed, Denied, or Unknown.
2. **Business sentence** — whether the named user can use the named document in
   secure search.
3. **Access granted through** — one or more resolved assignment rows when an
   allowed relationship path exists.
4. **Document availability** — whether the current source-owned and canonical
   content restrictions allow the document to participate.
5. **Checked at** — the evaluation timestamp returned by the server.

### Resolved explanation boundary

The API remains protected by `can_view_audit` before any target user or resource
metadata is resolved. After that guard succeeds, each explanation step may add
a tenant-owned business label for supported object types:

- organization;
- department (`organizational_unit`);
- Knowledge Space;
- Knowledge Asset.

The existing raw path remains a protocol compatibility field, but the web
surface does not render raw object IDs, relation names, reason codes, policy
versions, or provenance. The browser consumes only server-resolved labels and
never guesses a label from an ID.

### Assignment presentation

The web maps the resolved path into concise assignment rows:

- **Department member** — granted through a named department and applied through
  a named Knowledge Space;
- **Organization member** — granted through the named organization and applied
  through a named Knowledge Space;
- **Direct access** — used when no group/organizational derivation exists.

The row is an explanation of the current relationship path, not a new role
model and not an editable grant.

### Denied and unknown results

A denial identifies the business layer that stopped access without exposing a
denied resource outside the already-authorized audit boundary:

- no current user assignment grants access;
- the user assignment allows access but current document restrictions deny it.

Unknown remains unresolved and is never styled or described as Denied.

### Visual direction

Use the existing OrgMemory cards, badges, tokens, and typography. Remove the
split developer-debugger layout. Keep one strong outcome panel, one compact
assignment table, restrained success/danger/warning color, and enough whitespace
for browser use and presentation capture. Preserve light/dark themes, keyboard
access, loading/error states, and responsive stacking.

## Scope Limits

- No OpenFGA model, relation, tuple, or permission behavior changes.
- No retrieval, citation, source ACL, classification, publication, lifecycle,
  or current-version enforcement changes.
- No tenant-wide document picker or document enumeration endpoint.
- The existing exact resource input remains until a separately authorized
  selector contract exists; its visible copy becomes business-facing without
  implying search-by-name capability.
- No raw technical-details drawer on User Detail.

## Strongest Counterargument

Removing technical details could make authorization incidents harder to debug,
and translating a relationship path into business labels could oversimplify
multiple or unusual derivations.

The selected response is to preserve the raw path in the protected API and in
backend diagnostics while keeping the ordinary User Detail surface task-focused.
The UI renders every resolved grant row it can prove; it does not invent a
single causal explanation when the path is unresolved. Engineering debugging
continues through protected API/CLI evidence rather than a buyer-facing screen.

## Verification

- Backend integration tests prove tenant-checked labels and no pre-guard
  metadata resolution.
- Frontend unit tests prove business copy, assignment derivation, denied and
  unknown semantics, absence of technical terminology, and timestamp display.
- OpenAPI/client generation, typecheck, lint, unit tests, and production build
  pass.
- A real browser verifies Allowed and Denied fixture users against DOC037.
- The final production capture is compared with the approved prototype and used
  as the Slide 8 proof image only after deployment verification.
