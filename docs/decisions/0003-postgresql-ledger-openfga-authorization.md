# 0003 — PostgreSQL Ledger And OpenFGA Authorization

## Status

Accepted on 2026-07-20.

## Context

Source systems provide historical ACL evidence while enterprise relationships
and OrgMemory actions need a versioned policy decision point. Search indexes and
graph stores cannot be security authorities.

## Decision

PostgreSQL remains canonical for source revisions, sealed ACL evidence/current
head, provenance, lifecycle, and audit. OpenFGA is the production relationship
authorization engine and a projection fed through a transactional outbox. A
resource becomes searchable only when expected ledger, tuple, and index versions
converge.

Effective permission is the intersection of tenant, ingestion ACL, current ACL,
OpenFGA relationship policy, classification, and lifecycle. Unknown identity,
stale ACL, model mismatch, projection mismatch, or OpenFGA outage fails closed.

## Consequences

Java coordinates freshness and convergence and may deny more; it does not keep a
second independent allow policy. OpenFGA does not replace immutable source ACL
history. Search prefilter is an optimization followed by authorization recheck.
