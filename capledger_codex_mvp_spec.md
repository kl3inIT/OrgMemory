# CapLedger MVP — Codex Implementation Brief

## 0. Working name

**Project name:** CapLedger  
**Tagline:** The system of record for enterprise AI capabilities.  
**Category:** Organizational AI Memory / AI Capability Management  

CapLedger turns individual AI know-how into reusable, governed, transferable organizational assets.

---

## 1. Product summary

Organizations are adopting AI through prompts, workflows, automations, and agents created by individual employees. These assets often live in private ChatGPT/Claude histories, personal notes, Slack threads, or undocumented workflows. When employees move teams or leave, the organization loses valuable AI know-how.

CapLedger is an **AI Capability Registry**. It stores AI workflows, prompts, agent configurations, and operational know-how as structured **AI Capability Assets** with ownership, versioning, approval status, usage tracking, and handover support.

This MVP is not a generic wiki, not an agent runtime, and not passive employee surveillance. It is a registry and workflow layer that proves whether people will submit, discover, reuse, and transfer AI capabilities.

---

## 2. MVP thesis

The MVP should validate this loop:

```text
Employee submits an AI workflow or prompt
        ↓
AI enriches it into a structured Capability Asset
        ↓
Team lead reviews and approves it
        ↓
Other employees search and reuse it
        ↓
Usage is tracked
        ↓
Onboarding and offboarding flows use the asset graph
```

If users do not submit assets or reuse assets created by others, the product thesis is weak. The MVP should focus on that behavior before building browser extensions, passive capture, MCP integrations, or a full knowledge graph.

---

## 3. MVP scope

### In scope

1. Multi-organization, multi-department basic data model.
2. AI Capability Asset creation form.
3. AI enrichment from raw prompt/workflow text.
4. Asset versioning.
5. Approval workflow: Draft → In Review → Approved → Deprecated.
6. Search and discovery: keyword + semantic search.
7. Usage tracking: viewed, copied, used, favorited.
8. Onboarding pack by role/department.
9. Offboarding report by employee.
10. Basic dashboard.
11. Demo seed data for Sales, Marketing, Customer Success, and Engineering.

### Out of scope for MVP

1. Passive browser capture from ChatGPT/Claude.
2. HRIS integration.
3. Slack/Google Drive/Notion ingestion.
4. MCP server.
5. Neo4j or dedicated graph database.
6. Enterprise SSO/SAML/SCIM.
7. Complex RBAC.
8. Billing.
9. Full agent orchestration.
10. Marketplace.

---

## 4. Recommended MVP stack

Use a simple, Codex-friendly stack:

```text
Frontend: Next.js + TypeScript + Tailwind CSS
Backend: FastAPI + Python
Database: PostgreSQL + pgvector
ORM/Migrations: SQLAlchemy + Alembic
AI layer: provider-neutral service wrapper
Queue/background jobs: simple FastAPI BackgroundTasks first; Celery/RQ later
Auth MVP: simple email/password or seeded demo users + JWT
Deployment MVP: Docker Compose
```

Why this stack for MVP:

- FastAPI is quick for AI/ETL workflows.
- Python is convenient for enrichment, embeddings, parsing, and future LangGraph agents.
- PostgreSQL is enough for structured records, version history, approvals, and usage events.
- pgvector is enough for first semantic search without operating a separate vector database.
- Next.js gives a clean SaaS UI quickly.

If the team strongly prefers Java, Spring Boot can replace FastAPI later as the system-of-record backend. For the MVP, prioritize speed and behavior validation.

---

## 5. Core concept: AI Capability Asset

An **AI Capability Asset** is not just a prompt. It is a reusable unit of organizational AI capability.

Minimum asset schema:

```json
{
  "id": "asset_123",
  "title": "Post-demo Follow-up Email",
  "summary": "Generates a concise follow-up email after a sales demo.",
  "department": "Sales",
  "business_process": "Demo follow-up",
  "use_case": "Sales email generation",
  "owner_user_id": "user_1",
  "backup_owner_user_id": "user_2",
  "status": "APPROVED",
  "visibility": "TEAM",
  "current_version": "1.2",
  "tags": ["sales", "email", "demo", "follow-up"],
  "required_inputs": ["customer_name", "pain_points", "interested_features", "next_step"],
  "output_format": "email",
  "allowed_tools": ["ChatGPT", "Claude"],
  "usage_count": 82,
  "created_at": "2026-01-01T00:00:00Z",
  "updated_at": "2026-01-01T00:00:00Z"
}
```

The asset should be readable by humans and structured enough for future agents to retrieve and execute.

---

