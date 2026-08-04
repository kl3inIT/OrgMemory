# Assistant Evidence Continuity And Live Activity

## Intent

Make every Assistant citation lead to useful evidence in one action, retain
that behavior after conversation reload, communicate real work before first
token, and state original-file support honestly without weakening OrgMemory's
permission boundary.

## Current Gap

The inline citation hover card is visually large but contains no excerpt or
document content. It repeats a title, a generic permission sentence, and a
`1/1` carousel. Clicking it opens the Sources panel; opening the full document
requires another click.

Live answer streaming supplies the citation-number-to-chunk mapping, but the
persisted transcript stores only message text and feedback. On reload the
browser cannot reconstruct actionable citations. The full-content route does
correctly recheck current authorization and safely dispatch supported browser
representations, but the UI does not distinguish exact evidence from the
entire original file. Markdown is currently served as safe `text/plain` and
then rendered in a `<pre>`, so headings, lists, tables, links, and code remain
raw syntax. Before first token, one static `Searching permitted knowledge…`
label covers both blocking retrieval and provider generation, so a long search
looks indistinguishable from a stalled model.

## Reference Judgment

The pinned Onyx reference keeps citations compact and moves ordered source
exploration into a dedicated sidebar. Current AI Elements documentation shows
that its citation components render application-supplied metadata and optional
quotes; they do not fetch evidence or provide an authorization contract.

OrgMemory will keep the compact interaction pattern and local AI Elements
presentation primitives. It will not port Onyx's packet/document architecture
or treat AI Elements as an evidence layer. See [reference study](reference-study.md).

## Selected Design After Independent Challenge

### One click to authorized evidence

Remove the single-source carousel and generic permission copy. An accessible
inline citation trigger selects its source and opens the source surface with a
bounded, currently authorized chunk excerpt already visible. The excerpt is
the retrieved canonical evidence chunk, not a claim that every sentence in the
chunk was quoted by the model.

The source surface shows title, heading/page context when present, the plain-
text evidence excerpt, and a clear secondary action: `Open document` for safe
inline formats or `Download original` for attachment-only formats. It does not
require a second source-row click merely to reveal the selected citation.

### Minimal durable citation references

Store citation number and canonical chunk ID in a separate immutable row owned
by the completed assistant message, actor, and organization. Persist it in the
same successful transcript commit as the answer. Do not persist raw evidence,
source bytes, title, URL, heading, page metadata, or model-generated quotes.

The row is an Assistant-owned dependent, not a Retrieval aggregate. It has no
foreign key to canonical chunks. Database ownership is repeated in a composite
foreign key to `(message_id, organization_id, actor_user_id)`, deletion
cascades from the owning message, and `(message_id, citation_number)` is
unique. Any citation insert failure rolls back the answer; a failed, cancelled,
empty, or aborted stream creates neither answer nor mapping rows.

### Transcript-independent bounded hydration

Actor-owned history always returns without consulting OpenFGA. Citation
affordances hydrate separately and lazily for one visible assistant message at
a time, so a live authorization outage cannot hide the historical transcript.
The hydration boundary reads at most 100 stored references for that message,
deduplicates chunk IDs, resolves current scope/model once, and performs fixed
batches of at most 20 authorization checks. It never serializes a stored chunk
ID before authorization.

Determinate denied, missing, stale, or unavailable references are omitted and
their answer markers remain inert. An unresolved provider or policy response
returns `citation hydration unavailable`; it is never converted into an empty
denied set. Browser query caching and visible-message hydration prevent one
unbounded request per transcript or one OpenFGA request per citation.

### Separate excerpt and original-content reads

Add a bounded excerpt endpoint under the existing citation API. It reuses the
lower canonical authorization boundary but owns distinct final allow/deny
audits. It returns at most 4,000 Unicode code points plus `truncated`, title,
heading, page range, and a closed server-derived presentation kind. It excludes
source URI, object key, stored MIME, and other identifiers; sends `no-store`
and `nosniff`; and maps missing, denied, stale, or unavailable evidence to the
same opaque not-found response.

