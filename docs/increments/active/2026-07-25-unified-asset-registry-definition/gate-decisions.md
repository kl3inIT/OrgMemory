# Asset Registry POC Gate Decisions

Status: accepted for implementation on 2026-07-25. The independent Claude
Fable 5 debate was explicitly waived by the product owner; no independent
review is claimed. Stakeholder validation remains required before PR 5 can
close the POC.

## Frozen Demo Fixture

The fixture is synthetic and contains no customer or employee data.

| Kind | Stable fixture identity | Purpose |
| --- | --- | --- |
| Knowledge | `support.sla-and-escalation@1` | Authorized SLA tiers, escalation paths, and citation targets |
| Work Instruction | `support.classify-and-respond@1.0.0` | Triage and response steps plus acknowledgement |
| Prompt Template | `support.triage-customer-ticket@1.0.0` | Typed ticket variables, grounded response, and structured output |
| Capability Pack | `support.l1-onboarding@1.0.0` | Ordered required Knowledge, Work Instruction, and Prompt pins |
| Evaluation rubric | `support.triage-quality@1` | Classification, SLA, escalation, grounding, tone, and schema checks |

PR 5 will materialize eight deterministic mock tickets: billing question,
password reset, degraded service, confirmed outage, suspected security issue,
data-deletion request, duplicate ticket, and abusive message. Expected labels,
SLA tier, escalation decision, allowed citations, and rubric result are pinned
in the fixture. Until then this table is the frozen semantic contract.

Two actors prove the flow:

- `operations-lead` owns, authors, reviews through a distinct reviewer, and
  publishes the fixture Assets;
- `support-agent` can discover and use the released Pack but cannot see drafts,
  review, publish, withdraw, or change assignments.

## Common Transition Table

| Aggregate/axis | From | Action | To | Required invariant |
| --- | --- | --- | --- | --- |
| Draft | editable | submit | immutable Revision plus open Review | exact canonical payload digest is pinned |
| Review | open | request changes | changes requested | decision pins revision and digest |
| Review | open | approve | approved | author and approving reviewer differ |
| Review | open | reject | rejected | immutable decision and reason |
| Review | open | cancel | cancelled | no release may use the cancelled review |
| Release | approved Revision | publish | released | coordinate and digest are unique; snapshot is immutable |
| Availability | available | deprecate | deprecated | existing evidence remains readable |
| Availability | available/deprecated | withdraw | withdrawn | no new use; history remains auditable |
| Portfolio | active | retire | retired | no new authoring/release; retained records remain |

Corrections create a new draft and revision. Approval never floats to newer
bytes. A replacement release never rewrites an existing Pack pin.

## Permission Matrix

OpenFGA decides candidate object access; the application enforces transition,
payload, digest, and separation-of-duty invariants.

| Role | View/use released | Edit draft | Submit | Review | Publish | Withdraw | Manage roles |
| --- | --- | --- | --- | --- | --- | --- | --- |
| viewer | yes | no | no | no | no | no | no |
| editor | yes | yes | yes | no | no | no | no |
| reviewer | yes | no | no | yes | no | no | no |
| publisher | yes | no | no | no | yes | no | no |
| steward | yes | yes | yes | yes | yes | yes | yes |
| owner | yes | yes | yes | yes | yes | yes | yes |
| backup owner | yes | yes | yes | yes | yes | yes | yes |
| organization admin | yes | yes | yes | yes | yes | yes | yes |

Knowledge Space membership remains the containment ceiling. Direct Asset roles
cannot grant access outside the actor's organization and Knowledge Space.
Unauthorized identifiers return the same opaque denial as missing identifiers.

## Separation Of Duty

- A revision author cannot approve that revision.
- Publisher permission does not imply review permission.
- Publication requires an approval for the exact revision digest.
- Review decisions, releases, availability events, and audit events are
  immutable.
- Assistant and public MCP cannot approve, publish, withdraw, or manage roles.
- Owner, backup owner, steward, and organization admin are still subject to the
  self-approval prohibition.

The POC requires one independent approval. Multi-party/quorum approvals and
regulated electronic signatures are follow-on policy profiles.

## Retention Defaults

- Asset identity, immutable revisions, decisions, releases, availability
  history, relations, and audit evidence are retained for the life of the POC
  organization and are not hard-deleted through product APIs.
- Mutable abandoned drafts may be archived after 30 days of inactivity in a
  later maintenance increment; PR 1 does not add deletion.
- Prompt runs default to metadata-only retention. Raw sensitive variables and
  model output are not persisted unless a future explicit policy enables them.
- Withdrawal blocks new consumption but does not erase evidence.
- Knowledge retention continues to follow the existing Knowledge lifecycle and
  is not copied into the Asset Registry.

## OAuth And MCP Decision

PR 4 exposes one authenticated remote MCP protected resource under the same
OrgMemory issuer but a distinct MCP audience. It publishes OAuth protected
resource metadata and validates issuer, expiry, audience, and coarse
`assets:read`/`assets:use` scopes before object authorization.

Bearer passthrough is allowed only when the presented token explicitly contains
the MCP audience. If an API-audience token must call MCP, the gateway must use
token exchange/on-behalf-of; it must not reinterpret the API token as an MCP
token. Object authorization remains OpenFGA-backed and cannot be replaced by
OAuth scopes. PR 4 is read-only: no prompt execution, progress mutation,
review, publication, withdrawal, permission change, or installation.
