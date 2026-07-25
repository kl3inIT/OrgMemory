# Asset Projection Generation Repair

## Problem

OrgMemory has two independent monotonically increasing values:

- the immutable Knowledge Asset version generation attached to chunk and graph
  evidence;
- the projection publication generation that advances whenever a namespace
  publishes a new copied-forward snapshot.

`StoreBackedAuthorizedQueryProjection` incorrectly assigned the publication
snapshot generation to a selected chunk. A graph contribution from asset
generation `1` therefore conflicted with the same chunk copied into publication
snapshot `5`. The strict evidence-closure guard correctly failed closed.

Pinned LightRAG `v1.5.4` does not have these two generation axes. It tracks graph
provenance with stable chunk IDs and resolves selected content directly from the
text-chunk store. The defect is in OrgMemory's enterprise projection adapter,
not in the upstream retrieval algorithm.

## Decision

- Publish `assetProjectionGeneration` with every chunk content, lexical, and
  vector record.
- Read the chunk generation only from that immutable metadata.
- Missing, malformed, or non-positive values fail closed.
- Never fall back to the namespace publication generation.
- Keep evidence-closure and canonical citation checks strict.
- Backfill existing PostgreSQL content records from canonical
  `knowledge_chunks.projection_generation` using the adapter's Base64
  key/value metadata codec.

## Regression Proof

Tests must keep the asset generation and publication generation independent.
The adapter must return the asset generation even after later assets advance
the namespace snapshot, and invalid legacy metadata must fail closed.
