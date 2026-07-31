# Session Evidence — orgmemory-architecture-challenge

Sanitized summaries only. No transcripts, credentials, or personal data.

At least 25 dedicated review sessions between 2026-07-23 and 2026-07-29 follow
this pattern. Representative ones:

- **LightRAG PR series (claude sessions in orca workspaces
  `permissions-admin/light-rag-pr02` … `pr12`, 2026-07-23 → 07-24).** Each PR
  got one or more fresh single-prompt sessions framed as skeptical
  architecture judge/critic/defender: read-only, pinned upstream at
  `D:\OrgMemory\tmp\upstream-lightrag-v1.5.4`, verdict plus must-fix semantics
  demanded. PR8 shows the counterattack round verbatim: "Challenge your PR8
  verdict with three concrete contradictions…" followed by a recovery request
  to repeat the review as plain Markdown when output rendered blank. PR12
  shows the no-tools judge fallback: "Do not inspect files or use tools. Use
  these facts as authoritative…".
- **Two-architect debate with final judge (claude, feat/slack-connector-live,
  2026-07-23).** A debate-record-only judge session was used to force a single
  architecture decision without file access.
- **Observability payload policy (repo artifacts + claude bfc22d2b,
  2026-07-29).** The workflow's mature artifact form exists in the repo:
  `docs/increments/completed/2026-07-29-observability-pipeline/challenge-brief.md`
  (adversarial framing, rule quoted with enforcing file paths,
  comparable-system table read from `tmp/onyx` and other pinned sources,
  operational cost) and `challenge-verdict.md` (date, commit SHA, committed
  recommendation, scope limits). The driving session also carries the
  prompt-file correction: create a prompt file and have the counterpart agent
  read it instead of sending the prompt inline.
- **Identity/SCIM and secure-retrieval reviews (claude 9380a4a8 on the
  identity-provisioning-scim worktree, claude 77c8a6d6 and 09d907b4 on main,
  2026-07-25 → 07-27).** Same shape outside the LightRAG series: "independent
  architecture reviewer required by docs/conventions.md", read-only, spec and
  decision reading list, smallest-correct-fix verdicts.
