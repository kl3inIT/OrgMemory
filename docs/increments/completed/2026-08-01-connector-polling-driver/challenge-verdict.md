# Independent Architecture Challenge Verdict

Date: 2026-08-01

Reviewer: Claude Fable 5 in a separate Orca terminal, read-only. The reviewer
read the repository and pinned Onyx evidence after a transient Claude API
retry and returned an approximately 10,000-token adversarial verdict.

Reviewed proposal: `d1300c04` on baseline
`ddda13598e762391d291c24539e7040ee5576e6f`.

## Decision

**Approve with corrections.** The consolidation is justified because the
failure-policy and derived-client drift are real, but the original proposal
contained a guaranteed Drive cursor break, an undeclared activity-code change,
a cadence-retirement promise with no implementation surface, and an
unrecorded revocation-stall consequence of whole-connection aborts.

The accepted direction is a final template driver that owns connection
enumeration, credential-derived client lifecycle, cadence, threshold
admission, failure envelopes, retirement, and outer cursor scaffolding.
Adapters retain source calls, eligibility/failure counting, mapping,
completeness evidence, component cursor material, and historical cursor
prefixes.

## Strongest Counterargument

Keep adapters independent and extract only utilities. The sources genuinely
differ: Slack excludes `not_in_channel` from failures, Drive counts indexable
files, and GitHub has collaborator and content failure modes per repository;
their prechecks and completeness evidence also differ. A generic pair of
counts cannot prove an adapter counted correctly.

The reviewer rejected that alternative because optional utilities reproduce
the observed defect: GitHub omitted the abort, while Slack and Drive omitted
GitHub's cache even though shared `ContentCadence` already existed. The driver
must own whether counts are admitted; adapter-local tests must prove the counts
are honest.

## Repository Evidence

- Poll-loop duplication: `pendingBatches()` in the three connector batch
  sources under `integrations/connectors/src/main/java`.
- Abort drift: `SlackConnectorBatchSource.abortIfMostlyFailed` and
  `GoogleDriveConnectorBatchSource.abortIfMostlyFailed`; no GitHub equivalent.
- Cache drift: `GitHubConnectorBatchSource.clientFor` and `ClientContext`;
  Slack and Drive reconstruct clients every poll.
- Identical outer cursor algorithm: each source's `crawlCursor`; digest bytes
  come from `core/src/main/java/com/orgmemory/core/shared/Digests.java`.
- Prefix trap: `GoogleDriveSourceProfile.SOURCE_SYSTEM` is `google_drive`, but
  `GoogleDriveConnectorBatchSource.crawlCursor` prefixes `google-drive-`.
- Retirement gap: `ContentCadence` has two maps and no removal operation.
- Checkpoint/security semantics:
  `docs/specs/domains/knowledge-ingestion.md` and decision 0009's sealed
  authorization ceiling.
- Comparable runner/adapter boundary: pinned Onyx
  `backend/onyx/connectors/connector_runner.py`, `factory.py`, and
  `credentials_provider.py` at
  `618b5031bf21463f44e3bed9eb9d5073b806fec0`.

## Answers To The Challenge Questions

1. **Policy versus source semantics:** the split is honest only if the driver
   owns admission while the adapter owns the denominator and declares its
   expected exceptions. A pass-specific result is needed because Slack's
   content and permission passes count separately.
2. **GitHub abort:** missing abort is a defect. Count one repository at most
   once when collaborators or content throws. Do not count configured issue
   truncation, a content skip after that same collaborator failure, or
   incomplete source fields.
3. **Client cache:** typed tenant/source/connection identity plus credential
   and client-config revisions is sufficient if both caches and cadence are
   retired. The cached object necessarily extends credential-derived secret
   residency; fingerprints must never be logged.
4. **Cursor centralization:** safe only with an adapter-supplied historical
   prefix. Golden vectors must cover content and permissions-only component
   sets, enum rendering, natural ordering, and the empty-material case.
5. **SPI shape:** the abstract class preserves the public SPI and bean
   compatibility, although composition is marginally narrower. The template
   is acceptable only with final orchestration and narrow protected hooks.
6. **Mechanical proof:** the binding list below is required before the
   increment may be consolidated.

## Strongest Production Failure

For two GitHub repositories, one persistent collaborator refusal is exactly
50%. Rejecting the whole connection also rejects healthy-repository membership
updates, so a removed reader can retain retrieval through the previously
sealed membership head until the failing repository recovers. A one-repository
connection stalls on any refusal. This is accepted over checkpointing a
broadly degraded connection, but it is a security/operations tradeoff rather
than merely "more retries" and must remain visible as recurring
`UNAVAILABLE` / `mostly_failed` activity.

## Binding Must-Fix List

1. Supply the historical outer cursor prefix per adapter; never derive it from
   the source-system id.
2. Before refactoring production code, commit passing golden vectors for each
   connector's content and permissions-only passes, and keep them
   byte-identical after delegation.
3. Add cadence retirement for both due-time and served-request state; prove
   disabled/deleted eviction and clean recreation.
4. Pin GitHub's repository-at-most-once denominator and every exclusion with
   below/at/above-50% tests, including one-of-one and one-of-two failures.
5. Record and test the revocation-stall consequence and recurring operational
   failure visibility.
6. Split the existing single-repository incomplete-ACL test: below threshold
   still proves incomplete never becomes empty-authoritative ACL; at threshold
   proves no batch plus `mostly_failed`.
7. Either preserve Slack/Drive's old generic abort codes or explicitly declare
   the standardized `mostly_failed` activity code. The revised design declares
   it, so consolidation must reconcile it.
8. Prove client reuse, credential/config rotation, missing/disabled eviction,
   Drive access-token refresh, and absence of credential/fingerprint material
   from diagnostics.
9. Unknown runtime exceptions must propagate rather than becoming a
   `ConnectorConnectionFailure`; record that cached clients retain
   credential-derived material for their lifetime.
10. Consolidate the knowledge-ingestion spec/test pair, including refreshed
    `Source:` / `Reconciled:` lines, in the follow-up documentation PR.

## Rejected Alternative

Narrow optional utilities were rejected because they cannot prevent the exact
policy omissions already observed. A composition-based driver is not rejected
architecturally; it is the fallback if an abstract template would require
broad protected source semantics or break the existing direct test seams.
