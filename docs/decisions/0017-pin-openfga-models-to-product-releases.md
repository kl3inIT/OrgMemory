# 0017 — Pin OpenFGA Models To Product Releases

## Status

Accepted on 2026-07-29 by explicit project-owner direction.

The repository-required independent Claude challenge was unavailable because
the configured account remained over quota. The project owner had already
directed the delivery loop to continue without that reviewer and then reported
the production authorization failure for repair. The proposal, strongest
counterargument, repository evidence, current official OpenFGA guidance, and
rollback test are recorded in the
[increment design](../increments/active/2026-07-29-openfga-model-rollout/design.md).

## Context

OpenFGA authorization models are immutable. Writing one produces a new model
ID, while tuples remain in the store. Production OrgMemory requests explicitly
send one configured model ID so every binary uses a known policy version.

The initial deployment created one store and model, then persisted both IDs.
Subsequent deployments updated application images and the repository model but
never wrote another model version. Code could therefore start checking a
relation that did not exist in the pinned production model. This happened when
`can_manage_ai` shipped: authorization correctly failed closed, but valid
organization administrators lost access to the new AI settings endpoints.

## Decision

The repository OpenFGA model is a versioned product-release input.

- First-store bootstrap persists the store ID, model ID, and SHA-256 of the
  model bytes.
- A production deployment compares the release model digest with the pinned
  digest.
- A missing or changed digest writes a new immutable model into the same store
  before application containers are recreated.
- The deployment atomically persists the returned model ID and digest, and all
  application calls remain explicitly pinned to that model ID.
- An unchanged model is a no-op and does not create another immutable version.
- Failed deployment rollback restores the previous images, model ID, and
  digest. A newly written but unused model may remain in the store.

Tuple migration remains an explicit concern for model changes that add, rename,
or remove tuple-bearing relations. The deployment mechanism orders and pins the
model; it does not invent or rewrite tuples.

## Strongest Counterargument

Omit `authorization_model_id` and let OpenFGA select the latest model. That
removes the configuration update and would have hidden this deployment bug.

This is rejected because a model write would then change authorization for
running replicas independently of their binary version. An accidental write
could affect production immediately, gradual rollout would be impossible, and
application rollback would not restore the prior policy. OpenFGA recommends
pinning a specific model ID in production.

## Consequences

- Application and authorization policy rollback are one environment rollback.
- Legacy environments intentionally write one current model because they have
  no stored digest.
- Identical product releases do not accumulate model versions.
- Model changes must keep the immediately previous binary/model combination
  rollback-safe or explicitly use a staged migration.
- Deployment CI must test upgrade ordering, unchanged-model no-op, and rollback
  of the model pin.

## References

- [OpenFGA immutable authorization models](https://openfga.dev/docs/getting-started/immutable-models)
- [OpenFGA model migrations](https://openfga.dev/docs/modeling/migrating/migrating-models)
- [OpenFGA CLI model versions](https://openfga.dev/docs/getting-started/cli)
