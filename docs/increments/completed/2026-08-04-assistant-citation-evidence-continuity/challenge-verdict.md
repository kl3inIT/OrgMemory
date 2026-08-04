# Assistant Evidence Continuity And Live Activity Challenge Verdict

Date: 2026-08-04
Commit reviewed: `bc59ddb4daf6faaa0a6296fdb2959e2d08838545`

## Verdict

`ACCEPT WITH MUST-FIXES`.

Persisting minimal citation mappings is justified because the transcript text
cannot reconstruct the server-declared chunk mapping after reload. Rendering
untrusted Markdown and moving blocking retrieval behind an already-started
stream are acceptable only with the closed security, concurrency, and terminal
contracts below.

## Strongest Counterargument

A live-only UI change could remove the empty carousel, parse Markdown, and use
AI SDK `submitted` versus `streaming` for two truthful waiting labels without a
migration or asynchronous retrieval scheduler. The full proposal adds a new
Assistant dependent, lazy authorization composition, a hardened rich-text
surface, product-owned concurrency/overload policy, context propagation, and
cancellation paths before citation reopening or retrieval latency has measured
usage frequency.

## Must-Fixes Accepted Into The Design

### Citation ownership and hydration

1. Keep actor-owned transcript delivery independent from live evidence
   authorization. Hydrate citation affordances separately so OpenFGA failure
   cannot hide historical answers, and never serialize stored chunk IDs before
   current authorization.
2. Hydrate one visible assistant message at a time, cap it at 100 stored
   references, deduplicate chunks, resolve current scope/model once, and use
   fixed authorization batches of at most 20. Determinate denied or missing
   entries may be filtered; provider-indeterminate state is an unavailable
   hydration, not an empty denied set. Protect the boundary with call-count
   tests.
3. Make mappings immutable Assistant-owned message dependents with repeated
   organization/actor ownership, a composite message foreign key, cascade
   deletion, and unique citation number per message. Add no foreign key from
   transcript retention to canonical chunks.
4. Persist answer and server-declared mappings in the same completion
   transaction. Citation failure rolls back the answer; failed, cancelled,
   empty, and aborted turns leave neither row type.

### Excerpt and original representation

5. Re-run current canonical authorization and availability for every excerpt,
   but record excerpt-specific final allow/deny audits. Return one opaque 404
   for missing, denied, stale, and unavailable evidence; disclose at most 4,000
   Unicode code points plus truncation, title, heading, and page range only
   after allow. Exclude URI, object key, stored MIME, and internal identifiers;
   send `no-store` and `nosniff`.
6. Derive a closed `PDF|MARKDOWN|PLAIN_TEXT|IMAGE|DOWNLOAD` presentation kind
   from the server filename policy. Blob MIME and display title never select a
   renderer. PDF, safe text, and exactly PNG/JPEG/GIF/WebP may render inline;
   DOCX, PPTX, and unknown types remain download-only.
7. Use a citation-specific Streamdown profile, not the Assistant response
   renderer. Escape/strip embedded HTML, disable Mermaid/custom active
   renderers and automatic remote resources, harden explicit link navigation,
   retain raw view, and fall back to raw on render error. Browser tests must
   prove hostile HTML, remote images, dangerous URLs, SVG/data URLs, and
   Mermaid directives cause no execution or attacker-origin request.
8. State that citation persistence provides reload continuity only. A source
   revision or revocation can make a historical marker inert; immutable
   historical evidence is not authorized by this increment.

### Pre-first-token activity

9. Run blocking retrieval on a dedicated application-owned scheduler with
   maximum concurrency, finite queue, generic overload rejection, and shutdown
   disposal. `Flux.defer` alone and the global elastic pool are insufficient.
10. Resolve immutable actor and model authority on the authenticated request
    thread. Pass them as values; never read thread-local security on the
    worker. Restore observation context separately with the repository's task-
    decoration pattern.
