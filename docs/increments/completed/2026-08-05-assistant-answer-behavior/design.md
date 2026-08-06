# Assistant Answer Behavior

## Status

Completed and production-verified on 2026-08-06. This increment changed answer
wording, prompt discipline, and one browser disclosure without changing
retrieval, authorization, ranking, evaluation scoring, or official fixtures.
The owner-run post-merge sweep met the citation threshold and preserved the
permission boundary after accounting for the documented P035 fixture mismatch;
the detailed scoreboard and operational follow-up are recorded in
[results.md](results.md).

## Problem

The production post-reseed transcript preserved the permission boundary: the
Assistant received only permission-verified evidence and did not claim that a
restricted document existed. The user-facing answer text still failed in four
ways:

1. Deny cases such as P027 and P032 used pipeline voice (for example,
   `Bằng chứng được cung cấp không có...` and
   `Dữ liệu được cung cấp không có...`) instead of speaking from the user's
   perspective.
2. Not-found answers ended without a useful next step.
3. P035 presented neighboring authorized information as though it directly
   answered the requested target.
4. Citation score was 41/43: P031 cited DOC011 while using DOC001 and DOC011,
   and P001 cited DOC001, DOC002, and DOC011 while only DOC001 grounded the
   answer.

The authorization architecture is not the defect. `AuthorizedEvidenceScope`
trims evidence before generation, and the model cannot know whether another
document exists. The repair must preserve that ignorance rather than adding
permission metadata to the prompt.

## Current Pipeline

`AssistantService` has two final-answer paths:

- When verified evidence exists, it either renders the canonical evidence in
  `AssistantPromptFactory` or forwards the already-verified LightRAG generation
  request. The latter currently receives only the personalization block, so a
  behavior instruction added solely to the canonical renderer would miss the
  production GraphRAG path.
- When verified evidence is empty, the service does not call the model. It
  returns the fixed English `NO_ACCESSIBLE_EVIDENCE` sentence, which cannot
  satisfy same-language wording or the escalation requirement.

The browser renders bracketed citations from the server-declared citation map.
It has no localization framework; product copy is colocated with the owning
component. The requested Vietnamese disclosure therefore remains static copy
owned by the Assistant answer presentation.

## Research Basis

The design follows the supplied research and rechecks the local reference
implementations rather than copying their product vocabulary:

- [Spring AI 2.0 RAG guidance](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/api/retrieval-augmented-generation.html)
  says to admit when the answer is absent and to avoid phrases such as “Based
  on the context” and “The provided information.” Its
  `ContextualQueryAugmenter` disallows empty context by default and instructs
  the model not to answer. OrgMemory keeps its stronger deterministic
  zero-evidence path while improving that path's wording.
