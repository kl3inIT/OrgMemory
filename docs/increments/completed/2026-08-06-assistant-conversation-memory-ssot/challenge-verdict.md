# Verdict — Chat Transcript SSOT

Judge: independent, non-participant. Decided on the debate record alone
(brief, both round-1 files, the round-2 instruction file with the moderator's
measured evidence, and both round-2 files). No repository inspection was
performed and no claim was independently verified.

---

## 1. Decision

**Collapse to one store: `assistant_conversation_messages` becomes the sole
persisted store for both the product transcript and the model context, and
`spring_ai_chat_memory` is dropped — Position A wins, subject to the binding
constraints in §4.**

---

## 2. Rationale

### 2.1 Position B's own round-2 reframing dissolves Position B

B's round 2 does not defend the architecture B argued in round 1. It states:

> "**No meaningful value remains in Spring AI's stock JDBC repository or
> dialect.** A custom repository should replace them; implementing both a custom
> repository and a custom dialect would be redundant because the upstream binder
> cannot supply the new tenant values. **That part of A's attack succeeds.**"

and concludes:

> "Keep two stores, but **stop calling Table B an upstream-owned store. It is a
> project-governed, tenant-linked, disposable projection** of completed
> model-memory lifecycle."

Round 1 rested Position B on two pillars: a genuine semantic boundary, and
upstream compatibility. The second pillar is explicitly surrendered. What
remains is a self-described *disposable projection* — B's word — of table A,
requiring a project-owned repository, a project-owned dialect-equivalent, a
project-owned transaction wrapper, a project-owned lock, a key-type change, two
new columns, a composite FK, a backfill of untenanted rows, and two new
integration test classes. That is a large, permanently owned persistence surface
whose entire purpose is to hold a derivable projection. A projection with no
independent source of truth belongs in a query, not in a table.

The residual upstream value B enumerates is honest and correctly scoped —
"the `ChatMemory` SPI", "`MessageWindowChatMemory`'s bounded-window algorithm",
"`MessageChatMemoryAdvisor`'s prompt integration", "Spring AI `Message` types" —
but B itself labels it: "That is **algorithm and integration reuse, not
persistence reuse**." Algorithm reuse does not require a second table. It is
precisely what A proposed to keep as a read policy.

### 2.2 The moderator's measurement is the load-bearing fact, and it cuts for A

The measurement refuted part of the brief (drift is ~0.5%, not endemic) and
refuted B's round-1 causal hypothesis outright. B conceded this: "I withdraw my
round-1 explanation of the measured zero-memory conversations."

The decisive shape of that data is not the divergence count but its *absence*:

> `memory_rows = LEAST(transcript_rows, 20)` — 514/546 conversations;
> `memory_rows > transcript_rows` — **0**.

A store whose population is reproducible by a bounded expression over another
store, with no row the other store lacks, is a cache. B was entitled to argue
that the split was a boundary; the data says it is a projection. A framed this
correctly: "I would have accepted 'the two stores drift' as evidence for A; what
I actually got is stronger: the second store is a pure function of the first."

Critically, B accepted the acceptance condition *in round 1*, before seeing the
data:

> "If the desired product rule is ultimately 'model context is always a pure
> deterministic window over committed product transcript rows,' a second physical
> store would be unjustified."

The measurement then showed it already is that, in 514 of 546 conversations, with
the remainder explained by `memory = transcript − failed_turns`. B's round-2
attempt to keep the boundary alive rests on reinterpreting the residue —
"model-invocation admission and failed-turn omission are the stronger
persistence-boundary argument." But admission and omission are *selection
predicates*. A's completed-turn window rule (round 2, Q4, rule 2: "Drop every
USER row that is not immediately followed — in `sequence_id` order — by an
ASSISTANT row") implements exactly that selection as a read policy. B named a
filter and called it a boundary.

### 2.3 B's round-2 change inventory concedes the architecture while disputing the cost

The most telling artifact in the record is B's own "Viable A" inventory. It
proposes:

> "Add `.../TranscriptContextAdvisor.java`: read completed transcript turns
> before the prompt but never call `ChatMemory.add`; this avoids A's impossible
> verified-duplicate timing."

That is a complete, workable single-store design — authored by the losing side.
B's remaining disagreement is therefore not about *whether* one store can serve
both roles; B has designed the mechanism by which it does. B's disagreement is
about A's *cost estimate* ("A can be smaller only by accepting incorrect pairing
and the broken guarded ASSISTANT add"). On that narrower point B is right, and
§4 makes it binding. But a cost correction is not a defeat of the architecture.

### 2.4 Tenancy and deletion decide the tie-break that remains

Both sides agree table B holds message content with no tenant, no actor, and no
foreign key, deleted by a second non-atomic call from the delivery layer. B
agrees the fix requires forking vendor persistence. Under A, the content lives in
exactly one table already inside the tenancy model, and deletion becomes the
owned parent delete plus `ON DELETE CASCADE` — A's round 2: "`AssistantController`
line 380 removed; FK cascade only." B reaches the same deletion end-state, but
only after building the FK, the key conversion, the backfill and the lock first.
Where two designs converge on the same invariant, the one that reaches it by
deleting a table beats the one that reaches it by building a parallel persistence
stack.

