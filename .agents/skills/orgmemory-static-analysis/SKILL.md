---
name: orgmemory-static-analysis
description: Gate-1 static checks for OrgMemory. Use JetBrains IDE inspection only for edited backend Java files. Use Gradle for backend verification and Oxlint, TypeScript, build, and browser tests for frontend verification.
---

# OrgMemory Static Analysis

Gate 1 means every file you created or edited gets the appropriate static check
before you claim the task is done. JetBrains IDE inspection is reserved for
backend Java. Frontend files use the frontend-native toolchain.

## 1. JetBrains IDE Inspection For Backend Java

If a JetBrains MCP inspection is attached, run it on every touched backend
`.java` file before the Gradle commands — it surfaces unresolved imports,
missing beans, invalid JPQL, and broken JPA mapping hints (often as warnings)
earlier than a test run, and all of those are blockers. In practice most
sessions go straight to the Gradle gates; treat inspection as an extra early
signal, never as a reason to skip Gradle.

Rules:

- Resolve the repository root dynamically (`git rev-parse --show-toplevel`) and
  pass that absolute path as `projectPath` on every call, including when several
  projects are open. Never hard-code a drive or checkout path.
- Include warnings, not only errors.
- Never trust an empty result unless the call clearly inspected the intended
  file in the OrgMemory project. Wrong-project targeting or "URI is not
  registered" noise means inspection is unavailable — use the fallback gates.
- Do not run it on `.ts`, `.tsx`, CSS, config, workflow YAML, or other
  frontend files.

## 2. Backend Fallback

Run from the repo root:

```bash
./gradlew --no-daemon compileJava
./gradlew --no-daemon :core:test
```

On native Windows use `gradlew.bat --no-daemon compileJava` and
`gradlew.bat --no-daemon :core:test` instead.

For the full backend gate:

```bash
./gradlew --no-daemon clean test
```

On native Windows use `gradlew.bat --no-daemon clean test`.

Use `:core:test` whenever you touch `core/src/main/java/com/orgmemory/core`
because the Modulith verification test protects module boundaries.

## 3. Web Static Analysis And Build

```console
corepack pnpm --filter @orgmemory/web lint
corepack pnpm --filter @orgmemory/web typecheck
corepack pnpm --filter @orgmemory/web build
```

Oxlint and TypeScript are the static-analysis authority for web code.
`corepack pnpm --filter @orgmemory/web typecheck` is required for `.ts` and `.tsx`; Vite alone does not
type-check the app. Use a real browser flow when behavior or layout changes.

For `apps/docs` changes:

```console
corepack pnpm --filter @orgmemory/docs check
corepack pnpm --filter @orgmemory/docs build
```

If backend REST endpoints or DTOs changed, refresh the committed OpenAPI
contract and regenerate the web client before type-checking; the procedure is
in `orgmemory-create-test`.

## 4. Migration And Persistence Checks

For every JPA entity, enum, repository query, or column change:

1. Confirm there is a matching Flyway migration under
   `core/src/main/resources/db/migration`.
2. Keep `ddl-auto: validate`.
3. Run a test or boot path that actually validates schema against PostgreSQL.
4. Prefer Testcontainers PostgreSQL/pgvector over H2.

## 5. Authorization Model Checks

For OpenFGA authorization-model or tuple-fixture changes under
`integrations/authorization-openfga`, run the model test gate exactly as
listed in `docs/conventions.md` (Verification) before handoff.

## 6. Mechanical Floor When No IDE Inspection

Use these when JetBrains inspection is unavailable and the change touches source,
config, or migration files:

Use `python3` on POSIX (or `py -3` on Windows) from the repository root:

```python
from pathlib import Path
import re

roots = [Path("core"), Path("apps")]
defects = []
for root in roots:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file() or "src" not in path.parts:
            continue
        if path.suffix == ".java" and not any(
            line.startswith("package ")
            for line in path.read_text(encoding="utf-8").splitlines()
        ):
            defects.append(f"missing package: {path}")
        if path.suffix in {".java", ".yml", ".yaml", ".sql"} and path.stat().st_size == 0:
            defects.append(f"zero-byte file: {path}")

migration_root = Path("core/src/main/resources/db/migration")
if migration_root.exists():
    for path in migration_root.glob("*.sql"):
        if not re.fullmatch(r"V\d+__.+\.sql", path.name):
            defects.append(f"misnamed migration: {path}")

for defect in defects:
    print(defect)
raise SystemExit(1 if defects else 0)
```

Run it from a short temporary file so the command is identical across shells.
For changed Java mappings, separately search changed files for
`@Entity`, `@Table`, and `@Column`, then reconcile every hit with Flyway.

Any missing package line, zero-byte source/config/migration, or misnamed Flyway
migration is a defect. Entity/table/column hits are not automatically failures,
but every changed mapping must be reconciled with Flyway.

## 7. Final Report Format

In the final answer, state:

- files changed,
- whether JetBrains MCP inspection was used for changed backend Java or was not
  applicable,
- which Gradle/pnpm/mechanical checks passed,
- which checks were not run and why,
- remaining risk.

Never report "done" from compilation alone if the change affects runtime wiring,
YAML, migrations, JPA mappings, permissions, AI provider config, or UI rendering.
