# Session Evidence — orgmemory-agent-debate

Sanitized summaries only. No transcripts, credentials, or personal data.

- **Slack-connector cluster (claude sessions, 2026-07-23,
  feat/slack-connector-live).** Two read-only sessions framed as
  "Architecture debate only; do not edit" argued a projection-publication
  design; a separate judge session then ran with the exact constraints this
  skill codifies: final judge in a two-architect debate, no file inspection,
  no tools, answer from the debate record, ending in a one-sentence final
  architecture decision.
- **LightRAG pr02 cluster (claude sessions in the orca workspace,
  2026-07-23).** Same shape independently: defender and "skeptical and
  concise" counterpart debating ProjectionPublicationStore semantics
  read-only, followed by a judge round.
- **claude bfc22d2b (2026-07-29, observability payload policy).** Cross-model
  debate: the user directed a discussion with Codex via orca cli and gave the
  standing correction this skill enforces — create a prompt file and tell the
  counterpart to read it instead of sending the prompt inline. The verdict
  was written to `tmp/obs-payload-policy-verdict.md`, and the user checked the
  file existed ("có rồi mà") — origin of the verify-the-file step.
- **codex 147d5354 (2026-07-15 → 07-26).** Orca terminal driving mechanics
  used repeatedly (`orca terminal wait --for tui-idle --timeout-ms 60000`,
  terminal read/send), the transport this skill uses for the counterpart.
- **codex 4003dcf7 and c4544da8 (2026-07-27 → 07-29).** The debate/discuss
  step is explicitly optional and skippable inside the delivery loop:
  "Claude is out of quota, you can skip the discuss step", "no need for the
  counterpart model yet, start implementing" — origin of the
  quota-unavailable fallback that must not block the loop.
