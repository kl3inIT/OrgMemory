# Product Release Guideline

OrgMemory has one semantic product version even though its deployables span
Gradle and pnpm. `release/product.json` is that version's source of truth;
`release/CHANGELOG.md` is generated history. Executable artifacts remain the
immutable SHA-addressed images and manifests described in the delivery
pipeline guideline.

## Add A Release Entry

Every pull request with user, administrator, operator, API, compatibility, or
security impact adds one Markdown file under `.tegami/`:

```md
---
packages:
  orgmemory: patch
subject: Short release subject
---

## Fixes

Describe the externally meaningful result.
```

Use `patch` for compatible fixes and small improvements, `minor` for compatible
capabilities, and `major` for breaking contracts or required migration. Allowed
section names are Breaking changes, Features, Fixes, Improvements,
Documentation, Operations, and Security.

The repository is public. A release entry may contain normal technical detail,
but never credentials, tokens, private keys, customer data, private access
details, managed-secret values, or unredacted sensitive incident and
vulnerability detail. Gitleaks and structural CI checks are merge gates, not a
way to retract information already pushed publicly. Review and scan locally
before pushing.

A change may omit an entry only when it has no product or operator impact, for
example an internal comment correction. State `skip-release` and the reason in
the pull-request description; it is a review convention, not a bypass label.

## Automated Lifecycle

1. Pull-request CI validates the entry, scans repository history for secrets,
   and posts a read-only Tegami preview through a separately permissioned
   comment workflow.
2. After the change reaches green `main`, release automation waits for the
   product and docs image workflows at that exact commit.
3. It selects the latest complete manifests applicable to that commit and
   writes one consolidated `release/artifacts.json`.
4. `tegami ci` opens or updates `tegami/version-packages`. Its diff is limited
   to consumed entries, publish lock, product version, changelog, its generated
   public docs fragment, and the artifact manifest.
5. CodeRabbit and required CI review that Version Packages pull request like
   any other pull request.
6. After it merges and the exact commit is green, Tegami validates the manifest,
   creates `v<version>`, creates the GitHub Release, attaches
   `artifacts.json`, and verifies the remote tag target and Release.

Release-only commits do not rebuild or deploy images. Their manifest carries
forward the already verified component digests and source SHAs. Deployment
continues to consume image manifests, not semantic tags.

## Local Commands

```powershell
pnpm release pr preview --number <PR_NUMBER>
pnpm release:check
```

Do not run `pnpm release ci` locally against the real remote. It is the writable
main-branch automation entrypoint. Do not run Tegami `init-agent`; OrgMemory's
durable instructions are maintained here and linked from `CLAUDE.md`.

## Recovery

The non-cancelling release workflow and `.tegami/publish-lock.yaml` serialize
retries. Before retrying, inspect the lock, `release/artifacts.json`, remote tag,
and GitHub Release.

- Provider or manifest failure before a tag: repair evidence or code and rerun;
  do not create a tag manually.
- Tag creation/push failure: rerun. If the remote tag exists, its peeled target
  must equal the Version Packages merge SHA.
- Tag exists at a different SHA: stop. Do not move or delete it automatically;
  obtain owner approval for the explicit GitHub recovery.
- Tag succeeded but GitHub Release or attachment failed: retain the lock and
  rerun. Creation and artifact upload are idempotent for the same tag.
- GitHub Release already exists: verify its tag and remote target, then rerun to
  restore/verify the artifact attachment.
- A genuinely pending failed lock blocks a newer release. Recover or explicitly
  abandon it before accepting another Version Packages pull request.

After recovery, verify the remote tag SHA, Release tag, attached artifact
manifest, every recorded digest, and a second idempotent workflow run.
