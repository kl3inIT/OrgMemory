---
name: orgmemory-static-analysis
description: Gate-1 static checks for OrgMemory after changing backend, database, config, or web code. PRIMARY when a JetBrains IDE MCP inspection such as get_file_problems is connected: run it per edited file with the absolute projectPath so Spring/JPA/Modulith/YAML/TypeScript warnings are caught before compile or runtime. Fall back to Gradle, pnpm, and mechanical checks when IDE inspection is unavailable.
---

# OrgMemory Static Analysis

Gate 1 means every file you created or edited gets a static check before you
claim the task is done. For this repo, the strongest static gate is JetBrains IDE
inspection when the MCP is connected. It catches Spring, JPA, YAML, Flyway, and
TypeScript defects that plain compilation can miss.

## 1. JetBrains IDE Inspection, Primary When Connected

If a JetBrains MCP inspection is available, run it per edited file before the
fallback commands.

Rules:

- Always pass `projectPath` as the absolute repo root: `D:\OrgMemory`.
- Run it on every touched `.java`, `.yml`, `.yaml`, Flyway `.sql`, `.ts`, and
  `.tsx` file, not just a sample.
- Include warnings, not only errors. Spring/JPA unresolved references often
  appear as warnings.
- Treat unresolved imports, unresolved Spring properties, missing beans, invalid
  JPQL, invalid JPA mapping hints, duplicate YAML keys, and TypeScript unresolved
  symbols as blockers.
- Never trust an empty result unless the call clearly inspected the intended
  file in the OrgMemory project. If the IDE targets the wrong project or reports
  generic "URI is not registered" noise, treat JetBrains inspection as
  unavailable and use the fallback gates.

If more than one project is open and the MCP reports that no exact project is
specified, choose the project whose path is exactly `D:\OrgMemory` and pass that
path on every later JetBrains MCP call.

## 2. Backend Fallback

Run from the repo root:

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat :core:test
```

For completion-grade backend verification:

```powershell
.\gradlew.bat --no-daemon clean test
```

Use `:core:test` whenever you touch `core/src/main/java/com/orgmemory/core`
because the Modulith verification test protects module boundaries.

## 3. Web Fallback

```powershell
pnpm -C web typecheck
pnpm -C web build
```

`pnpm -C web typecheck` is required for `.ts` and `.tsx`; Vite alone does not
type-check the app.

## 4. Migration And Persistence Checks

For every JPA entity, enum, repository query, or column change:

1. Confirm there is a matching Flyway migration under
   `core/src/main/resources/db/migration`.
2. Keep `ddl-auto: validate`.
3. Run a test or boot path that actually validates schema against PostgreSQL.
4. Prefer Testcontainers PostgreSQL/pgvector over H2.

## 5. Mechanical Floor When No IDE Inspection

Use these when JetBrains inspection is unavailable and the change touches source,
config, or migration files:

```powershell
Get-ChildItem core,apps -Recurse -Filter *.java |
  Where-Object { $_.FullName -like '*\src\*' } |
  ForEach-Object {
    if (-not (Select-String -LiteralPath $_.FullName -Pattern '^package ' -Quiet)) {
      $_.FullName
    }
  }

Get-ChildItem core,apps -Recurse -Include *.java,*.yml,*.yaml,*.sql |
  Where-Object { $_.FullName -like '*\src\*' -and $_.Length -eq 0 } |
  Select-Object -ExpandProperty FullName

Get-ChildItem core\src\main\resources\db\migration -Filter *.sql |
  Where-Object { $_.Name -notmatch '^V\d+__.+\.sql$' } |
  Select-Object -ExpandProperty FullName

Select-String -Path core\src\main\java\**\*.java -Pattern '@Entity|@Table|@Column'
```

Any missing package line, zero-byte source/config/migration, or misnamed Flyway
migration is a defect. Entity/table/column hits are not automatically failures,
but every changed mapping must be reconciled with Flyway.

## 6. Final Report Format

In the final answer, state:

- files changed,
- whether JetBrains MCP inspection was used or unavailable,
- which Gradle/pnpm/mechanical checks passed,
- which checks were not run and why,
- remaining risk.

Never report "done" from compilation alone if the change affects runtime wiring,
YAML, migrations, JPA mappings, permissions, AI provider config, or UI rendering.
