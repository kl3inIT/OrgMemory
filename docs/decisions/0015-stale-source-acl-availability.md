# 0015 — Stale Source ACL Availability

## Status

Accepted on 2026-07-27. Supersedes only the hard freshness-window denial in
ADRs 0003 and 0009.

## Context

OrgMemory stores every connector permissions crawl as an immutable, sealed ACL
generation and makes the newest successful generation current. The original
policy also denied all reads when that current generation became older than 23
hours.

That bound reduced the stale-revocation window during a connector outage, but it
made all otherwise authorized knowledge disappear after one missed crawl. It
also incorrectly affected native uploads. A connector outage therefore became
an organization-wide knowledge outage even though the last verified permission
state, content, OpenFGA relationships, and projections were intact.

## Decision

Retrieval continues to enforce the latest complete, sealed ACL generation after
its freshness timestamp. Freshness is health evidence, not a universal
authorization gate.

The remaining gates stay unchanged:

- organization and active lifecycle;
- OpenFGA object authorization;
- latest sealed source ACL generation;
- deny entries and resolved source principals/groups;
- classification and declared-access rules;
- current Knowledge Asset version and publication model.

A successful permissions crawl still rotates the head immediately, so grants
and revocations take effect without content re-ingestion.

Connector health must expose stale permission syncs to operators. A future
source policy may opt sensitive connectors into bounded fail-closed behavior,
but it must be explicit and must not be the default.

## Consequences

During a connector outage, access reflects the last successfully synchronized
source permissions. Revocation latency can therefore exceed the normal crawl
cadence until synchronization recovers. In exchange, a missed crawl no longer
removes every document, graph contribution, citation, and assistant answer from
otherwise authorized users.

Unknown, incomplete, unsupported, or unsealed ACL evidence still grants
nothing. This decision does not permit administrators to manufacture connector
ACL generations or bypass source principal mapping.