The existing content endpoint remains the only original-byte path. It keeps
revision/blob integrity verification, `nosniff`, and filename-derived safe
representation. Browser or connector MIME claims cannot widen renderability.
The presentation kind is derived from the server's closed filename policy and
is one of `PDF`, `MARKDOWN`, `PLAIN_TEXT`, `IMAGE`, or `DOWNLOAD`; Markdown is
never inferred from Blob MIME or a display title.

### Restricted rendered Markdown

When the canonical filename is `.md`, the source dialog offers `Rendered` and
`Raw` views. `Rendered` uses a dedicated restricted Streamdown composition,
not the Assistant answer renderer unchanged. It escapes embedded HTML, keeps
sanitization, disables automatic remote images/resources and Mermaid/custom
renderers, and retains a confirmation boundary for permitted outbound links.
The raw view preserves exact text for inspection and copy.

Markdown images and HTML media elements render as inert placeholders. Safe
links require explicit navigation, allow only the closed protocol policy, and
use `noopener noreferrer`. Render failure falls back automatically to the raw
`<pre>` view. Browser proof includes hostile HTML, remote Markdown images,
`javascript:` and data/SVG URLs, Mermaid link directives, and a network
assertion that preview caused no attacker-origin request.

Markdown detection remains filename-derived from the same closed content-type
policy that chose `text/plain` delivery. A stored or connector-supplied MIME
value cannot turn an unknown document into rendered Markdown.

### Truthful pre-first-token activity

Begin the response stream before blocking retrieval and emit a small closed set
of typed, transient server activity events:

1. `RETRIEVAL / ACTIVE` — `Searching permitted knowledge…`;
2. `RETRIEVAL / COMPLETE` — `Found N permitted sources` using only evidence
   already authorized for the actor;
3. `GENERATION / ACTIVE` — `Preparing the grounded answer…`;
4. first text delta — remove or collapse the waiting activity while normal
   answer streaming continues.

The events are operational state, not chain-of-thought, not model reasoning,
and not durable transcript content. They use AI SDK transient `data-*` parts
and a compact text-only local status composition. The active phase uses the
existing reduced-motion-safe shimmer text with no OrgMemory logo, agent avatar,
network glyph, or leading status icon. Actual future model tool calls use
`tool-*` parts and the AI Elements `Tool` component with real input/output
state; server-side retrieval is not relabeled as a tool merely for visual
effect.

The stop control remains available during both request submission and
streaming. Abort, retrieval failure, provider failure, no-evidence completion,
and first-token arrival all clear or replace the active phase. After a measured
long-wait threshold, copy may say the operation is taking longer than usual,
but no percentage, synthetic step, or completion estimate is shown. ARIA live
announces phase transitions once and reduced-motion users receive no required
meaning through animation alone.

Blocking retrieval runs on an application-owned scheduler with configured
maximum concurrency, finite queue, generic overload rejection, and shutdown
disposal. The authenticated request thread resolves immutable `CurrentActor`
and model authority values before handoff. The worker restores observation
context explicitly, invokes the existing proxied synchronous retrieval method
so its transaction remains short and worker-bound, and never reads a thread-
local security principal.

Abort, actor/conversation change, disconnect, and turn timeout dispose and
interrupt the scheduled retrieval where supported, cancel downstream model
generation, and prevent transcript completion. Finite database/provider
timeouts remain authoritative where a blocking dependency ignores interrupt.
Queue overload follows the sanitized unavailable sequence and never leaves a
live SSE or spinner.

The transient protocol is a closed state machine carrying only phase, state,
and optional authorized evidence count; browser-owned copy maps those values
to text. It never carries a query, source identifier, model reasoning, or
arbitrary server prose. Explicit terminal sequences cover success, zero
evidence, retrieval failure/overload, provider failure/empty output, cancel,
and timeout; a late event can never resurrect cleared activity.

