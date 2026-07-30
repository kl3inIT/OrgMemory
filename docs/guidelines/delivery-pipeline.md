# Delivery Pipeline Guideline

The repository uses path-aware CI followed by two independent automatic release
trains:

1. `CI` verifies the affected backend, web, CLI, public-docs, adapter, and
   deployment surfaces and closes through `CI Gate`.
2. A successful `main` CI run may trigger the immutable six-image product build
   or the independent docs image build. Each train proves that its affected
   verification/build job ran; skipped surfaces are release no-ops.
3. Successful immutable image publication triggers the corresponding protected
   production deployment.
4. Older automatic runs cannot overwrite a newer published release.
5. Manual exact-commit dispatch remains available for a verified redeploy or
   rollback. It never weakens commit ancestry, successful-build, known-host,
   ephemeral-registry-credential, health, smoke, or rollback checks.

Keep product and docs concurrency, Compose projects, state, health gates, and
rollback ledgers separate. Identify every artifact by immutable commit SHA and
preserve the last verified release before mutation.

Do not make a green build depend on long-running `bootRun`. Production profiles
use bounded pools, graceful shutdown, structured logs, and prompt logging off.