## 6. User roles

### Employee

- Creates draft assets.
- Searches approved assets.
- Uses/copies approved assets.
- Favorites assets.
- Suggests improvements.

### Team Lead / Manager

- Reviews submitted assets.
- Approves, rejects, or deprecates assets.
- Assigns owner and backup owner.
- Views department dashboard.
- Generates onboarding/offboarding reports.

### Admin

- Manages organizations, departments, users.
- Seeds demo data.
- Views all assets.

MVP can implement simplified role checks.

---

## 7. Database model

### organizations

```text
id UUID primary key
name text not null
created_at timestamptz
updated_at timestamptz
```

### departments

```text
id UUID primary key
organization_id UUID references organizations(id)
name text not null
created_at timestamptz
updated_at timestamptz
```

### users

```text
id UUID primary key
organization_id UUID references organizations(id)
department_id UUID references departments(id)
name text not null
email text unique not null
role text not null -- EMPLOYEE, MANAGER, ADMIN
password_hash text nullable for MVP
created_at timestamptz
updated_at timestamptz
```

### capability_assets

```text
id UUID primary key
organization_id UUID references organizations(id)
department_id UUID references departments(id)
title text not null
summary text
business_process text
use_case text
owner_user_id UUID references users(id)
backup_owner_user_id UUID references users(id)
status text not null -- DRAFT, IN_REVIEW, APPROVED, DEPRECATED
visibility text not null -- PRIVATE, TEAM, ORG
current_version_id UUID nullable
tags jsonb default '[]'
quality_score numeric nullable
risk_level text nullable -- LOW, MEDIUM, HIGH
created_by UUID references users(id)
created_at timestamptz
updated_at timestamptz
```

### asset_versions

```text
id UUID primary key
asset_id UUID references capability_assets(id)
version_number text not null
raw_content text not null
prompt_template text
workflow_steps jsonb default '[]'
required_inputs jsonb default '[]'
output_schema jsonb default '{}'
example_input text
example_output text
change_note text
created_by UUID references users(id)
created_at timestamptz
```

### asset_embeddings

```text
id UUID primary key
asset_id UUID references capability_assets(id)
version_id UUID references asset_versions(id)
content text not null
embedding vector(1536) -- adjust dimension based on embedding model
created_at timestamptz
```

### asset_approval_events

```text
id UUID primary key
asset_id UUID references capability_assets(id)
reviewer_id UUID references users(id)
action text not null -- SUBMIT, APPROVE, REJECT, DEPRECATE
comment text
created_at timestamptz
```

### asset_usage_events

```text
id UUID primary key
asset_id UUID references capability_assets(id)
user_id UUID references users(id)
event_type text not null -- VIEWED, COPIED, USED, FAVORITED, SHARED
metadata jsonb default '{}'
created_at timestamptz
```

### asset_relations

```text
id UUID primary key
organization_id UUID references organizations(id)
source_asset_id UUID references capability_assets(id)
relation_type text not null -- RELATED_TO, REPLACES, SUPPORTS_PROCESS, USES_TOOL
 target_type text not null -- ASSET, PROCESS, TOOL, DOCUMENT, USER
 target_id text not null
created_at timestamptz
```

Note: `asset_relations` is a lightweight graph substitute. Do not add Neo4j in the MVP.

---

## 8. API endpoints

### Auth / demo session

```text
POST /auth/login
GET  /auth/me
POST /auth/logout
```

For MVP, demo users may be seeded and login can be simplified.

### Assets

```text
GET    /assets
POST   /assets
GET    /assets/{asset_id}
PATCH  /assets/{asset_id}
DELETE /assets/{asset_id}
```

Query filters for `GET /assets`:

```text
q
status
department_id
owner_user_id
tag
visibility
sort
```

### Versions

```text
GET  /assets/{asset_id}/versions
POST /assets/{asset_id}/versions
GET  /assets/{asset_id}/versions/{version_id}
```

### AI enrichment

```text
POST /assets/enrich
POST /assets/{asset_id}/enrich
```

Input:

```json
{
  "raw_content": "Paste prompt, workflow, SOP, or agent configuration here.",
  "department_hint": "Sales",
  "tool_hint": "ChatGPT"
}
```

Output:

```json
{
  "suggested_title": "Post-demo Follow-up Email",
  "summary": "Creates a follow-up email after a product demo.",
  "use_case": "Sales follow-up",
  "business_process": "Demo follow-up",
  "required_inputs": ["customer_name", "pain_points", "next_step"],
  "workflow_steps": [
    {"step": 1, "name": "Collect demo context"},
    {"step": 2, "name": "Generate email draft"},
    {"step": 3, "name": "Review for accuracy"}
  ],
  "output_schema": {"type": "email", "fields": ["subject", "body"]},
  "tags": ["sales", "email", "follow-up"],
  "risk_level": "LOW",
  "quality_score": 0.82
}
```

