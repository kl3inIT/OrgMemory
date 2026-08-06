# Assistant Answer Behavior Production Results

## Delivery

PR #305 merged as `333b6ec6` and deployed with the subsequent main commit
`f4282a70`. The owner ran the final 50-case production sweep on 2026-08-06.
The committed [official evaluation report](official-eval-report-2026-08-06.json)
has SHA-256
`efb5da8525bb2fbdeb81cb85de4781a9ed59237b93d10e0efc0a0f6c9ef18847`.

Following the Phase 2 evaluation-evidence convention, the raw transcript is
not committed. The report records its schema, 50-case count, and SHA-256
`68141530336c2608f5b6cb57128fd0da46a2d63643975e93915d04fa8db393c3`.

## Scoreboard

| Gate | Result | Verdict |
| --- | --- | --- |
| Terminal completion | 50/50 cases reached `finish` | pass |
| Permission | 49/50 | expected known mismatch: P035 is Deny in the official fixture while authoritative metadata makes DOC030 available to All Employees |
| Official Deny cases | 7 reviewed; 6 scorer passes plus P035 | no new product authorization exception; preserve and report the fixture inconsistency |
| Exact citation set | 41/43 Allow cases | pass at the required threshold; 43/43 target not reached |
| End-to-end latency | 3,432 ms median; 7,723 ms observed max-of-50 | recorded, not a percentile estimate |
| Time to first token | 2,965 ms median; 6,994 ms observed max-of-50 | recorded, not a percentile estimate |

The two residual citation failures are:

- P031 is `PARTIAL`: DOC001 is missing; DOC003 is unexpected and DOC011 is
  present.
- P001 is `UNEXPECTED_DOCUMENTS`: DOC001 is present, with extra DOC002 and
  DOC011 citations.

## Wording Review

Manual review of the seven official Deny-labeled answers found none of the
banned pipeline-voice phrases. The deterministic no-answer examples use a
user-perspective not-found sentence followed by one escalation sentence; P009,
P027, P032, P037, and P042 demonstrate that path. P007 labels neighboring
authorized material with `Thông tin gần nhất trong phạm vi tài liệu là...`
instead of presenting it as the requested 2026 answer. P035 directly answers
from DOC030 because the repository's authoritative metadata grants that
document to All Employees; this is the
[known dataset inconsistency](../../../../demo/README.md#known-dataset-inconsistency),
not a permission-safe wording regression.

These results verify the increment's restricted-resource non-disclosure,
user-perspective no-answer, one-line escalation, adjacent-information labeling,
and direct-answer voice requirements in production. Exact citation discipline
met the 41/43 exit threshold but retains the two residual cases above.

## Operational Incident And Follow-up

The first post-deploy sweep failed all 50 turns before answer evaluation with:

> 400: Function tools with reasoning_effort are not supported for gpt-5.6-sol
> in /v1/chat/completions. To use function tools, use /v1/responses or set
> reasoning_effort to 'none'.

PR #304 added Assistant function tools, while the `ASSISTANT_CHAT` deployment
route supplied no explicit reasoning effort. The deployed gateway default made
that tools-plus-implicit-effort combination invalid. The temporary production
mitigation was an organization-level `ASSISTANT_CHAT` override to the
`openai-chat-noeffort` profile
`92d0993f-d53a-442c-8576-90868696628a` with
`openAiReasoningEffort=NONE`. This override is runtime state and is not the
repository fix.

The follow-up deployment-config fix is deliberately deferred to PR B, which
must start from updated `origin/main` only after the owner merges this
consolidation PR. PR B will make the Assistant chat effort explicit for fresh
deployments and document removal of the temporary organization override.
