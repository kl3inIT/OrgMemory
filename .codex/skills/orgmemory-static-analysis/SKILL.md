---
name: orgmemory-static-analysis
description: Run OrgMemory's static verification gates after changing backend, database, config, or web code. Use before claiming a task is done, especially after editing Java, SQL migrations, YAML, Gradle, TypeScript, React, or Tailwind files.
---

# OrgMemory Static Analysis

Use this skill as the Gate 1 checklist for OrgMemory changes.

## Backend Checks

Run the fastest relevant checks first:

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat :core:test
```

For a completion-grade check, run:

```powershell
.\gradlew.bat --no-daemon clean test
```

## Web Checks

```powershell
pnpm -C web typecheck
pnpm -C web build
```

## Migration Checks

For every JPA entity or column change:

1. Confirm there is a matching Flyway migration.
2. Keep `ddl-auto: validate`.
3. Boot the API or run context tests so schema validation actually runs.

## Report Format

In the final answer, state:

- which files changed,
- which checks passed,
- which checks were not run and why,
- any remaining risk.

Never report "done" from compilation alone if the change affects runtime wiring,
YAML, migrations, or UI rendering.