### Approval workflow

```text
POST /assets/{asset_id}/submit
POST /assets/{asset_id}/approve
POST /assets/{asset_id}/reject
POST /assets/{asset_id}/deprecate
```

### Search

```text
GET  /search?q=...
POST /search/semantic
```

Default behavior:

- Search approved assets first.
- Include drafts only for owner/admin.
- Boost latest versions.
- Boost assets with higher usage count.

### Usage

```text
POST /assets/{asset_id}/usage
```

Input:

```json
{
  "event_type": "USED",
  "metadata": {
    "source": "web_app",
    "notes": "User generated output from asset page"
  }
}
```

### Use an asset

```text
POST /assets/{asset_id}/run
```

This does not need to be a full automation engine. It simply fills prompt variables and calls an LLM.

Input:

```json
{
  "inputs": {
    "customer_name": "Acme Corp",
    "pain_points": "Manual reporting takes too long",
    "next_step": "Schedule a technical deep dive"
  }
}
```

Output:

```json
{
  "generated_output": "Subject: Following up on our demo...",
  "used_version_id": "version_123"
}
```

### Onboarding

```text
POST /onboarding/generate
```

Input:

```json
{
  "department_id": "dept_sales",
  "role_title": "Sales Executive"
}
```

Output:

```json
{
  "role_title": "Sales Executive",
  "recommended_assets": [...],
  "first_week_plan": [...],
  "suggested_owners_to_meet": [...]
}
```

### Offboarding

```text
POST /offboarding/report
```

Input:

```json
{
  "user_id": "user_linh"
}
```

Output:

```json
{
  "departing_user": "Linh",
  "owned_assets": [...],
  "assets_without_backup_owner": [...],
  "handover_checklist": [...],
  "recommended_new_owners": [...]
}
```

### Dashboard

```text
GET /dashboard/summary
```

Return:

```json
{
  "total_assets": 42,
  "approved_assets": 25,
  "draft_assets": 12,
  "deprecated_assets": 5,
  "assets_without_backup_owner": 8,
  "top_used_assets": [...],
  "assets_by_department": [...],
  "recent_activity": [...]
}
```

---

## 9. Frontend screens

### 1. Dashboard

Show:

- Total assets.
- Approved assets.
- Draft assets.
- Assets without backup owner.
- Assets by department.
- Top used assets.
- Recent activity.

### 2. Asset list / discovery

Features:

- Search bar.
- Filter by department/status/tool/tag.
- Cards with title, summary, owner, status, version, usage count.
- Default filter: approved assets.

### 3. Create asset

Form sections:

- Basic information.
- Raw prompt/workflow.
- Department and business process.
- Owner and backup owner.
- Visibility.
- Tags.
- Examples.

Important button:

- “Enrich with AI”

### 4. Asset detail

Tabs:

- Overview.
- Prompt / workflow.
- Inputs and outputs.
- Examples.
- Versions.
- Approval history.
- Usage.
- Related assets.

Actions:

- Submit for review.
- Approve/reject.
- Deprecate.
- Use asset.
- Copy prompt.
- Create new version.

### 5. Review queue

For managers/admins:

- Assets waiting for approval.
- AI-generated metadata diff.
- Approve/reject buttons.
- Assign backup owner.

### 6. Onboarding generator

Inputs:

- Department.
- Role title.

Output:

- Recommended asset list.
- First-week learning plan.
- Suggested people to meet.

### 7. Offboarding report

Input:

- Employee.

Output:

- Assets owned by employee.
- Assets with no backup owner.
- Suggested new owners.
- Handover checklist.

---

## 10. AI enrichment behavior

The enrichment service should accept raw content and return structured JSON. It must never directly overwrite human-reviewed fields without review.

### Enrichment tasks

1. Generate clear title.
2. Generate short summary.
3. Identify department and use case.
4. Extract required inputs.
5. Extract expected output format.
6. Convert workflow into steps.
7. Suggest tags.
8. Identify risk level.
9. Suggest owner/backup owner only if signals exist.
10. Find possible duplicates using semantic search.

### Guardrails

- AI-generated metadata must be marked as suggested.
- Human approval is required before an asset becomes approved.
- If confidence is low, ask the user to fill missing information.
- Do not invent business outcomes.
- Do not invent usage metrics.
- Do not expose private assets to unauthorized users.

