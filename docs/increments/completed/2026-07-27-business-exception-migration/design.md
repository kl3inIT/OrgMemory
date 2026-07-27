# Business Exception Migration

Status: completed

## Problem

The API still has a compatibility `IllegalArgumentException -> 400` translator.
That translator cannot distinguish a rejected request from a programming error,
an invalid runtime configuration, or a broken internal invariant. It therefore
turns some server defects into misleading client errors.

## Boundary

This increment migrates expected failures reachable from delivery use cases:

- invalid caller input and unsupported operations become `VALIDATION`;
- invalid lifecycle transitions and duplicate state become `CONFLICT`;
- absent or tenant-mismatched resources use an opaque `NOT_FOUND` contract;
- temporary storage or dependency failures become `UNAVAILABLE`;
- controller-only request translation uses `ApiRequestException`.

`IllegalArgumentException` remains valid for constructors, value objects,
configuration properties, worker limits, parser internals, and programmer
invariants. If one of those failures unexpectedly crosses the API boundary, the
generic handler returns `internal.unexpected` with status 500.

## Inventory

The migration covers deliberate caller/business failures in:

- Asset creation, lifecycle, prompt execution, Skill package staging, and
  Assistant Asset tool confirmation;
- source upload, Knowledge Space administration, graph exploration/curation,
  connector administration, and source-principal administration;
- organization invitation and role administration;
- direct controller validation still expressed as `ResponseStatusException`.

The following remain invariant failures rather than public API contracts:

- typed configuration property validation;
- authorization convergence batch limits;
- canonical evidence structures and graph projection invariants;
- ingestion worker transition invariants;
- entity, record, value-object, and serialization constructors.

## Error Contract

Every expected failure has:

- a transport-neutral `BusinessErrorCategory`;
- a stable dotted machine code;
- a deliberately safe public message;
- an optional internal cause that is logged but never serialized.

The global API handler maps only categories and framework failures. It does not
list domain exception classes and does not contain a compatibility path for
`IllegalArgumentException`.

## Verification

- focused service tests cover representative validation, conflict, not-found,
  and unavailable behavior;
- API handler tests prove category mapping and prove a raw
  `IllegalArgumentException` is an unexpected 500;
- a reflection contract test prevents the broad
  `IllegalArgumentException` handler from returning, and a repo-wide source
  check rejects controller `ResponseStatusException`;
- backend static analysis, Modulith tests, and terminating `clean test` pass.
