# Knowledge Ingestion Coverage

Evidence class: `apps/api/src/test/java/com/orgmemory/api/knowledge/KnowledgeIngestionIntegrationTests.java`.

| Behavior | Automated evidence |
| --- | --- |
| Idempotent ingestion and copied security lineage | `ingestionIsIdempotentAndPromotionCopiesSecurityLineage` |
| Same source revision cannot change content | `sameSourceRevisionWithDifferentContentIsRejected` |
| Incomplete ACL/classification mismatch quarantine | `incompleteAclAndClassificationMismatchAreQuarantined` |
| ACL rotation append/head/idempotency | `rotatingSourceAclAppendsEvidenceAdvancesHeadAndIsIdempotent` |
| Sealed ACL database immutability | `databaseRejectsMutationOfSourceAclEvidence` |
| Unmapped source groups deny | `completeAclRejectsUnmappedSourceGroup` |
| URI credentials/query/fragment safety | `sourceUriDropsQueryAndFragmentBeforePersistence` |
| Stale expected heads reject | `sourceRevisionAndAclRotationRejectStaleExpectedHeads` |
| Refresh-window validity | `completeAclRejectsValidityBeyondRefreshWindow` |
| Concurrent retries converge | `concurrentRetriesConvergeOnOneRawNormalizationAndAsset` |

## Connector Staging Coverage

Evidence classes: `core/src/test/java/com/orgmemory/core/knowledge/ConnectorIngestionServiceTests.java`,
`apps/worker/src/test/java/com/orgmemory/worker/connector/ConnectorStagingIngestionIntegrationTests.java`,
`apps/worker/src/test/java/com/orgmemory/worker/connector/FileConnectorBatchSourceTests.java`.

| Behavior | Automated evidence |
| --- | --- |
| Unknown payload version fails closed before any work | `unknownPayloadVersionFailsClosedBeforeAnyWork` |
| Unsupported system / unknown org / inactive actor rejected | `unsupportedSourceSystemIsRejected`, `unknownOrganizationIsRejected`, `inactiveActorIsRejected` |
| Per-object failure isolated from the rest of the batch | `perObjectFailureIsIsolatedFromTheRestOfTheBatch` |
| Fixtures deserialize into the contract in filename order | `readsCommittedFixturesInFilenameOrder` |
| Crawl grants only mapped members; mapped non-member and unobserved principal denied | `slackChannelBecomesGovernedAndConvergesOnMembership` (initial phase) |
| Membership re-crawl converges grant/revoke without re-materializing content (same revision + chunks, ACL generation 1→2) | same test (re-crawl phase) |
| Tombstone archives the object out of retrieval | same test (tombstone phase) |
| Email join needs the source or an administrator to vouch; neither leaves it unmapped | `emailJoinNeedsSomebodyToVouchForTheAddress`, `emailJoinBindsWhenTheSourceVouchesForThePrincipal`, `emailJoinBindsWhenAnAdministratorAttestedTheConnection` |
| An attested connection opens a sealed grant that an unvouched crawl left closed | `administratorTrustDecisionOpensAnUnvouchedEmailJoin` |
| An edited message stops answering from the superseded text | `anEditedMessageStopsBeingAnsweredFromTheTextTheCrawlFirstSaw` |
| A retired object refuses a later content revision | `aRetiredObjectIsNotRevivedByALaterContentRevision` |
| A restarted driver resumes from the checkpoint instead of replaying | `aRestartedDriverResumesInsteadOfReplaying` |
| A permanent rejection is checkpointed past; a transient one is retried and left pending | `aRejectedBatchIsCheckpointedPastRatherThanRetriedForever`, `aTransientFailureIsRetriedAndStaysPending` |
| A complete crawl retires what it stopped mentioning | `aCompleteCrawlRetiresWhatTheSourceNoLongerHas` |
| An incomplete crawl, and a complete crawl that enumerated nothing, retire nothing | `anIncompleteCrawlRetiresNothingItSimplyDidNotMention`, `aCompleteCrawlThatEnumeratedNothingIsRefused` |

| A refusal inside a 200 response is read as a refusal; pagination ends only on an empty cursor | `readsARefusalOutOfASuccessfulResponse`, `collectsEveryPageUntilTheCursorRunsOut`, `treatsAnAbsentCursorAsTheLastPage` |
| A rate limit is waited out before the next request, with jitter, and gives up when it outlasts the budget | `waitsOutARateLimitAndRetriesTheCall`, `appliesAWaitEarnedByOneCallToTheNext`, `spreadsResumingRequestsWithJitter`, `givesUpWhenTheRateLimitOutlastsTheRetryBudget` |
| The token reaches the client and no failure message, properties description, or URI | `refusesAConnectionItHasNoCredentialFor`, `keepsTheTokenOutOfItsOwnDescription` |
| A workspace becomes threads, members, and channel groups on the crawl contract | `turnsAWorkspaceIntoTheCrawlContract`, `reportsMembersAsUsersAndTheChannelAsTheirGroup`, `dropsBotsDeactivatedAccountsAndChannelNoise` |
| Completeness is claimed only by an unfiltered, uninterrupted, in-scope crawl | `claimsCompletenessOnlyForAnUnfilteredUninterruptedCrawl`, `withdrawsTheCompletenessClaimWhenOnlySomeChannelsWereAskedFor`, `withdrawsTheCompletenessClaimWhenAChannelCouldNotBeRead`, `withdrawsTheCompletenessClaimWhenPrivateChannelsAreOutOfScope` |
| The adapter is absent until enabled and inert until fully configured | `contributesNothingUntilTheConnectionIsEnabled`, `staysInertWhenEnabledButNotFullyConfigured`, `producesNothingUntilTheConnectionIsFullyConfigured` |

Gaps: there is no public upload/connector REST API or a real blob-store/scan/parser
integration test yet (the connector proofs mock object storage and OpenFGA). The
Slack adapter is proved against recorded responses only — no run against a real
workspace has happened yet, and the slim ID-and-ACL pull and whole-crawl failure
threshold are unbuilt. Those are the remaining `slack-connector-live` work.