---

## 11. Semantic search behavior

Implement hybrid search:

1. Keyword match across title, summary, tags, business_process, use_case.
2. Embedding similarity across asset content.
3. Status filter.
4. Permission filter.
5. Usage count boost.
6. Recency boost.

For MVP, implement a basic scoring function in Python. Do not over-optimize.

---

## 12. Demo seed data

Seed one organization: **Acme AI Labs**.

Departments:

- Sales
- Marketing
- Customer Success
- Engineering
- HR

Users:

- Admin: Ava Admin
- Sales Manager: Sarah Sales
- Sales Employee: Linh Nguyen
- Marketing Manager: Maya Marketing
- CS Manager: Chris Support
- Engineer: Evan Eng

Example assets:

1. Post-demo Follow-up Email — Sales — Approved
2. Lead Qualification Summary — Sales — Approved
3. Competitor Research Brief — Marketing — Approved
4. Social Content Repurposing Workflow — Marketing — Draft
5. Customer Feedback Analysis Workflow — Customer Success — Approved
6. Weekly Customer Health Report — Customer Success — In Review
7. Pull Request Review Checklist with AI — Engineering — Approved
8. Incident Postmortem Drafting Workflow — Engineering — Draft
9. New Hire AI Workflow Onboarding Pack — HR — Approved
10. Job Description Improvement Prompt — HR — Deprecated

Make at least two assets owned by Linh Nguyen, with one missing a backup owner, so the offboarding report is meaningful.

---

## 13. Implementation plan for Codex

Use small, bounded Codex tasks. Do not ask Codex to build the whole product in one prompt.

### Task 0 — Create repository and instructions

Ask Codex:

```text
Create a monorepo for the CapLedger MVP. Use Next.js for the frontend, FastAPI for the backend, PostgreSQL with pgvector via Docker Compose, SQLAlchemy and Alembic for migrations. Add a root AGENTS.md with project instructions, commands, coding standards, and definition of done.
```

Acceptance criteria:

- Repo has `apps/web`, `services/api`, `infra`, and `docs` folders.
- Docker Compose starts Postgres.
- README explains local setup.
- AGENTS.md exists.

### Task 1 — Backend data model and migrations

Ask Codex:

```text
Implement the SQLAlchemy models and Alembic migrations for organizations, departments, users, capability_assets, asset_versions, asset_embeddings, asset_approval_events, asset_usage_events, and asset_relations. Add seed data for Acme AI Labs.
```

Acceptance criteria:

- Migrations run cleanly.
- Seed command creates demo organization, departments, users, and example assets.
- Foreign keys and enums are implemented.

### Task 2 — Backend asset CRUD

Ask Codex:

```text
Implement FastAPI routes for capability asset CRUD, version creation, asset detail retrieval, and list filters. Add Pydantic schemas and basic tests.
```

Acceptance criteria:

- `GET /assets`, `POST /assets`, `GET /assets/{id}`, `PATCH /assets/{id}` work.
- Versions can be created and listed.
- Basic tests pass.

### Task 3 — Approval and usage tracking

Ask Codex:

```text
Implement approval workflow endpoints and usage tracking endpoints. Enforce simple role checks: managers/admins can approve/reject/deprecate; employees can submit and use assets.
```

Acceptance criteria:

- Submit, approve, reject, deprecate status transitions work.
- Events are recorded in `asset_approval_events`.
- Usage events are recorded.

### Task 4 — AI enrichment service

Ask Codex:

```text
Implement a provider-neutral AI enrichment service. It should accept raw prompt/workflow text and return structured JSON with suggested title, summary, use case, business process, required inputs, workflow steps, output schema, tags, risk level, and quality score. Add a mock mode when no API key is present.
```

Acceptance criteria:

- `POST /assets/enrich` works with mock mode.
- Response matches the enrichment schema.
- The app does not fail if no LLM API key is configured.

### Task 5 — Embeddings and search

Ask Codex:

```text
Implement keyword search and semantic search for approved capability assets using PostgreSQL and pgvector. Add a mock embedding mode for local development. Search should filter by department, status, owner, and tag.
```

Acceptance criteria:

- `GET /search?q=...` returns relevant assets.
- Approved assets are prioritized by default.
- Search works locally without external API keys.

### Task 6 — Frontend scaffold

Ask Codex:

```text
Build the Next.js frontend shell with navigation, dashboard page, asset list page, create asset page, asset detail page, review queue page, onboarding page, and offboarding page. Use Tailwind CSS and simple reusable components.
```

Acceptance criteria:

- Pages exist and load.
- Navigation works.
- Frontend can call backend API.

