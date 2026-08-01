# Permission Evidence And Audit Spec

Source: `core/src/main/java/com/orgmemory/core/permission`,
`core/src/main/java/com/orgmemory/core/authorization`,
`core/src/main/java/com/orgmemory/core/knowledge`,
`apps/api/src/main/java/com/orgmemory/api/admin`, and
`integrations/authorization-openfga`.

Reconciled: `2026-08-02-effective-access-inspector (c57bea58)`.

## Current Behavior

Source ACL snapshots/entries are sealed immutable evidence. A compare-and-set
head selects the monotonically increasing current generation and rejects stale
writers. Entries cannot be inserted after sealing; update/delete/truncate are
database-rejected. Unsupported or incomplete principals are quarantined.

Permission audit is append-only and written in an independent transaction. It
stores actor/resource/operation/decision/reason/policy/request/query fingerprint
and selected ACL snapshot IDs. Raw query text and unrestricted metadata are not
stored. An audit attempt does not prove the surrounding business transaction
committed.

Administrator actions on the identity ledger append an event attributed to the
acting administrator alongside the mapping service's event attributed to the
affected user; the pair answers who acted and who was affected. A per-connection
identity trust decision is audited the same way, and any non-default trust level
also records on the connection row who decided it and when.

AI gateway create/update/disable, credential rotation, route selection, and
connection probes append sanitized audit events. Events identify the profile,
preset, or workload and a bounded machine outcome; API keys, ciphertext,
provider response bodies, prompts, and completions are never audit fields.

Effective-access inspection is an audit capability rather than an implication
of member administration. `can_view_audit` is checked before the target user,
tenant-owned resource, document title, Space name, or classification is
resolved. A Knowledge Asset `can_view` response distinguishes the OpenFGA
relationship result from the canonical content-policy result and exposes their
intersection as the final verdict. Other resource and permission combinations
are labeled relationship-only. The UI uses resolved names as primary labels and
keeps identifiers and policy reason codes in collapsed technical details.

## Source Modules

- `core.permission`
- `core.knowledge` ACL records
- `apps.api.permission`
- `apps.api.admin`
