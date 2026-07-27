# GitHub authorization connector plan

## 1. Provider shell — complete

- Add redacted GitHub App credential parsing.
- Add RS256 app JWT and cached installation-token exchange.
- Add a bounded, retrying, Link-paginated GitHub REST client.
- Add focused credential, authentication, pagination, and retry tests.

## 2. Connector adapter — complete

- Add GitHub source profile and auto-configuration.
- Add credential probe and repository scope browser.
- Add repository settings and admissibility validation.
- Emit stable user/repository-reader identities, independent memberships,
  issue/PR content, repository-group ACLs, and component cursors.
- Add full-crawl, permission-only, truncation, mapping, and failure tests.

## 3. Vertical authorization proof — complete

- Ingest an initial GitHub issue with two effective readers.
- Reconcile a second membership-only batch after one reader is removed.
- Assert the removed AppUser is denied while ACL head, source revision, chunks,
  and embeddings remain unchanged.

## 4. Administration and contracts — complete

- Register GitHub in the product connector catalog and credential form.
- Document required GitHub App permissions and the effective-reader mapping.
- Consolidate architecture/spec/test facts after behavior exists.
- Move this increment to `completed`.

## 5. Gates and merge loop — complete

- [x] Focused connector tests.
- [x] GitHub-shaped PostgreSQL ingestion/retrieval convergence test.
- [x] Web lint, typecheck, unit tests, and production build.
- [x] Terminating Gradle `clean test` and `git diff --check`.
- [x] JetBrains inspection attempted for every edited Java file. Unavailable
      because the attached IDE had a different MSS301 repository open; the
      mechanical compile/test fallback passed.
- [x] Branch prepared for the end-to-end PR and the owner-directed
      CodeRabbit/required-CI merge loop.
