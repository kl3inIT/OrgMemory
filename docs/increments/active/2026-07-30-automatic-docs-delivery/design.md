# Automatic Public Docs Delivery Design

## Problem

The public portal is independently buildable and rollback-capable, but a merged
docs change still requires two manual workflow dispatches. A green `main`
commit therefore remains unpublished until an operator notices, builds its
image, and deploys it.

## Decision

Keep docs isolated from the six-image product release train and automate its
existing two workflow stages:

```text
CI push on main
  -> Build docs image
       -> no-op when Public docs · Node 24 was skipped
       -> publish ghcr.io/kl3init/orgmemory-docs:sha-<commit>
          when that CI job succeeded
  -> Deploy docs
       -> no-op when no image was published
       -> no-op when a newer descendant docs image was published
       -> deploy exact immutable image through production environment
```

Both workflows retain `workflow_dispatch`. Manual build supports an exact green
`main` commit. Manual deploy additionally requires `confirm_deploy` and
supports a verified older ancestor for intentional rollback. A manual image
build never initiates an automatic deployment.

## Safety Invariants

- Privileged `workflow_run` paths accept only successful upstream `push` runs
  from this repository on `main`; pull-request, fork, and manual CI runs cannot
  initiate publication.
- The build checks out only the SHA carried by that trusted CI run, after it
  proves the commit is in `origin/main`.
- Automatic publication requires the exact triggering CI run to contain a
  successful `Public docs · Node 24` job.
- Automatic deployment accepts only a build that was itself triggered by the
  trusted CI workflow and requires its exact run to contain a successful
  `Publish immutable docs image` job.
- Before production access, the planner downloads that build's named release
  artifact and verifies its commit, immutable image reference, and digest.
- A successful workflow with a skipped publish job cannot trigger a mutation.
- A published docs image is stale only when a newer descendant docs image has
  already been published. A later non-docs commit does not suppress the last
  verified docs change.
- The protected `production` environment and SSH secrets are attached only to
  the mutating deploy job, after unprivileged planning succeeds.
- Package-write permission exists only on the image-publishing job, and
  package-read permission exists only on the mutating deploy job.
- Product and docs locks, Compose projects, services, state, and rollback
  ledgers remain separate.

## Architecture Challenge

Proposal: mirror the product delivery pattern with
`CI -> Build docs image -> Deploy docs`, while adding explicit evidence checks
for the public-docs CI job and docs publish job.

Strongest counterargument: build and deploy in one workflow directly after CI.
That would propagate the source SHA without another `workflow_run` metadata
boundary and would use one fewer privileged workflow transition.

Repository evidence: the existing docs release already separates package-write
authority from production SSH authority, produces a reusable immutable image,
and supports manual redeploy/rollback. Combining stages would make those
operational actions rebuild unnecessarily and give one workflow both
publication and deployment responsibilities. GitHub permits this two-level
downstream chain below its three-level `workflow_run` limit.

Final choice: retain two workflows. The build run name carries the verified
commit; the deploy planner verifies the event origin and triggering publish job;
and all release evidence is rechecked before the production environment is
entered.

Rejected alternative: trigger on every `push` path and deploy without waiting
for aggregate CI. It is faster but can publish a commit before its repository
gate is known green.

Independent challenge verdict: accepted after four corrections. The challenger
required explicit same-repository push guards, excluded manual image builds from
automatic deployment, rejected `origin/main` HEAD as the stale criterion because
later non-docs commits would lose a valid docs release, and removed dependence
on workflow-dispatch `headSha` when validating manual image builds. The final
least-privilege pass also moved registry permissions off both planning jobs.

## Success Criteria

- A merged public-docs change builds and deploys without manual dispatch.
- A green non-docs commit publishes and deploys no docs image.
- An older automatic build cannot replace a newer published docs image.
- Manual exact-commit build, redeploy, and rollback remain available.
- Actionlint, repository docs checks, selected CI, CodeRabbit review, and live
  EN/VI route verification pass.
