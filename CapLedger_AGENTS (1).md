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
