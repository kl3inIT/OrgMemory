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

- [PR #162](https://github.com/kl3inIT/OrgMemory/pull/162) merged as
  `5099c48afe9c60dea632c4a415b28f4dfc3bb179`.
- The final pull-request and `main` CI runs passed Backend Java 25, Web and
  public docs on Node 24, CLI, OpenFGA, PostgreSQL GraphRAG, documentation
  structure, Playwright, and their aggregate gate.
- CodeRabbit completed a real 31-file review. Its review-state race finding was
  fixed by serializing direct publication and review submission on the same
  Asset row; its migration finding was fixed with `text`, `NOT VALID`, and
  explicit constraint validation. The proposed second Skill Draft action was
  withdrawn because the challenged product decision intentionally keeps one
  default `Publish Skill` action while retaining reviewed publication in the
  API.
- Immutable product and docs images were built for the merge SHA. The first
  automatic checkout found previously applied observability files in the
  deployment worktree. Every conflicting file was hash-verified against the
  target commit before the index was reconciled; the untracked production
  `observability.env` was preserved. Exact-SHA product and docs redeployments
  then passed their workflow smoke gates.
- Production release stamps and the running API, Worker, Web, and Docs image
  references all report the merge SHA. `https://om.kl3in.tech/api/health` and
  `https://docs.kl3in.tech/healthz` return HTTP 200.
- Production Flyway history records V15 as successful. The
  `asset_releases.publication_mode` column is `text NOT NULL`, its allowed-value
  constraint is validated, and the configured OpenFGA model hash matches the
  deployed model containing `can_publish_skill`.
- The public Asset API reference contains the direct Skill release endpoint and
  `publicationMode` contract. The authenticated browser journey itself was
  proven by the 12/12 Playwright suite without creating synthetic production
  Asset data.