---

## 3. The rejected alternative, in its strongest form

Position B, at its best, is this:

*The boundary is not transcript-vs-copy; it is **what the product recorded**
versus **what the model was actually given**. Those are different facts about the
world. A turn that errored, was cancelled, or never invoked a model is a real
event in the transcript and a non-event in the model's context. The measurement
proves the distinction is live and stable: the residue is not noise, it is exactly
`transcript − failed_turns`, i.e. the model-invocation lifecycle faithfully
recorded. Materializing that lifecycle gives you an auditable record of what the
model saw, independent of inference; it lets model context have its own retention
and reset clock; and it leaves room for future model-only state (tool results,
durable system context) that table A's `CHECK (role IN ('USER','ASSISTANT'))`
forbids. Collapsing replaces a recorded fact with a derived one, and derivations
silently change whenever anyone edits the query.*

**What would have made it win.** Any one of:

1. A written retention or erasure requirement with two genuinely different clocks
   — A conceded twice that this is "the only argument I could not answer with a
   read policy over table A."
2. A concrete, near-term requirement for durable SYSTEM or TOOL content in the
   model window. Both sides agreed the current absence is structural, so this
   needed to be a stated product requirement, not a possibility.
3. A demonstration that "what the model saw" must be independently auditable —
   e.g. a compliance or evaluation requirement — rather than reconstructible.
4. A second `ChatMemory` consumer outside the assistant module, which would make
   the coupling of the model window to `AssistantConversationService` a real cost.

B produced none of these. B's round-2 case reduces to concurrency safety and
turn-identification — both of which are implementation defects in A, correctable
without a second table, and both of which are now binding constraints below.

---

## 4. Binding constraints — what Position A conceded, which the implementation MUST honor

These are not footnotes. A won on the boundary and lost on much of the execution.
The design that ships is A's boundary with B's corrections.

