# Active Increments

Every implementation-active increment has one dated directory containing
`design.md` and `plan.md`. The design owns scope and rationale; the plan owns
execution. Both are authoritative only while the directory remains here.

[The roadmap](../../roadmap.md#active) is the single delivery-status index.
Do not duplicate its queue or status in this README.

Before closing an increment:

1. finish or explicitly defer its exit gates with evidence;
2. reconcile current facts into architecture and the affected spec/test pairs;
3. record reusable mechanics in guidelines and significant rationale in a
   decision;
4. refresh spec/test `Source:` and `Reconciled:` provenance;
5. move the whole directory to `../completed/`;
6. update the roadmap status.

Design-only future increments may remain here when they form an explicitly
dependency-ordered program, but they are not implementation-active until their
predecessor exit gates pass.

## Native Identity Provisioning Dependency Order

```text
completed tenant hardening
  -> SCIM provisioning foundation
  -> SCIM User lifecycle private beta
  -> inert SCIM Directory Groups
  -> optional Directory Group authorization mapping
  -> SCIM operations and certification
```

- [Provisioning foundation](2026-07-27-scim-provisioning-foundation/design.md)
- [User lifecycle](2026-07-27-scim-user-lifecycle/design.md)
- [Directory Groups](2026-07-27-scim-directory-groups/design.md)
- [Optional authorization mapping](2026-07-27-directory-group-authorization/design.md)
- [Operations and certification](2026-07-27-scim-operations-certification/design.md)

No later stage may bypass the predecessor's schema, security, compatibility,
and rollback gates.