Turn observation begins at reactive subscription and stops exactly once.
Retrieval duration is recorded separately; time to first token means the first
model text token, not an activity event. Retrieval failure, model failure,
cancellation, empty output, and the server-owned no-evidence fallback record no
TTFT sample. A latch-controlled integration test must prove the client receives
stream start and `RETRIEVAL / ACTIVE` while retrieval is still blocked.

### Honest format capability

The exact evidence excerpt is text derived during governed ingestion and is
therefore independent of the original container format once a chunk exists.
Original-file behavior remains deliberately narrower:

| Original format | Direct upload today | Inline original preview | Planned action |
| --- | --- | --- | --- |
| PDF | yes | yes | evidence excerpt plus embedded PDF |
| Markdown | yes | yes, currently raw safe text | evidence excerpt plus restricted rendered/raw Markdown preview |
| TXT | yes | yes, as safe plain text | evidence excerpt plus text preview |
| DOCX, PPTX | yes | no | evidence excerpt plus original download |
| PNG, JPEG, GIF, WebP | no | safe delivery supported | evidence excerpt when indexed plus exact closed-allowlist image preview; add missing browser proof |
| XLSX, CSV, HTML, unknown | no closed upload support | no | no new support; attachment-only if canonical evidence exists |

Office inline rendering, arbitrary rich content, server-side conversion, OCR,
and spreadsheet ingestion are not implied by this increment.

## Strongest Counterargument

The live UI can be fixed without persistence. Adding message citation rows and
batch authorization to transcript replay increases data lifecycle, migration,
and coupling cost for links that may rarely be reopened. A citation marker
could remain plain after reload and the product could defer durable citation
continuity until user evidence proves it matters.

## Scope

Included:

- independent challenge of persistence, module ownership, and current-access
  semantics;
- minimal message-owned citation reference persistence;
- transcript-independent, lazy, bounded current-authorization hydration;
- currently authorized bounded evidence excerpt API;
- direct citation-to-evidence interaction and removal of the empty carousel;
- restricted rendered/raw Markdown preview without active HTML or automatic
  remote resource loading;
- typed transient retrieval/generation activity before first token, truthful
  stop/error behavior, and separate retrieval versus first-token timing;
- explicit preview versus download affordances;
- PDF, text, Office-download, image, reload, revocation, and two-user negative
  coverage;
- generated REST contracts plus Assistant and secure-retrieval documentation
  reconciliation.

Excluded:

- Office, spreadsheet, or HTML rendering, including embedded HTML inside
  Markdown;
- document conversion, OCR, annotation, page thumbnails, or search within
  source;
- persisting raw evidence text or source bytes with a conversation;
- model-generated citation summaries or quotes;
- changes to retrieval ranking, citation generation, or answer prompting;
- exposing chain-of-thought or presenting server retrieval as a model tool;
- Packs, tools, custom agents, attachments, or deep research.

## Decision And Rejected Alternative

The independent challenge returned `ACCEPT WITH MUST-FIXES`. Proceed only with
the Assistant-owned mapping, transcript-independent bounded hydration,
surface-specific excerpt audit, closed presentation kind, hardened Markdown,
and bounded cancellable activity scheduler above. The full verdict is recorded
in [challenge verdict](challenge-verdict.md).

Reject a live-only UI fix because citation mappings cannot be reconstructed
safely after reload. Reject eager history authorization because OpenFGA failure
must not hide an actor-owned transcript. Reject `Flux.defer` or the shared
elastic pool because neither establishes product-owned concurrency/overload
bounds. Reject fake `tool-*` retrieval because the model does not choose or
invoke the mandatory grounding operation.

Continuity means continuity across transcript reload while the canonical chunk
is still current and visible. A normal source revision may make an old mapping
inert. Immutable historical evidence access is a separate retention and
authorization design and is not claimed here.
