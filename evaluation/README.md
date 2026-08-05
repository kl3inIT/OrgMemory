# OrgMemory Evaluation

This directory contains offline quality and semantic-conformance tooling. It is
not part of the API or worker runtime.

## Deterministic LightRAG oracle

`oracle/generate_lightrag_v1_5_4.py` executes the pinned upstream fixed-token
chunker and weighted-polling function, and verifies the upstream
entity/relation embedding payload expressions. It refuses any checkout other
than commit `9a45b64c2ee25b1d806e90db926a8af37480bb16`.

From the repository root:

```powershell
evaluation\.venv\Scripts\python.exe `
  evaluation\oracle\generate_lightrag_v1_5_4.py `
  --upstream D:\OrgMemory\tmp\upstream-lightrag-v1.5.4 `
  --output evaluation\baselines\lightrag-v1.5.4-oracle.json

.\gradlew.bat :components:graph-rag-core:test `
  --tests "com.orgmemory.graphrag.query.LightRagUpstreamOracleTests"
```

The committed JSON is the single source consumed by Java conformance tests.
Regenerate it only from the pinned checkout and review semantic changes rather
than accepting a changed file mechanically.

## Official 50-case offline evaluation

`demo/fixtures/public-evaluation.json` is the single source of record for the
official hackathon evaluation. The loader requires exactly P001-P050, rejects
unknown permission, answer-type, or difficulty values, splits semicolon-delimited
multi-document goldens, and verifies that actor IDs stay within U001-U032. The
source currently contains 43 Allow cases and 7 Deny cases; it is never copied or
rewritten by the evaluator.

The production sweep writes one UTF-8 JSON object per line using transcript
schema `orgmemory.official-transcript.v1`. Blank lines, duplicate/missing cases,
unknown fields, actor/case mismatches, duplicate citations, non-finite timings,
or TTFT later than completion are rejected.

| Field | Type | Contract |
|---|---|---|
| `question_id` | `Pnnn` string | Exactly one row for every official P001-P050 case. |
| `http_status` | integer 100-599 | Final HTTP status observed for the Assistant request. |
| `sse_terminal_event` | `"finish"`, `"error"`, `"abort"`, or `null` | Final Assistant SSE event; `null` means the request was rejected before an SSE terminal event. |
| `answer_text` | string | Assistant answer text only. HTTP problem details are not answer text. |
| `cited_document_ids` | unique `DOCnnn[]` | Document IDs resolved from the emitted citations, in emitted order. |
| `latency_ms` | non-negative number | Request start through response/stream termination. |
| `ttft_ms` | non-negative number or `null` | Request start through first answer token; `null` when no answer token was emitted. |
| `actor_user_id` | `Unnn` string | Must equal the official case's user ID. |

An Allow permission pass is exactly HTTP 200 + terminal `finish` + non-blank
answer + non-null TTFT. A Deny permission pass is either an HTTP 403 transport
rejection or HTTP 200 + terminal `finish` with no citation of any expected denied
document and no verbatim denied-document content. The latter permits a polite
no-information answer after org-level search authorization succeeds and
fail-closed retrieval returns no evidence.

`DENY_EVIDENCE_LEAK` takes precedence over both passing paths. It means the
response cites an expected denied document or an answer line shares at least
eight consecutive Unicode word tokens, compared case-insensitively, with one
line of that document's `demo/fixtures/documents/DOCnnn.md` body. The report
records matching document IDs but not answer or document content. A terminal
`error` or `abort` is not accepted as a permission denial. Citation scoring
applies to Allow cases and requires the cited set to equal the expected set.
Missing, wrong, unexpected, and partial citations are distinct; P031 passes
only with both DOC001 and DOC011.

Latency and TTFT are reported per case and as medians plus explicitly labeled
observed max-of-N groups by difficulty and answer type. The scorer never labels
small-N maxima as p95. Its JSON report omits raw answers.

