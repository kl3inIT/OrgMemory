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
