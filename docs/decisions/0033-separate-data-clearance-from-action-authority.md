# 0033 — Separate Data Clearance From Action Authority

## Status

Accepted on 2026-08-06 after an independent two-architect debate.

## Context

The application-user `role` enum mixed dead job-title labels, an apparent
administrator label that granted no administration, and the one live policy
bit: Executive status widened CONFIDENTIAL and RESTRICTED evidence reads. The
same six-value selector therefore obscured both what changed and what did not.
OpenFGA already owns administrative action authority through static,
release-pinned relations.

## Decision

Replace the local role with a closed `STANDARD | EXECUTIVE` clearance. The
database rejects unknown values and migrates every former non-Executive value
to Standard. Executive keeps the existing retrieval behavior: org-wide access
to CONFIDENTIAL and RESTRICTED evidence. The retrieval SQL and permission
decision table do not change.

Administration remains an OpenFGA `can_manage_members` decision. No OpenFGA
model bytes or relationships change. The browser labels clearance explicitly,
confirms the Executive blast radius, and never uses clearance to infer an
administrative route. Department assignment is exposed alongside clearance so
the existing CONFIDENTIAL policy can be repaired without direct database work.

## Rejected Alternative

A numeric, governed clearance hierarchy plus customer-defined OpenFGA role
bundles was rejected. It introduces a second Postgres-to-OpenFGA convergence
boundary and forces model-rotation behavior before the deployment has a
zero-drift publication compatibility gate. Its widening is additive, so
deferral is inexpensive.

Revisit customer-defined role bundles on the first concrete customer
requirement. If a new clearance tier is required first, extend the closed,
ordered clearance model without coupling it to action authority.

## Consequences

Clearance now says only which classified evidence may be read; OpenFGA says
which administrative actions may be performed. HR titles remain out of the
authorization model. Any future authorization-model evolution first requires
the roadmap's publication compatibility and convergence rollout gate because
current retrieval pins publications to the exact applied model identity.
