# Slack Connector Staging Design

## Outcome

A Slack workspace becomes a governed source. A crawl of one channel produces
`SourceObject`s with `source_type = SLACK`, sealed ACL generations carrying
`SOURCE_GROUP`/`SOURCE_USER` evidence and channel membership, and resolved
principal mappings — the exact ledger shape
`ExternalPrincipalRetrievalIntegrationTests` already proves retrievable. A
re-crawl that changes membership appends a new sealed generation and rotates the
head, so grants and revocations converge without re-ingesting content, under the
[ADR 0009](../../../decisions/0009-dynamic-source-acl-ceiling.md) live-source
ceiling. Two users with different channel membership get different grounded
answers; removing a user from the channel closes their access on the next crawl.

This increment is **fixture-driven**: the crawl payload comes from a committed
Slack-shaped fixture, not the live Slack API. The real Slack SDK adapter and a
Developer sandbox run are a deliberate follow-up increment
(`slack-connector-live`), because they need external credentials and add nothing
to the permission/convergence contract this slice locks down.

## Boundary

```mermaid
flowchart LR
    CRAWL[Slack crawl payload<br/>fixture now, Slack API later] --> STAGING[Versioned staging contract<br/>content / identity / permissions]
    STAGING --> ADAPTER[Connector ingestion use case<br/>core]
    ADAPTER --> MAP[SourcePrincipal + mapping ledger]
    ADAPTER --> ACL[Sealed ACL generation + membership + head rotation]
    ADAPTER --> RAW[RegisterRawSource - existing ingestion]
    RAW --> WORKER[Worker parse/chunk/embed/publish]
    ACL --> RETRIEVE[SecureKnowledgeRetrieval]
    MAP --> RETRIEVE
    WORKER --> RETRIEVE
```

Rules carried from vision and ADRs:

- Connectors never write domain memory tables directly. A narrow adapter
  consumes a **versioned staging contract** and calls existing core use cases.
- The staging contract has three separately-versioned payload kinds, mirroring
  Glean/Onyx: **content** (documents/messages), **identity** (users, groups,
  membership), and **permissions** (per-object ACL). Permissions and identity
  re-crawl on their own cadence, independent of content.
- Effective access stays the intersection of tenant, current sealed source-ACL
  generation, OpenFGA policy, classification, and lifecycle. The connector only
  supplies source ACL evidence; it can never broaden beyond what the payload
  states, and unmapped principals grant nothing.

## Key Decision — How The Connector Creates External-Principal ACLs

`KnowledgeIngestionService` deliberately refuses to seal a `COMPLETE` ACL that
carries external principals (the Phase-1 guard), because a raw upload has no
verified identity behind `SOURCE_USER`/`SOURCE_GROUP`. The connector is exactly
the trusted path that *does* carry that evidence. Rather than relax that guard,
this increment adds a dedicated **connector ingestion use case** in `core` that:

1. validates the staging batch (tenant, source system, target Knowledge Space,
   payload versions),
2. upserts observed `SourcePrincipal`s and runs the matcher
   (`SourcePrincipalMappingService`) to resolve them,
3. registers the raw source and seals an ACL generation whose entries and
   sealed group membership come from the permissions payload,
4. rotates the source ACL head to the new generation (compare-and-set),
5. hands off to the existing worker parse/chunk/embed/publish pipeline.

Re-crawl repeats steps 1–4 for a new generation; content steps run only when the
content payload's revision hash changed. This is the reconciliation loop.

## Scope

- `contracts/connector/` versioned JSON schema for the three payload kinds plus
  a batch envelope (tenant, source system, connection key, crawl cursor,
  payload versions). Not a Gradle module.
- Core connector ingestion use case + a `SLACK` source-system profile that maps
  payload shapes (channel = `SOURCE_GROUP`, member = `SOURCE_USER`, public
  channel = workspace group) to ACL entries and membership.
- Worker orchestration that reads a staging batch (fixture source now) and runs
  the use case, with per-object failure isolation, an idempotent crawl cursor,
  and tombstone handling for objects removed at the source.
- A committed Slack-shaped fixture batch and an integration proof: first crawl
  grants a member, a membership-change re-crawl grants a new member and revokes
  a removed one without re-ingesting content, and an unmapped principal is
  denied throughout.

Out of scope (next increment `slack-connector-live`): the real Slack Web API
adapter, OAuth/bot-token credential storage, rate-limit/Retry-After handling,
checkpoint/resume across large crawls, incremental webhooks, and the Developer
sandbox run. The staging contract is designed so that adapter is a drop-in
producer of the same batches.

## Exit Criteria

- A fixture crawl produces a searchable SLACK `SourceObject` whose access is
  governed solely by resolved channel membership; two members-vs-non-member
  users get different retrieval results.
- A re-crawl with changed membership converges grants and revocations by
  appending a sealed generation and rotating the head — content is not
  re-ingested (same content revision hash is a no-op on the content path).
- Removed-at-source objects are tombstoned out of retrieval.
- Unmapped or admin-injected principals never broaden access; every crawl
  decision is auditable.
- Staging payloads are versioned; an unknown payload version fails closed.
- `:core:test`, `:apps:worker:test`, and existing suites stay green.
