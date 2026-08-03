# Challenge Verdict - Graph Extraction Model Route

Date: 2026-08-02  
Commit reviewed: `00aabe15cbc4bc1a42a650ecee65b3b81a7b8649`  
Verdict: **accept with changes**

Fable 5 was attempted first in a fresh Orca terminal, but returned no review
tokens and reported that 98% of its weekly allowance was already consumed.
The required fallback was therefore a fresh Codex `gpt-5.6-sol` session with
ultra reasoning. Both sessions were read-only; neither edited the worktree.

## Committed recommendation

Use `gpt-5.4-mini` as an independent Graph Extraction deployment default,
with `ORGMEMORY_GRAPH_EXTRACTION_MODEL` retained as the explicit override.
Keep Graph Extraction absent and non-editable in the current Language Models
route editor. Future editability belongs in a graph/index-processing workflow
that creates an immutable processing profile, previews affected content, and
requires an explicit rebuild decision.

## Must-fix items accepted

1. Remove the nested fallback through `ORGMEMORY_OPENAI_MODEL`; otherwise an
   Assistant-model change would still alter Graph Extraction.
2. Update the root `.env.example` as well as production configuration.
3. Test route separation: Assistant unchanged, Keyword retains its current
   Assistant fallback, Graph defaults independently, and its explicit override
   still wins in both API and worker.
4. Activate through a bounded production canary. Retain `gpt-5.6-sol` as the
   immediate override-based rollback until the new model proves valid output,
   lower latency, and acceptable entity/relation yield.
5. State explicitly that the route change creates a new processing profile for
   new or explicitly rebuilt jobs. It neither rewrites completed jobs nor
   authorizes a global reindex.
6. Preserve the backend mutation guard as well as the current UI omission.

## Strongest counterargument

`gpt-5.6-sol` is the only candidate with observed production completion in
OrgMemory. The incident does not isolate model identity from concurrency,
timeout, or gateway behavior. A smaller model could reduce visible latency
while silently losing entities or relations. `gpt-5-mini` is also a credible
conservative alternative because upstream role-specific documentation uses it
even though the pinned v1.5.4 environment default has advanced to
`gpt-5.4-mini`.

## Scope limit

The verdict approves the default and current UI boundary only. It does not
approve a corpus-wide rebuild, claim quality parity before a live canary, or
make Graph Extraction editable through the generic route API.