**C1 — The no-op `add()` is dead, and so is the "guarded duplicate" `add()`.**
A withdrew the no-op in round 1 ("A no-op `add()` is genuinely bad, and B is
right to name it"). B then killed the replacement in round 2, unrefuted, because
A's round 2 was written in parallel and never answered it:

> "Spring AI invokes the advisor's after-write from the aggregation callback …
> while OrgMemory writes the transcript answer only later in the controller's
> downstream `doOnComplete`. A's guarded ASSISTANT add therefore **either rejects
> every healthy completion or writes before the alleged canonical writer.**"

This attack stands. The implementation must therefore **not** retain
`MessageChatMemoryAdvisor`'s write path. Adopt B's own proposal: a project-owned,
**read-only** context advisor that assembles the bounded window before the prompt
and never calls `ChatMemory.add`. If a `ChatMemory` bean is retained at all, its
`add` must be an explicit, telemetered rejection — never a silent no-op, and
never a "verify it was already written" check whose precondition cannot hold.

**C2 — Turn identity must be explicit; role + `sequence_id` is not sufficient.**
B's unrefuted attack:

> "Table A has role and a global sequence but no `turn_id` or completion status …
> `beginTurn` and `completeTurn` are separate transactions … Concurrent calls can
> therefore produce `U1,U2,A2,A1`. Neither 'drop a trailing USER' nor 'pair each
> USER with the next ASSISTANT' identifies the true turns."

A half-conceded this by making it falsifier #4 and by admitting the inference "is
sound only because `beginTurn` and `completeTurn` are the sole writers … A
reviewer should treat that as an invariant to guard." Sole-writer-ness does not
imply serialized ordering, so the concession does not cover the concurrency case.
Ship a `turn_id` (per B's `V24` sketch: nullable, backfilled only for unambiguous
adjacent legacy pairs, with partial uniqueness for one USER and one ASSISTANT per
turn), or prove a one-turn-in-flight invariant with a test that reproduces
`U1,U2,A2,A1`. A's "~10 lines" estimate is withdrawn by this constraint.

**C3 — `clear()` must not delete the transcript and must not require ambient
identity.** A withdrew the brief's shape entirely: "`clear()` as written in the
brief is wrong, and I withdraw it entirely." The replacement is the
`memory_reset_sequence_id` watermark on `assistant_conversations`, with `get()`
reading only `sequence_id > memory_reset_sequence_id`. That watermark column is
part of the migration, not optional. Any implementation requiring a `CurrentActor`
inside `clear(String)` is out of contract and must fail test T-C2.

**C4 — The current in-flight USER must be excluded structurally, not by timing
accident.** A conceded the duplicate-USER defect in full ("B's mechanism is
exactly right"). The window contract must exclude any USER row lacking an
ASSISTANT successor, so exclusion follows from the query definition rather than
from write ordering. Test T-G2 (assert the captured prompt is exactly
`[System, U1, A1, U2]`, U2 appearing once) is the gate.

**C5 — Snap-forward is not a benefit of collapsing.** A conceded in round 1 that
Spring AI 2.0.0 already snaps the window head to a USER message. Retain the
behavior for parity; claim no credit for it.

**C6 — Drift was never the case for A, and must not be cited as one.** A
retracted the "~97 orphaned USER rows in live model windows" attack: "those 97
orphaned USER rows are present in table A too — the imbalance is a property of the
turn lifecycle, not of the second store." Orphan replay is a shared read-policy
defect; C4's rule fixes it under either architecture. The consolidation write-up
must not claim collapsing cured a drift problem that measurement showed was
~0.5%.

**C7 — Cost honesty.** A conceded "'smaller patch' is not the same as 'cheaper to
undo'", and B's inventory is the better cost estimate. Plan for a migration
(turn_id + watermark + drop, with the drop split into a separate migration for
rolling deployment as B proposes), service and controller changes to carry the
turn id, a new context advisor, adapter rewiring, dependency and
`application.yml` cleanup, and the full test set — not "one migration, one
~70-line class."

**C8 — Deletion via cascade, one command.** Both sides converged: the second
`memory.clear` call in the controller disappears; owned parent delete plus
`ON DELETE CASCADE` on the conversation FK is the whole deletion path. Note A's
own round-2 correction that only the conversation FK cascades — the `app_users` FK
does not — so every cascade claim must route through the conversation FK.

**C9 — Record the decision.** A's unrebutted point that `docs/decisions/` holds
nothing on this boundary, and that the split rests on a two-line SQL comment,
applies equally to the new state. This debate is the required architecture
challenge; it must be written up as a decision, with B's position recorded as the
rejected alternative in the form given in §3.

---

## 5. Open questions the record did NOT settle

1. **The ~3 unexplained conversations.** Two incompatible mechanisms were
   proposed and neither was tested. A argues the moderator's refutation is a
   measurement artifact, because the no-evidence sentences were rewritten on
   2026-08-05 by `145a27b6`, one day before measurement, so "essentially the
   entire production dataset predates the current wording," and predicts all three
   match the pre-`145a27b6` string. B proposes a lost whole-window update from two
   concurrent `add` calls racing `saveAll`'s delete-then-insert, and explicitly
   declines to explain the two `2/1/0` cases: "Plausible classes are a later stale
   replacement/clear, an older deployed code/configuration, or external/manual
   mutation. The counts alone cannot distinguish them." **The record is
   insufficient here.** Run A's proposed query (re-match the three conversations,
   including the historical no-evidence wording, and note that the `6/3/4` case was
   never in the tested population at all) before implementation. If the match
   fails, an unidentified writer of ASSISTANT transcript rows exists and must be
   found first — under a single store that writer becomes a correctness problem,
   not a reconciliation problem.
2. **Concurrency semantics of the winning design.** B's lost-update mechanism was
   argued against table B, but the analogous question for table A — what happens
   when two turns of one conversation overlap — is unanswered by either side. C2
   makes turn identity mandatory; it does not define the intended behavior. Decide
   explicitly: reject concurrent turns, serialize them, or define a deterministic
   window under interleaving.
3. **What replaces `MessageChatMemoryAdvisor`'s streaming aggregation.** B listed
   "streaming aggregation" among the upstream value worth preserving. C1 removes
   the advisor's write path. Whether any aggregation behavior is lost, and whether
   a read-only advisor can sit at the same order relative to `ToolCallingAdvisor`,
   is unexamined in the record.
4. **SYSTEM and TOOL persistence.** Both sides agree the current absence is
   structural (vendor filtering plus advisor nesting at order 200 vs 300), and
   both agree neither architecture supports durable tool state today. Neither
   established whether it will be needed. A conceded the need "is real and A does
   not get it for free" — costing a role CHECK migration. Left open.
5. **Retention.** No written requirement for a separate model-context retention
   clock was produced by either side, and A named it as the one argument it could
   not answer. If such a requirement exists anywhere in product or compliance
   scope, it should be surfaced before the drop migration, not after.
6. **Invisible memory reset.** A's own falsifier #5: the watermark makes a reset
   inferable from the transcript. If the product requires a model-context reset
   that leaves no trace visible to a user or admin, C3's design does not provide
   it and needs revisiting.
7. **Backfill and legacy pairing.** B's inventory requires deciding what happens
   to legacy rows whose turns cannot be unambiguously paired ("leave ambiguous
   legacy rows visible in transcript but ineligible for model context"). The "no
   backfill obligation" premise came from the brief and was flagged by B as not
   repository-verifiable; confirm it against the migration conventions before
   relying on it.
8. **Guarding the writer invariant.** A proposes an ArchUnit or module-visibility
   test to prevent a third writer of `assistant_conversation_messages`. With
   `turn_id` (C2) the inference is less fragile, but whether the guard is still
   required was not settled.
