# Assistant Conversation Memory SSOT Verification

Date: 2026-08-06

## Delivered behavior

- One tenant-and-actor-owned transcript is the only persisted home of an
  Assistant conversation. `spring_ai_chat_memory`, `MessageWindowChatMemory`,
  `ChatMemoryRepository`, the `ChatMemory` bean, `ObservedChatMemory` and the
  JDBC chat-memory starter are gone.
- Each turn carries an explicit identity allocated when its question is
  persisted and carried through to its answer. A partial unique index over turn
  and role holds one question and one answer per turn. Rows predating the
  migration keep a null turn, stay in the transcript, and are never model
  context.
- A project-owned read-only advisor reads the last ten completed turns before
  each model call, scoped by organization as well as conversation. Nothing on
  the model path writes to the transcript.
- Reading whole turns rather than counting messages excludes the question of the
  turn in flight, excludes turns that failed before answering, and cannot leave
  the window opening on an answer.
- Deleting a conversation is one owned call relying on the existing cascade,
  not a domain delete plus an unscoped store clear outside the transaction.
- A failed turn ends on a sentence naming its cause: saturation read from the
  bounded failure code, everything else from the leading HTTP status, every
  sentence fixed so no provider text reaches the browser.
- An unavailable turn is tagged with `failure_code` and logged at `WARN` with
  its cause.

## Gates

- Independent architecture challenge run as a two-architect debate with a
  no-tools judge; brief and verdict recorded in this directory. The winning
  position won conditionally and its six binding constraints are in `design.md`.
- Spring AI 2.0.0 `BaseAdvisor`, `BaseChatMemoryAdvisor` and
  `MessageChatMemoryAdvisor` verified against the sources in the Gradle cache
  before the replacement was written.
- `./gradlew :core:test`: passed.
- `./gradlew :integrations:ai-model-gateways:test`: passed.
- `./gradlew :apps:api:test`: passed, 244 tests.
- `contracts/openapi.json` regenerated for the changed delete-endpoint summary
  and `OpenApiContractTests` re-run green.
- Web Oxlint, TypeScript project build, and production build: passed. No
  frontend source changed in this increment.
- Deployed to ZM and verified after the turn-identity half: schema at version
  26, `ddl-auto=validate` clean, zero API errors, `failure_code` publishing both
  `none` and `assistant_turn_failed` to Prometheus.

## Measured before deciding

546 conversations on the deployment: 514 matched `memory = LEAST(transcript,
20)` exactly, 31 were explained by a failed or model-free turn, one was
unexplained, and none held more model memory than transcript. The two stores
were not drifting. The earlier "20 orphaned conversations" drift claim was
wrong and is withdrawn; the case for collapsing rests on the second store being
derivable, not on it being broken.

## Not verified

- The failure sentences are not exercised against a real failing gateway.
  Doing so needs a production model route or gateway credential change, which is
  disproportionate to the risk of a fixed string chosen by status code.
- Two turns of one conversation completing at the same instant still raise an
  optimistic-locking failure through the unlocked conversation touch in
  `completeTurn`. Pre-existing, unrelated to this increment, recorded as a gap
  in the Assistant test matrix.
- The one unexplained conversation from the measurement above has a proposed
  mechanism but no proven diagnosis. Collapsing removes the class; it does not
  explain that row.
