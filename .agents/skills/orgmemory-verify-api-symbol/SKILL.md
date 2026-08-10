---
name: orgmemory-verify-api-symbol
description: Verify unfamiliar Spring Boot 4, Spring Modulith 2, Spring AI 2, JPA, Flyway, Gradle, React 19, Vite 8, Tailwind 4, shadcn/ui, Next.js, Fumadocs, or TypeScript symbols before using them in OrgMemory. Use before adding imports, annotations, starters, configuration keys, library APIs, npm exports, or UI library calls not already present in this repo. Primary check is Context7/current official docs; floor is repo grep or dependency types.
---

# OrgMemory Verify API Symbol

Before typing a symbol that is not already used in this repository, verify it.
This project uses recent major versions where older examples can be wrong.

## Workflow

1. Search this repo first with `rg`.
2. If not found, use Context7/current official docs before typing the symbol.
3. Prefer an existing local pattern over a new abstraction.
4. Do not use deprecated symbols. A deprecation warning is a blocker.
5. Record the verified import/config/API in the final note when it affects the change.

## High-Risk Areas

- Spring Boot 4 package moves and starter names.
- Spring Modulith annotations and test APIs.
- Spring AI 2 configuration keys, MCP server settings, ChatClient APIs, and vector store APIs.
- JPA mappings that must match Flyway exactly.
- Tailwind 4 and Vite 8 plugin/config syntax.
- React 19 and TypeScript 6 typing behavior.
- Airbyte/abctl deployment commands and connector behavior.
- AI Elements component exports.
- Next.js and Fumadocs APIs, MDX configuration, and content conventions in
  `apps/docs`.

## Local Ground Truth

- Backend package root: `com.orgmemory`
- Domain module: `core/src/main/java/com/orgmemory/core`
- Flyway migrations: `core/src/main/resources/db/migration`
- API app: `apps/api`
- MCP app: `apps/mcp`
- Worker app: `apps/worker`
- Web app: `apps/web`
- Documentation app: `apps/docs`
- CLI app: `apps/cli`

When in doubt, verify by compiling:

```bash
./gradlew --no-daemon compileJava
corepack pnpm --filter @orgmemory/web typecheck
```

On native Windows use `gradlew.bat --no-daemon compileJava`; the Corepack
command is unchanged.

## Known Stack Traps

- Boot 4 web MVC starter is `spring-boot-starter-webmvc`, not old Boot 3 habits.
- Boot 4 split test starters; verify the exact starter before adding one.
- `@EntityScan` moved in Boot 4; verify the package before importing.
- Testcontainers 2 uses package-specific containers such as
  `org.testcontainers.postgresql.PostgreSQLContainer`; avoid deprecated generic
  imports.
- Tailwind v4 in this repo uses `@import "tailwindcss"` and the Vite plugin; do
  not add Tailwind v3 config by habit.
- `apps/docs` pins patched Fumadocs packages (`patchedDependencies` in
  `pnpm-workspace.yaml`); verify against the installed patched version, not
  upstream's latest docs.
- Airbyte is external data movement into staging; do not import Airbyte concepts
  into OrgMemory domain tables directly.

For per-file static checks after code is written, use `orgmemory-static-analysis`.
