# Adversarial Challenge: Effective Access Inspector

You are an independent architecture reviewer. Attack the proposal; do not
validate it by default. Verify every claim in the repository itself. Read
`CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/permission-evidence-and-audit.md`,
`docs/specs/domains/identity-and-organization.md`, the filenames under
`docs/decisions`, and the source paths cited below. Remain read-only.

## Product Promise at Stake

OrgMemory is a governed organizational-memory layer. A document may be returned
only when tenant ownership, mirrored source ACL, OpenFGA relationships,
classification, lifecycle, publication, and current canonical evidence all
allow it. The administrator screen must explain that boundary without widening
it or leaking denied metadata.

## Exact Proposal Under Review

> For `knowledge_asset#can_view`, the admin inspector's primary verdict is the
> result of `KnowledgeEvidenceScopeResolver`, the same canonical visibility
> scope used by retrieval. The OpenFGA check remains a separately labelled
> relationship gate and derivation. If canonical resolution is unavailable the
> primary verdict is `UNKNOWN`. Resolved asset labels require a dedicated audit
> permission and tenant validation; identifiers are secondary. Other resource
> or permission checks stay explicitly relationship-only. Do not add a
> tenant-wide document picker in this increment.

Current enforcement and UI paths:

- `apps/api/src/main/java/com/orgmemory/api/admin/AdminPermissionController.java`
- `core/src/main/java/com/orgmemory/core/authorization/AccessExplanationService.java`
- `core/src/main/java/com/orgmemory/core/knowledge/retrieval/KnowledgeEvidenceScopeResolver.java`
- `core/src/main/java/com/orgmemory/core/knowledge/retrieval/SecureKnowledgeRetrievalStore.java`
- `apps/web/src/features/admin/components/access-inspector.tsx`
- `apps/web/src/features/admin/components/access-path.tsx`

## Comparable-System Evidence

| System | Source evidence | What it demonstrates |
| --- | --- | --- |
| Onyx | `D:/OrgMemory/tmp/onyx/backend/onyx/db/document_access.py` | Direct document fetches apply the same external user/group access filter instead of trusting a coarser container relationship. |
| Onyx | `D:/OrgMemory/tmp/onyx/backend/ee/onyx/external_permissions/post_query_censoring.py` | Source-specific permission failures remove chunks; an exception fails closed for that source. |
| OrgMemory current retrieval | `core/src/main/java/com/orgmemory/core/knowledge/retrieval/SecureKnowledgeRetrievalStore.java` | Canonical visibility intersects OpenFGA's candidate set with sealed ingestion/current ACLs, lifecycle, publication, classification, and current evidence. |
| OrgMemory current relationship explainer | `core/src/main/java/com/orgmemory/core/authorization/AccessExplanationService.java` | Its verdict intentionally comes from relationship check ports and defaults provenance to OrgMemory when the caller supplies none. |

## Observed Production Incident

At deployed commit `ce1a970b704ae4e86404e6372a909128de8e905b`, the
screen showed Vũ Thị Lan as `Allowed` for Knowledge Asset
`64d065f5-093e-4c88-910d-161b95134a90`. Production ledger evidence on
2026-08-02 identifies it as `DOC006 - Quy trình tuyển dụng.md` in Human
Resources Knowledge. Its current snapshot is `COMPLETE`, defaults to `DENY`,
and contains one `ALLOW` for Human Resources department
`d2000000-0000-4000-8000-000000000002`. Lan belongs to Executive Office
`d2000000-0000-4000-8000-000000000008`. The relationship model nevertheless
allows every organization member through the space viewer tuple, so the two
layers legitimately disagree and the UI incorrectly presents the first as the
final answer.

## Questions You Must Resolve

1. Is `KnowledgeEvidenceScopeResolver` the correct reusable authority for a
   single-resource final verdict, or does its index/publication contract make it
   the wrong abstraction?
2. If it is wrong, what existing boundary can answer final visibility without
   duplicating authorization SQL?
3. Which permission may resolve and display a denied asset's title without
   violating `docs/guidelines/agent-safety.md`?
4. Should the screen show one final verdict plus gates, or only independently
   labelled gate states with no synthesized verdict?
5. What focused negative tests are mandatory before this can ship?

Return plain Markdown with: **Verdict**, **Must-fix items**, **Repository
evidence for every claim**, **Strongest surviving counterargument**, and
**Rejected alternative**.

