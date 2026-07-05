---
name: orgmemory-verify-api-symbol
description: Verify unfamiliar Spring Boot 4, Spring Modulith 2, Spring AI 2, JPA, Gradle, React 19, Vite 8, Tailwind 4, or TypeScript symbols before using them in OrgMemory. Use when adding imports, annotations, starters, configuration keys, library APIs, or UI library calls that are not already present in this repo.
---

# OrgMemory Verify API Symbol

Before typing a symbol that is not already used in this repository, verify it.
This project uses recent major versions where older examples can be wrong.

## Workflow

1. Search this repo first with `rg`.
2. If not found, check the official docs or current package metadata.
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

## Local Ground Truth

- Backend package root: `com.orgmemory`
- Domain module: `core/src/main/java/com/orgmemory/core`
- Flyway migrations: `core/src/main/resources/db/migration`
- API app: `apps/api`
- MCP app: `apps/mcp`
- Worker app: `apps/worker`
- Web app: `web`

When in doubt, verify by compiling:

```powershell
.\gradlew.bat --no-daemon compileJava
pnpm -C web typecheck
```
