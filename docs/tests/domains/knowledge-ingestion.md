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
| A batch that reconciled leaves a row with what it changed | `aSuccessfulBatchIsRecordedWithWhatItChanged` |
| A connection that produced no batch is recorded with the source's own reason, and nothing is marked done | `aConnectionThatProducedNoBatchIsRecordedWithItsReason` |
| Only what an adapter contributed is governed: an uncontributed source is refused, and two adapters cannot claim one name | `ConnectorSourceRegistryTests` |
| A database that already holds evidence survives the split of `source_type` into `acl_authority` and `source_system` | `SourceObjectAclAuthorityMigrationTests.anExistingObjectKeepsItsSystemAndGainsTheRightAuthority` |
| A complete crawl retires what it stopped mentioning | `aCompleteCrawlRetiresWhatTheSourceNoLongerHas` |
| An incomplete crawl, and a complete crawl that enumerated nothing, retire nothing | `anIncompleteCrawlRetiresNothingItSimplyDidNotMention`, `aCompleteCrawlThatEnumeratedNothingIsRefused` |

## Slack Adapter Coverage

Evidence classes under `integrations/connectors/src/test/java/com/orgmemory/connectors/slack/`:
`SlackWebApiClientTests`, `SlackConnectorBatchSourceTests`, `SlackTextCleanerTests`,
`SlackConnectorAutoConfigurationTests`.
All run against recorded Slack responses; none touches the network.

| Behavior | Automated evidence |
| --- | --- |
| A refusal inside a 200 response is read as a refusal; pagination ends only on an empty cursor | `readsARefusalOutOfASuccessfulResponse`, `collectsEveryPageUntilTheCursorRunsOut`, `treatsAnAbsentCursorAsTheLastPage` |
| A rate limit is waited out before the next request, with jitter, and gives up when it outlasts the budget | `waitsOutARateLimitAndRetriesTheCall`, `appliesAWaitEarnedByOneCallToTheNext`, `spreadsResumingRequestsWithJitter`, `givesUpWhenTheRateLimitOutlastsTheRetryBudget` |
| The token reaches the client and no failure message or URI | `refusesToCrawlWithACredentialSlackRejects` asserts the refusal carries the error code and not the token |
| A workspace becomes threads, members, and channel groups on the crawl contract | `turnsAWorkspaceIntoTheCrawlContract`, `reportsMembersAsUsersAndTheChannelAsTheirGroup`, `dropsBotsDeactivatedAccountsAndChannelNoise` |
| Completeness is claimed only by an unfiltered, uninterrupted, in-scope crawl | `claimsCompletenessOnlyForAnUnfilteredUninterruptedCrawl`, `withdrawsTheCompletenessClaimWhenOnlySomeChannelsWereAskedFor`, `withdrawsTheCompletenessClaimWhenAChannelCouldNotBeRead`, `withdrawsTheCompletenessClaimWhenPrivateChannelsAreOutOfScope` |
| Slack markup leaves no identifiers or raw tags in the indexed body | `leavesNoSlackMarkupBehindInARealisticMessage`, `resolvesMentionsAndLinksOutOfTheIndexedBody`, and the rest of `SlackTextCleanerTests` |
| A thread broadcast back to its channel is indexed once, whole | `indexesAThreadOnceWhenAReplyWasBroadcastBackToTheChannel` |
| A rejected credential and a mostly-unreadable workspace fail rather than report a crawl | `refusesToCrawlWithACredentialSlackRejects`, `abandonsARunInWhichMostChannelsCouldNotBeRead` |
| Between content crawls no message body is read, and the cheap batch never claims completeness | `readsNoMessageBodiesBetweenContentCrawls`, `aPermissionsCrawlNeverClaimsCompleteness`, `reissuesAContentCrawlOnceTheIntervalElapses` |
| A permissions crawl omits objects whose channel it could not see rather than asserting nobody may read them | `aPermissionsCrawlLeavesOutObjectsWhoseChannelItCouldNotSee` |
| The adapter is present wherever the module is and crawls nothing until a connection says so | `contributesTheAdapterWhereverTheModuleIsPresent`, `contributesNothingToCrawlUntilAConnectionIsEnabled`, `producesNothingUntilAConnectionIsEnabled` |
| A connection with no stored credential produces nothing and says why, rather than being skipped silently | `reportsAConnectionWithNoStoredCredentialRatherThanSkippingItSilently` |
| A configuration change is picked up on the next poll, without a restart | `picksUpAConfigurationChangeWithoutARestart` |
| One unusable workspace does not cost the others their poll, and is still reported | `oneUnusableWorkspaceDoesNotCostTheOthersTheirPoll` |

## Connection Administration Coverage

Evidence classes: `core/src/test/java/com/orgmemory/core/shared/secret/SecretCipherTests.java`,
`apps/api/src/test/java/com/orgmemory/api/admin/ConnectorAdminIntegrationTests.java`.
`SourceConnectionAdminService` has no unit test of its own; it is proved through
the API boundary, which is the only way it is reached.

| Behavior | Automated evidence |
| --- | --- |
| Only an organization administrator reaches any connector endpoint, and a refused request creates nothing | `nonAdministratorsAreRefusedEverywhere` |
| A stored token is unreadable in its own row and is returned by no endpoint, in any form | `aStoredTokenIsEncryptedAndNeverComesBack` |
| What comes out of the cipher is what the administrator put in | `testingAStoredTokenRoundTripsItThroughEncryption` |
| A tampered ciphertext is refused rather than decrypted | `refusesATamperedRowRatherThanDecryptingIt` |
| Crawl settings round-trip, and enabling with nowhere to publish is refused | `aCrawlIsConfiguredAndReadBack`, `enablingACrawlWithNowhereToPublishIsRefused` |
| A probe reports the workspace it authenticated as and never repeats the token | `testingATokenReportsTheWorkspaceItAuthenticatedAs` |
| Testing a connection with nothing stored answers rather than fails | `testingAConnectionWithNothingStoredSaysSoRatherThanFailing` |
| Every mutation leaves an audit event recording that a token was set, not the token | `everyMutationLeavesAnAuditEvent` |
| Only the sources this deployment installed are offered, and naming another is a request error rather than an empty list | `reportsOnlyTheSourcesThisDeploymentCanActuallyIngest`, `refusesASourceNoAdapterInstalled` |
| A connection that looks healthy reports why it is producing nothing | `reportsWhyAConnectionThatLooksHealthyIsProducingNothing` |

Gaps: there is no real blob-store/scan/parser integration test yet (the connector
proofs mock object storage and OpenFGA). The Slack adapter is proved against
recorded responses only — no run against a real workspace has happened yet, which
is the remaining `slack-connector-live` work. The administration screens have no
browser test; their proofs are at the API boundary, and the catalogue, the field
descriptor and the connection detail page have no automated proof at all — they
are covered by lint, typecheck and build only. Only one migration is proved
against a database that already holds rows; the rest are proved against an empty
schema, where a data-transforming statement cannot fail.
