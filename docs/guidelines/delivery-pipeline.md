# Delivery Pipeline Guideline

The repository has no CI workflow yet. Introduce delivery in this order:

1. Add a repository script that checks Markdown links/source duplication,
   migration naming, generated contracts, and required test/spec symmetry.
2. Bind it to a Gradle verification task and web scripts so it runs locally.
3. Add path-aware CI: docs-only runs doc checks; core/root changes fan out to
   every JVM app; web runs lint/typecheck/unit/build; critical E2E runs once.
4. Build deployable images in parallel, identify artifacts by immutable commit
   SHA, and require migration/backup/rollback checks before deployment.
5. Add dependency automation only with the same compile/test/build gates.

Do not make a green build depend on long-running `bootRun`. Production profiles
use bounded pools, graceful shutdown, structured logs, and prompt logging off.