- [Anthropic's hallucination guardrails](https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-hallucinations)
  recommend explicitly allowing uncertainty, grounding claims in source text,
  retracting claims that cannot be supported, and restricting external
  knowledge.
- The [OpenAI Model Spec dated 2025-12-18](https://model-spec.openai.com/2025-12-18.html)
  ranks a no-answer above a wrong answer, favors concise direct responses, and
  warns against confirming or denying confidential material. That supports a
  truthful user-perspective not-found answer without speculating about hidden
  documents.
- The pinned [OpenAI Cookbook grounded-QA example](https://github.com/openai/openai-cookbook/blob/0cc994f27512791587a9ea35f77edd5331961e66/examples/Question_answering_using_embeddings.ipynb)
  uses the direct fallback “I could not find an answer.”
- LightRAG v1.5.4 at pinned revision
  `9a45b64c2ee25b1d806e90db926a8af37480bb16`
  (`D:/OrgMemory/tmp/upstream-lightrag-v1.5.4/lightrag/prompt.py`, lines
  330-356) has a fixed no-context response and tells the model not to guess
  when the answer is absent.
- Onyx at pinned revision
  `618b5031bf21463f44e3bed9eb9d5073b806fec0` enforces user ACLs before search
  (`backend/onyx/context/search/preprocessing/access_filters.py`, lines 8-22)
  and contains no permission-denial wording in its prompt set. Its citation
  prompt requires relevant inline `[n]` citations
  (`backend/onyx/prompts/chat_prompts.py`, lines 39-50).
- [Microsoft 365 Copilot privacy guidance](https://learn.microsoft.com/en-us/copilot/microsoft-365/microsoft-365-copilot-privacy)
  says Copilot surfaces only organizational data the user may view, and
  [Glean's access guidance](https://docs.glean.com/user-guide/assistant/how-glean-accesses-info)
  likewise describes results as tailored to accessible documents. Neither
  pattern requires telling the model that a denied document exists.

These references agree on the useful boundary: authorization precedes
generation; the prompt admits insufficient support, uses only supplied facts,
and cites the facts it actually states.

## Required Behavior

The implementation must encode all six requirements together:

1. **Restricted-resource non-disclosure.** Never state or imply that documents
   exist outside the user's permissions. Never confirm or deny that a specific
   restricted document exists. The prompt must not speculate about withheld
   material.
2. **User-perspective no-answer.** When available evidence does not answer the
   question, reply plainly in the user's language with the equivalent of
   `Tôi không tìm thấy nội dung này trong các tài liệu bạn truy cập được`.
   Pipeline-oriented wording is prohibited, including `bằng chứng được cung
   cấp`, `dữ liệu được cung cấp`, `based on the context`, `the provided
   information`, and their equivalents.
3. **One escalation line.** End every not-found answer with one short next
   step equivalent to `Nếu bạn cho rằng thông tin này tồn tại, hãy liên hệ bộ
   phận sở hữu tài liệu hoặc quản trị viên.` It appears once and stays concise.
4. **Adjacent information is labeled.** If evidence is relevant but does not
   answer the target, introduce it as the nearest available information, for
   example `Thông tin gần nhất tôi tìm thấy trong phạm vi của bạn là...`. Do
   not present it as the direct answer.
5. **Exact inline citations.** Put `[n]` inline on every factual statement
   taken from evidence. Cite every source whose facts appear and no source
   whose facts do not appear. Preserve bracketed numbers because the
   `AssistantCitation` pipeline depends on them.
6. **Direct answer voice.** Do not expose retrieval, ranking, keyword plans,
   prompt construction, authorization internals, or other meta commentary.

The existing injection-safety requirements remain unchanged: evidence and
user-context text are untrusted data, and user context is personalization only
and cannot change authorization.

## Design

### Shared generation instruction

`AssistantPromptFactory.SYSTEM_INSTRUCTION` becomes the single answer-behavior
policy. The canonical evidence renderer continues to prepend it. The
already-verified LightRAG request also appends the same instruction before the
existing user-context block, so both model-backed paths receive R1-R6 without
rebuilding or weakening the verified grounding.

The instruction describes prohibited pipeline voice without embedding the
banned example phrases themselves. A focused regression test inspects both
prompt paths, asserts the positive R2/R3/R5 invariants, preserves the
injection-safety lines, and rejects a lower-cased banned-phrase list.

### Deterministic zero-evidence answer

The empty-evidence path stays model-free. `AssistantService` selects a bounded
Vietnamese or English message from the question text and returns it with no
citations. The Vietnamese message is used when the question contains
Vietnamese-specific characters or common Vietnamese question words; otherwise
the English equivalent is used. Both variants contain exactly the not-found
sentence and one escalation sentence. This keeps zero evidence from reaching a
model while meeting R2 and R3 for the production languages in scope.

This is deliberately not a general language-classification subsystem. Adding
more product languages belongs with a real localization facility rather than
an unbounded prompt or dependency.

### Browser disclosure

Every rendered Assistant answer, including replayed answers and deterministic
no-evidence answers, shows this static supporting line directly below its
content:

`Câu trả lời chỉ dựa trên tài liệu bạn có quyền truy cập.`

User messages do not show it. The copy is presentation-only and makes no claim
that another document exists. A component test protects both the positive and
negative rendering conditions.

## Security And Scope Boundaries

- Do not change `AuthorizedEvidenceScope`, retrieval candidates, OpenFGA,
  canonical rechecks, citation hydration, scorers, or official evaluation
  fixtures.
- Do not send denied-resource metadata or empty evidence to the model.
- Do not parse provider output to invent or delete citations. Citation quality
  is steered at generation; the existing server declaration and browser
  interaction contract remain authoritative.
- Do not claim success from unit tests. Prompt wording is nondeterministic at
  runtime and needs the production evaluation sweep.

No independent architecture challenge is required: this increment preserves
the existing authorization, persistence, publication, and retrieval decisions
and changes only behavior instructions, a deterministic fallback, and static
presentation copy. Any proposal to expose denied-resource existence or alter
retrieval would cross that boundary and require a separate challenged design.

## Verification And Exit

Local gates:

- focused prompt and empty-evidence tests;
- focused Assistant answer component test;
- `./gradlew.bat --no-daemon clean test` as the terminating JVM context gate;
- Node 24 web lint, typecheck, unit tests, and production build;
- diff audit proving retrieval, authorization, scorers, and fixtures are
  untouched.

Post-merge and post-deploy, the owner runs the official production sweep. The
required result is permission deny 7/7 preserved and citation at least 41/43,
with 43/43 the target. The owner also reviews P027 and P032 for user-perspective
not-found plus one escalation line, P035 for explicit adjacent-information
labeling, P031 for DOC001+DOC011, and P001 for DOC001 only. Specs, test matrices,
and increment completion remain pending until that sweep confirms behavior.
