# OpenFGA Model Rollout Repair

## Problem

The production application pins every authorization request to
`ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID`, but the deployment lifecycle writes
that identifier only during first-store bootstrap. Later releases update the
repository-owned `model.fga` without writing a new immutable model version or
changing the pinned identifier.

This became user-visible when the multi-provider control plane added
`organization#can_manage_ai`. The web and API images deployed successfully,
while the production API continued checking an older model that did not contain
that relation. Organization administrators could enter the admin shell but the
Language Models and Index Settings requests failed closed.

Direct SSH evidence was unavailable during diagnosis because the ZM host timed
out from the current workstation. Repository and workflow evidence still proves
the lifecycle defect:

- `bootstrap-openfga.sh` creates a store and model only when both IDs are empty;
- `deploy.sh` requires and reuses the existing model ID without writing the
  current `model.fga`;
- production successfully deployed the application commit containing
  `can_manage_ai`.

## Selected Design

Treat the authorization model as a versioned release input:

1. Keep one durable OpenFGA store and all existing tuples.
2. Compute SHA-256 over the repository model used by the release.
3. If that digest differs from the digest pinned in the host environment, write
   the model into the existing store with the official OpenFGA CLI.
4. Parse and validate the returned `authorization_model_id`.
5. Atomically persist the new model ID and digest before recreating API and
   worker containers.
6. Keep every application request explicitly pinned to that model ID.
7. On a failed deployment, restore the prior environment and recreate the prior
   image set with its prior model ID. The unused immutable model version may
   remain in OpenFGA.

First-store bootstrap writes both the initial model ID and digest. Existing
installations have no digest, intentionally forcing one model write on the first
deployment containing this repair.

The official OpenFGA guidance says models are immutable, each write creates a
new version, production clients should pin a specific model ID, and adding a
relation requires writing the model before application code starts using it.

## Strongest Counterargument

The application could stop sending an authorization model ID and let OpenFGA
use the latest version. That would make a newly written model visible without
updating application configuration.

This is rejected because "latest" disconnects a running binary from the policy
version it was tested against. A later or accidental model write could change
authorization for every replica immediately, and rollback of the application
would not restore its compatible policy. Explicit pinning is the safer
production contract.

Writing a model on every deployment is also rejected. OpenFGA models are
immutable and cannot be deleted, so identical releases would accumulate
unnecessary versions. The digest makes unchanged model delivery a no-op while
forcing legacy installations through one repair write.

## Architecture Challenge

This changes the authorization deployment boundary and therefore requires an
independent challenge. The configured Claude reviewer remained unavailable due
to the previously reported quota limit. The project owner had already directed
this session to continue without the Claude discussion step and explicitly
asked for the production bug to be fixed. The counterargument above, repository
evidence, official OpenFGA lifecycle guidance, rollback behavior, and negative
tests are recorded here in place of that unavailable review.

## Scope

- production Compose operations service for writing the repository model;
- first-store bootstrap model digest;
- production deployment model write, atomic pin, no-op, and rollback;
- deterministic shell regression coverage;
- deployment runbook, architecture, and authorization coverage updates.

No OpenFGA relation, tuple, application role, or browser authorization bypass is
changed by this repair.

## Exit Gates

- OpenFGA model validation and store tests pass;
- production Compose interpolation and shellcheck pass;
- deterministic tests prove upgrade, unchanged-model no-op, and failed-canary
  rollback to the prior model ID;
- documentation checks pass;
- PR CI passes, the PR merges, production deploys the immutable release, and an
  authenticated administrator can load both affected screens.
