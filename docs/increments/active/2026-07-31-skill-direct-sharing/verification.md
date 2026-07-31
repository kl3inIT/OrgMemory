# Direct Skill sharing verification

## Local outcome

The implemented default Skill journey is:

```text
validated package -> Draft -> choose version -> immutable Release
                                      |
                                      +-> publication mode DIRECT
```

The same transaction creates the immutable Revision, Release, package-reference
chain, initial availability event, and dedicated audit evidence. An active
review blocks the direct command. The reviewed API remains intact and every
non-Skill profile still requires it.

The browser shows one `Publish Skill` action to an authorized owner-class actor,
hides an empty review journey, and moves to Release history after publication.
Governance and install views disclose `DIRECT` versus `REVIEWED`; the direct
confirmation explicitly says structural validation is not independent content
review.

## Verification evidence

- Independent architecture challenge: accepted with the dedicated
  `can_publish_skill` relation; `can_edit` was rejected as too broad. See
  `challenge-verdict.md`.
- OpenFGA CLI `0.7.19`:
  - `fga model validate --file src/main/openfga/model.fga` — valid.
  - `fga model test --tests store.fga.yaml` — 9/9 tests, 75/75 checks, and
    31/31 ListObjects assertions passed.
- Backend:
  - `.\gradlew.bat --no-daemon :core:check :apps:api:check` — passed.
  - `.\gradlew.bat --no-daemon clean` followed by
    `.\gradlew.bat --no-daemon test` — passed from a clean build tree.
  - Focused Postgres integration tests proved direct package pinning, durable
    publication provenance and audit context, active-review conflict, and
    non-Skill rejection.
  - The JetBrains inspection connector was unavailable in this session, so the
    documented Gradle compiler/check/test fallback was used.
- Contract and web, under Node `24.15.0`:
  - live OpenAPI contract regeneration and `OpenApiContractTests` — passed.
  - `pnpm --filter @orgmemory/web typecheck` — passed.
  - `pnpm --filter @orgmemory/web test:unit` — 8 files and 30 tests passed.
  - `pnpm --filter @orgmemory/web lint` — passed.
  - `pnpm --filter @orgmemory/web build` — passed.
  - Playwright — 12/12 browser tests passed, including the direct Skill
    publication journey and the disclosed install contract.
- Public docs:
  - `pnpm --filter @orgmemory/docs check` — OpenAPI, lint, types, content,
    manifest, publication, routes, and links passed.

## Delivery evidence

Pending PR, CodeRabbit, merge, automatic deployment, migration observation, and
live browser verification.