```powershell
Set-Location evaluation
uv sync --frozen --dev
uv run orgmemory-official-eval `
  --transcript output\official-production-transcript-v1.jsonl `
  --output output\official-evaluation-report-v1.json
```

The optional judge is disabled by default and therefore makes no network call.
An explicit `--judge-plugin module:factory` may supply the `OfficialJudge`
protocol. Its assessment structure mirrors the pinned LightRAG v1.5.4
`reproduce/batch_eval.py` criteria at commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16`: comprehensiveness, diversity,
and empowerment, plus an overall score. Judge results are diagnostic and do not
replace deterministic permission or citation verdicts.
Plugin exceptions, invalid assessments, and missing assessments are recorded as
per-case `judge_error` values and an aggregate `failure_count`; they never
prevent the deterministic report from being written. Both deterministic gate
CLIs write their report and exit with status 1 when their gate fails.

## Retrieval recall gate

`orgmemory-retrieval-recall` scores the ADR 0020 cache-miss bypass against the
current keyword-seeded path. It derives 43 Allow-case document goldens directly
from `demo/fixtures/public-evaluation.json`; there is no separately maintained
golden-question file. A retrieval export supplies ordered document IDs for both
paths. The scorer reports macro document recall@40, diagnostic keyword document
recall@60, and fails when bypass recall regresses by more than two percentage
points.

```powershell
Set-Location evaluation
uv sync --frozen --dev
uv run orgmemory-retrieval-recall `
  --observations output\retrieval-observations-v2.json `
  --output output\retrieval-recall-report-v2.json
```

The official questions are goldens, not a gate verdict: observations from the
two real retrieval paths are still required before ADR 0020 condition 3 can
pass.

## RAGAS

RAGAS evaluates exported, sanitized Assistant cases. It is a stochastic
external judge and must be run in at least two trials; the runner reports mean,
spread, minima and maxima rather than presenting one score as truth.

The runner uses the modern `ragas.metrics.collections` API end to end. Judge
calls use an asynchronous OpenAI client, while answer-relevancy embeddings use
a separate synchronous client because RAGAS 0.3.9 invokes that embedding
contract synchronously. Metric calls are concurrency-bounded and individually
timed out. OpenAI SDK retries cover transient transport, rate-limit and server
failures; a failed or non-finite metric aborts the run instead of producing a
partial quality report.

```powershell
Set-Location evaluation
uv sync --frozen --dev
$env:OPENAI_API_KEY = "<managed secret>"
uv run orgmemory-ragas `
  --input fixtures\sample-evaluation.json `
  --output output\ragas-results.json `
  --trials 3
```

The API key is read only from the environment. Prompts, retrieved contexts and
answers are not copied into the result artifact. Do not use production evidence
in evaluation fixtures. The included synthetic fixture proves evaluator wiring
only; it is not a product-quality benchmark.

## RAG workload route comparison

`orgmemory-workload-routing` compares the current Keyword and Graph baselines
with GPT-5.6 Luna using a fixed bilingual synthetic corpus. The default run uses
four cases and three repetitions per route. Luna uses `reasoning_effort=none`;
baseline requests omit the option so the provider default is preserved.

The output retains only fixture identity, model coordinates, validity counts,
keyword/entity recall, graph yields, provider-failure counts, and p95 latency.
It never retains the prompts, evidence, or raw model responses.

```powershell
Set-Location evaluation
uv sync --frozen --dev
$env:OPENAI_API_KEY = "<managed secret>"
uv run orgmemory-workload-routing `
  --input fixtures\workload-routing-v1.json `
  --output output\workload-routing-results.json `
  --repetitions 3
```

Keyword activation requires every candidate response to validate, no higher
provider-failure count, and no more than 0.05 mean recall regression. Graph also
requires at least 80% of baseline entity/relation yield and improved p95
latency. These gates are independent; a failed Graph gate does not block an
approved Keyword route.