11. Invoke the existing proxied synchronous retrieval service on the worker so
    each transaction remains short and worker-bound. Do not stretch a Spring
    transaction over the reactive stream; conversation begin and completion
    remain separate transactions.
12. Connect browser abort, actor/conversation change, disconnect, and timeout
    to scheduled retrieval disposal/interruption, downstream model cancel, and
    prevention of transcript completion. Retain finite dependency timeouts for
    blocking calls that ignore interrupt.
13. Start turn observation at subscription and stop exactly once. Measure
    retrieval separately; TTFT begins at the turn and ends only on the first
    model text token. Failure, cancellation, empty output, and the server-owned
    no-evidence fallback record no TTFT sample.
14. Define a closed transient activity protocol carrying only phase, state,
    and optional authorized evidence count. Browser-owned copy maps the enum;
    query, source identity, arbitrary server prose, reasoning, and activity
    history are excluded.
15. Specify and test terminal sequences for success, no evidence, retrieval
    failure/overload, provider failure/empty output, cancel, and timeout. Stop
    remains visibly available in both submitted and streaming states, and no
    late event may resurrect cleared activity.
16. Prove with a latch-controlled integration test that stream start and
    `RETRIEVAL / ACTIVE` reach the client while retrieval is blocked. Retrieval
    remains server activity; only real model-selected invocations become AI
    SDK `tool-*` parts and AI Elements Tool UI.

## Strongest Failure Scenarios

- One revoked chunk enters the current all-or-nothing verifier and suppresses
  every otherwise allowed citation.
- An authorization outage blocks an unbounded history response and makes an
  owned transcript appear missing.
- A Markdown remote image performs a tracking request or the existing Mermaid
  plugin widens the active-content surface.
- A shared elastic pool lets slow retrieval starve unrelated product work, or
  queue saturation leaves the stream waiting forever.
- Scheduler handoff loses trace parenting, reads an empty thread-local security
  context, or spans a transaction across reactive subscription.
- Client stop clears the UI while blocked work later emits activity, starts a
  model call, or commits an answer.

## Repository Evidence

- Historical answers are snapshots while citation opens recheck current access:
  `docs/specs/domains/assistant-and-mcp.md:97-106`.
- Protected metadata, snippets, counts, and source IDs must not leak:
  `docs/guidelines/agent-safety.md:3-14`.
- Messages currently persist no citation mapping:
  `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationMessage.java:13-67`.
- Current canonical citation verification is all-or-nothing:
  `core/src/main/java/com/orgmemory/core/knowledge/retrieval/CanonicalEvidenceAuthorizationService.java:54-99,118-190`.
- Markdown and text both arrive as `text/plain`, while the current browser
  sends all text to `<pre>` and accepts broad `image/*`:
  `KnowledgeContentType.java:28-33` and
  `assistant-sources-panel.tsx:364-407`.
- Retrieval blocks before the controller can construct the SSE response:
  `AssistantService.java:88-158` and `AssistantController.java:102-139`.
- Current turn observation already protects one-stop semantics and model-token
  TTFT: `AssistantService.java:113-153` and
  `AssistantTurnObservationTests.java:64-117`.
- Existing stream timeout cancels its source, and GraphRAG propagates interrupt
  to its internal futures: `UiMessageStream.java:39-49` and
  `DefaultGraphRagKnowledgeRetrievalService.java:499-543`.

## Committed Recommendation

Proceed with the selected boundary in [design](design.md). Deliver transcript
persistence and lazy hydration without coupling Assistant to Retrieval
internals; keep rich source rendering behind a closed server presentation kind;
and treat pre-token progress as a bounded, cancellable server orchestration.

## Rejected Alternatives

Reject live-only citations because safe reload reconstruction is impossible.
Reject eager history hydration because authorization availability must not own
transcript availability. Reject the existing Assistant Markdown renderer for
untrusted documents. Reject client-invented percentages and fake retrieval tool
calls. Reject `Flux.defer` without scheduling and the shared elastic pool
because neither creates an Assistant concurrency or overload contract.
