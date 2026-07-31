# Independent Architecture Challenge Brief

Date: 2026-07-31

## Decision To Challenge

Choose how to split `core.knowledge` and `core.assetregistry`, whose root
packages contain hundreds of Java types, while preserving their top-level
Spring Modulith boundaries and keeping each implementation pull request below
100 changed files.

## Repository Constraints

- `knowledge` and `assetregistry` remain top-level application modules inside
  `core`; this work does not create a Gradle project per aggregate or profile.
- Java package moves change fully qualified names and all in-repository callers
  must be updated in the same pull request.
- Adapter-facing ports require intentional named interfaces.
- Persistence mappings, authorization behavior, endpoints, and public
  contracts must not change solely because packages move.
- `ApplicationModules.verify()` is an existing repository gate.

## Competing Positions

1. Move responsibility slices immediately into nested application modules,
   keep them temporarily open, repair direct edges, and close them under an
   explicit exit gate.
2. First use ordinary internal subpackages, complete all physical moves, and
   annotate or close nested modules only after the package graph is stable.

## Questions For The Reviewer

- Which position creates the safer intermediate architecture in this codebase?
- What Java and Spring Modulith assumptions are incorrect or incomplete?
- Which first slice provides real enforcement without forcing a broad redesign?
- What mechanical gates prevent temporary openness from becoming permanent?

The challenge ran for two adversarial rounds with Claude Fable 5 and a separate
judge pass. The accepted outcome is recorded in
[challenge-verdict.md](challenge-verdict.md).
