# Assistant Skill Activity Receipt Challenge Verdict

Date: 2026-08-06
Commit reviewed: `438cf197`
Reviewer: Claude Fable 5, independent read-only session

## Verdict

`REVISE` — retain the proposed named current-turn receipt and visible-output
latch, but do not implement the original draft without the safeguards below.

## Strongest attack

Skill titles are direct-published, publisher-controlled strings up to 256
characters. Rendering one under server voice after a model-selected activation
would spend the existing closed-world activity boundary and could turn an
unreviewed title into deceptive UI copy. A second correctness gap is that the
12-call tool budget permits multiple activations, while the current activity
protocol has no correlation and cannot truthfully attribute resource activity.

## Committed recommendation

1. Enforce a short plain-text Skill display title in the activity record itself:
   trim, remove control characters, cap at 80 characters, and reject blank.
2. Emit the title only on successful actor-authorized activation. Discovery and
   every failure remain unnamed.
3. Give activation attempts a positive turn-local ordinal and use it to
   attribute later resource states for the successfully activated exact release.
4. Key the client latch by a turn token. Visible output, transport error, abort,
   stop, actor/conversation change, and finish-without-output are explicit
   terminal states; late activity cannot resurrect a terminal turn.
5. Keep receipts current-turn-only. Replay/reconnect reconstructs no receipt,
   and missing transient phases never imply completion.
6. Reconcile the spec/test matrix and supersede the no-identity activity rule in
   a decision entry.

## Scope limits

- No durable receipt schema, replay, revocation, or retention policy.
- No Skill instructions, resource path/content, tool input/output, raw error,
  asset/release ID, or reasoning reaches the receipt.
- A receipt proves tool use, not answer correctness or expanded authority.

## Rejected alternative

A generic title-free `Used a governed Skill` receipt avoids the disclosure
change but fails the product need and retains almost all state-machine
complexity. Durable receipt persistence is also rejected for this increment.