### Task 7 — Asset creation and enrichment UI

Ask Codex:

```text
Implement the create asset form. Add an 'Enrich with AI' button that calls the backend enrichment endpoint and lets the user review/edit suggested metadata before saving.
```

Acceptance criteria:

- User can paste raw workflow.
- AI suggestions populate fields.
- User can edit suggestions.
- Asset is saved as Draft.

### Task 8 — Asset detail, review queue, and usage

Ask Codex:

```text
Implement asset detail tabs, approval actions, copy/use buttons, and usage tracking. Implement the review queue for managers.
```

Acceptance criteria:

- Asset detail shows overview, workflow, inputs/outputs, versions, approval history, usage.
- Copy/use records usage events.
- Review queue allows approval/rejection.

### Task 9 — Onboarding and offboarding

Ask Codex:

```text
Implement onboarding pack generation by department/role and offboarding report generation by employee. Use approved assets, ownership, backup owner, usage count, and department relevance to rank recommendations.
```

Acceptance criteria:

- Onboarding page returns recommended assets and first-week plan.
- Offboarding page returns owned assets and missing backup owner warnings.
- Demo data produces meaningful reports.

### Task 10 — Polish, tests, and demo script

Ask Codex:

```text
Polish UI states, add loading/error handling, write basic backend tests, add a README demo script, and ensure Docker Compose can run the MVP locally.
```

Acceptance criteria:

- Local setup works from README.
- Demo script is clear.
- Basic tests pass.
- Product can be demoed end-to-end.

---

## 14. Suggested AGENTS.md content

Create this file at the repository root.

```md
# AGENTS.md

## Project

CapLedger is an MVP for Organizational AI Memory. It is an AI Capability Registry that turns prompts, workflows, automations, and agent know-how into governed, reusable organizational assets.

## Product principle

Do not build a generic wiki. The core object is an AI Capability Asset with owner, backup owner, version, approval status, input/output schema, usage tracking, and handover support.

## MVP loop

Submit asset → AI enrich → human review → approve → search/reuse → track usage → onboarding/offboarding.

## Tech stack

- Frontend: Next.js + TypeScript + Tailwind CSS
- Backend: FastAPI + Python
- Database: PostgreSQL + pgvector
- ORM: SQLAlchemy
- Migrations: Alembic
- Local infra: Docker Compose

## Repository structure

- `apps/web`: Next.js frontend
- `services/api`: FastAPI backend
- `infra`: Docker Compose and local infrastructure
- `docs`: product and implementation docs

## Development commands

Prefer adding Makefile commands:

- `make dev`: start local development
- `make db-up`: start PostgreSQL
- `make migrate`: run Alembic migrations
- `make seed`: seed demo data
- `make test`: run backend tests
- `make lint`: run lint checks

## Coding standards

- Keep tasks small and reviewable.
- Use explicit Pydantic schemas for API contracts.
- Do not expose private or draft assets to unauthorized users.
- Do not let AI-generated metadata become approved without human review.
- Keep AI providers behind a service interface.
- Include mock mode for AI enrichment and embeddings when API keys are missing.
- Prefer simple implementation over complex architecture in the MVP.

## Non-goals

Do not implement browser extension, MCP, HRIS integration, Neo4j, billing, enterprise SSO, or passive employee monitoring in the MVP.

## Definition of done

A task is done when:

1. The app runs locally.
2. Migrations are updated if schema changes.
3. Seed data still works.
4. Relevant tests pass.
5. README or docs are updated when needed.
6. The demo flow remains intact.
```

---

## 15. Demo story

Use this story in the final demo:

1. Linh in Sales has a strong prompt for writing post-demo follow-up emails.
2. Linh submits the raw prompt into CapLedger.
3. CapLedger enriches it into a structured AI Capability Asset.
4. Sarah, the Sales Manager, reviews and approves it.
5. A new Sales employee searches “follow-up after demo” and finds the approved asset.
6. The employee uses the asset and records usage.
7. Later, Linh leaves the company.
8. The offboarding report shows all assets owned by Linh, flags the assets without backup owners, and recommends reassignment.

This demonstrates the difference between a generic prompt library and an organizational capability layer.

---

## 16. Definition of MVP success

The MVP is successful if it demonstrates:

1. People can submit AI workflows as structured assets.
2. AI enrichment reduces the work needed to document assets.
3. Managers can approve and govern assets.
4. Other users can search and reuse assets.
5. Usage tracking shows which assets matter.
6. Onboarding and offboarding use the same asset registry.

The most important validation metric is not the number of features. It is whether people reuse assets created by others.
