# OrgMemory Architecture

## System Shape

OrgMemory is a Spring + React monorepo with one core domain and multiple
delivery apps.

```text
web
  Vite React UI
  TanStack Router/Query
  shadcn/ui primitives
  AI Elements-compatible chat
        |
        v
apps/api
  REST API
  Spring AI chat/enrichment
  Flyway migration owner
        |
        v
core
  Spring Modulith domain modules
  JPA entities/repositories/services
        |
        v
PostgreSQL + pgvector

apps/mcp
  Spring AI MCP server scaffold
  shares core model, Flyway disabled

apps/worker
  future scheduled ingestion/enrichment jobs
  shares core model, Flyway disabled

Airbyte, external to OrgMemory
  enterprise source connectors
  writes to staging/object storage only
```

## Backend Modules

`core/` contains the business model:

- organization
  - `Organization`
  - `Department`
  - `AppUser`
  - `UserRole`
- capability
  - `CapabilityAsset`
  - `AssetVersion`
  - `AssetUsageEvent`
  - `AssetApprovalEvent`
  - `AssetStatus`
  - `AssetType`
  - `AssetVisibility`
  - `RiskLevel`
  - `CapabilityAssetService`

`apps/api/` exposes:

- `GET /api/health`
- `GET /api/organization/context`
- `GET /api/assets`
- `POST /api/assets`
- `GET /api/assets/{assetId}`
- `GET /api/assets/{assetId}/versions`
- `PATCH /api/assets/{assetId}/submit-review`
- `PATCH /api/assets/{assetId}/approve`
- `PATCH /api/assets/{assetId}/reject`
- `PATCH /api/assets/{assetId}/deprecate`
- `PATCH /api/assets/{assetId}/backup-owner`
- `POST /api/assets/{assetId}/usage`
- `POST /api/ai/assets/normalize`
- `POST /api/ai/chat`
- `GET /api/graph`

## Data Model

Current persisted prototype tables:

- `organizations`
- `departments`
- `app_users`
- `capability_assets`
- `asset_versions`
- `asset_usage_events`
- `asset_approval_events`
- `tags`
- `asset_tags`
- `asset_embeddings`

The prototype already has the core Capability Asset registry lifecycle. The next
enterprise pilot phase should add source, knowledge, identity, and governance
records:

- `raw_source_object`
- `normalized_record`
- `knowledge_asset`
- `capability_candidate`
- `source_acl_snapshot`
- richer `audit_event`
- auth/session or external identity mapping tables as needed

The important distinction is:

```text
raw_source_object      source-shaped imported data, may be noise
normalized_record      parsed/cleaned intermediate record
knowledge_asset        trusted enterprise knowledge worth retaining
capability_candidate   possible reusable AI workflow/prompt/agent
capability_asset       approved reusable AI capability
```

## Frontend Routes

Current routes:

- `/` dashboard
- `/registry` capability registry
- `/assets/$assetId` asset detail
- `/create` create asset
- `/review` review queue
- `/transfer` onboarding/offboarding knowledge transfer
- `/ask` Ask Memory
- `/graph` knowledge graph
- `/analytics` analytics
- `/settings` settings

Shared UI is in `web/src/components/ui`, following the shadcn local-component
pattern. Product-specific logic is under `web/src/features`.

## AI Flow

AI enrichment:

```text
raw prompt/workflow text
        |
        v
POST /api/ai/assets/normalize
        |
        v
Spring AI ChatClient if enabled
        |
        v
strict JSON draft metadata
        |
        v
local fallback when model unavailable
```

Ask Memory:

```text
user message
        |
        v
local registry ranker checks live assets
        |
        v
Spring AI streams answer if enabled
        |
        v
SSE frames compatible with AI Elements UI message stream
```

The local fallback is intentional: a product walkthrough can continue even when
the external model call fails. Production behavior must still report provider
failures clearly and avoid silently masking critical workflow failures.

## Knowledge Graph

The current graph is a visualization backed by relational data, not a separate
graph database. It derives nodes and edges from:

- assets
- asset types
- departments
- owners
- backup owners
- tags
- shared business process

Use PostgreSQL relations first. A dedicated graph database is a later phase only
if the relational model no longer supports required traversal/search behavior.

## Ingestion Architecture

Airbyte should be the preferred enterprise data-movement layer for pilot
ingestion when a maintained connector exists. Ingestion is painful because of
OAuth, pagination, rate limits, incremental sync, schema drift, retries, and
connector monitoring. OrgMemory should not rebuild a broad connector catalog.

Airbyte is still not the OrgMemory product layer. It should feed a staging
schema or object storage:

```text
Slack / Teams / Drive / SharePoint / GitHub / CRM / n8n / Dify
        |
        v
Airbyte
        |
        v
PostgreSQL staging + object storage
        |
        v
OrgMemory ingestion worker
        |
        v
RawSourceObject -> NormalizedRecord -> KnowledgeAsset -> CapabilityCandidate -> Review -> CapabilityAsset
```

Airbyte solves source access and data movement. OrgMemory solves:

- source scope approval
- ACL snapshot and identity mapping
- PII/sensitive-data detection
- parsing, chunking, cleaning, and deduplication
- Knowledge Asset quality
- Capability Candidate detection
- human review and approval
- audit events
- citations
- permission-aware retrieval
- capability ownership, reuse, and transfer

Not every raw source becomes a Knowledge Asset. Not every Knowledge Asset becomes
a Capability Asset.

Do not write Airbyte output directly into the main memory/vector tables. Keep a
staging boundary so OrgMemory can validate permissions, quality, provenance, and
retention before data becomes trusted memory.

## Airflow Position

Airflow is not the default first pilot dependency. Add it only when the ingestion
pipeline needs DAG-level orchestration that Airbyte plus `apps/worker` cannot
reasonably handle:

- complex multi-step dependencies
- large backfills/reprocessing
- data quality gates across many stages
- SLA monitoring per pipeline
- scheduled ML/embedding workflows with many dependent tasks

Until that point, use Airbyte for connector sync and OrgMemory Worker for domain
normalization, enrichment, embeddings, and governance jobs.

## Configuration

Database defaults:

```properties
ORGMEMORY_DB_URL=jdbc:postgresql://localhost:55432/orgmemory
ORGMEMORY_DB_USER=orgmemory
ORGMEMORY_DB_PASSWORD=orgmemory
```

AI defaults:

```properties
ORGMEMORY_AI_MODEL_CHAT=none
OPENAI_API_KEY=
ORGMEMORY_OPENAI_MODEL=gpt-5.5
```

API runs Flyway. MCP and worker apps use `ddl-auto=validate` and keep Flyway
disabled.

## Engineering Rules

- Keep JPA schema changes paired with Flyway migrations.
- Keep `ddl-auto=validate`.
- Put core behavior in `core/`, not in delivery apps.
- Keep API/web payloads explicit and typed.
- Do not expose draft/private assets through agent surfaces without permission
  controls.
- Use shadcn primitives and maintained libraries before custom UI components.
- Permission checks must run before retrieval, vector search, AI answer
  generation, MCP tool responses, and export.
