# Knowledge Ingestion Coverage

Source: `core/src/test/java/com/orgmemory/core/knowledge`,
`apps/api/src/test/java/com/orgmemory/api/knowledge`,
`apps/api/src/test/java/com/orgmemory/api/source`,
`apps/web/src/features/sources`, `apps/web/test/e2e`,
`apps/worker/src/test/java/com/orgmemory/worker/ingestion`,
`apps/worker/src/test/java/com/orgmemory/worker/connector`,
`components/graph-rag-core/src/test/java/com/orgmemory/graphrag/parsing`,
`components/graph-rag-core/src/test/java/com/orgmemory/graphrag/chunking`, and
`integrations/document-parsing-spring-ai/src/test`,
`integrations/connectors/src/test`.

Reconciled: `2026-08-10-ingestion-coverage (c0c12728)`.

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
| Citation evidence read maps only a tenant-scoped ready matching revision plus validated blob into immutable metadata | `SourceCitationEvidenceQueryTests` |

## Versioned Processing Policy Coverage

Evidence classes: `RequestedProcessingPolicyTests`,
`DocumentProcessingEngineTests`, `SourceIngestionJobProcessingProfileTests`,
`SourceProcessingProfileMigrationTests`, `SourceIngestionPipelineIntegrationTests`, `TypedBlockParsingTests`,
`PdfPageProvenanceTests`, `ParagraphSemanticChunkerTests`,
`SupportedFormatParsingTests`, and `XhtmlBlockHandlerTests`.

| Behavior | Automated evidence |
| --- | --- |
| The default is the complete named `structured-block-v1` snapshot and output-affecting option changes alter its SHA-256 | `defaultsToTheVersionedStructuredBlockPolicy`, `changesIdentityWhenAnOutputAffectingOptionChanges` |
| A requested profile with reordered canonical keys is rejected instead of being normalized silently | `refusesAProfileWhoseCanonicalKeysWereReordered` |
| Named operator policies resolve to registered chunkers; retries refuse a pinned component version no longer present | `preservesNamedOperatorPoliciesWithoutReadingARawChunkerId`, `refusesARetryWhenAPinnedComponentVersionIsUnavailable` |
| Mixed Word paragraphs/tables reach the structured policy and table rows keep their header; PDF page provenance and plain text behavior remain intact | `runsAMixedWordDocumentThroughTheStructuredBlockPolicy`, `TypedBlockParsingTests`, `PdfPageProvenanceTests`, `ParagraphSemanticChunkerTests` |
| Semantic-vector fallback pins recursive-character and retry neither calls semantic embedding again nor changes the resolved profile hash | `retryUsesThePinnedRecursiveFallbackWithoutCallingSemanticEmbeddingAgain` |
| Retry rejects changed canonical evidence/chunk manifest against the pinned resolved snapshot | `retryRejectsContentThatNoLongerMatchesThePinnedChunkManifest` |
| First requested and resolved snapshots win; later runtime defaults or conflicting resolved output cannot replace them | `SourceIngestionJobProcessingProfileTests` |
| PostgreSQL migration stores requested/resolved snapshots, requires a complete profile on READY, and publication retry keeps both profile hashes unchanged | `sourceUploadFlowsThroughValidationParsingChunkingEmbeddingAndReady`, `keepsPublicationPendingWhenAuthorizationProjectionIsUnavailable` |
| V29-to-V30 preserves a historical READY revision without manufacturing a profile while the new unvalidated constraint rejects a new READY row without one | `SourceProcessingProfileMigrationTests.preservesLegacyReadyIdentityWhileEnforcingProfilesForNewReadyRows` |
| V30 refuses migration while a legacy ingestion job is PENDING instead of guessing the new deployment's policy | `SourceProcessingProfileMigrationTests.refusesToGuessAPolicyForLegacyNonterminalJobs` |
| The reusable parser advertises all sixteen admitted suffixes and parses CSV, Office, OpenDocument, HTML/HTM, RTF, text, Markdown, and PDF through real format fixtures | `SupportedFormatParsingTests`, `TypedBlockParsingTests`, `PdfPageProvenanceTests` |
| A genuine Microsoft Word Heading 1 survives as a typed `HEADING`; spreadsheets become tables; HTML navigation, script, and style are removed | `preservesAHeadingAuthoredByMicrosoftWordAsATypedBlock`, `parsesNewOfficeAndOpenDocumentFormatsEndToEnd`, `removesHtmlNavigationScriptAndStyleBeforeCreatingEvidence` |
| CSV handles UTF-8 BOM, delimiter sniffing, quoted newlines, and escaped quotes while preserving one table | `SupportedFormatParsingTests.readsBomSemicolonCsvWithQuotedNewlinesAsOneTypedTable` plus the CSV cases in `TypedBlockParsingTests` |
| Generic archives and renamed container/media mismatches fail closed, while bounded OOXML and OpenDocument containers remain admissible | `refusesDeclaredArchivesButStillAcceptsZipBasedOfficeAndOpenDocumentFiles` plus the mismatch cases in `TypedBlockParsingTests` |
| Parser/media policy failures quarantine once without consuming the retry budget | `SourceIngestionPipelineIntegrationTests.quarantinesDeterministicParserFailureWithoutSchedulingARetry` |
| The requested snapshot pins the complete per-format chunk ceilings so a retry cannot observe changed defaults | `RequestedProcessingPolicyTests.pinsDifferentChunkCeilingsForSpreadsheetHtmlAndPdfRetries` |

## Format Admission And Upload Limit Coverage

Evidence classes: `KnowledgeContentTypeTests`, `SourceUploadServiceTests`, and
`source-upload-dialog.test.ts`.

| Behavior | Automated evidence |
| --- | --- |
| Knowledge admission, reusable parser capability, and browser selection share the closed sixteen-suffix set; worker routing consumes parser capability rather than another copied list | `KnowledgeContentTypeTests.admitsEveryOrganizationalDocumentFormatAndNoArchive`, `SupportedFormatParsingTests.publishesTheCompleteReusableParserCapability`, `source-upload-dialog.test.ts` |
| Upload limits are 10 MB for CSV/HTML/HTM/RTF/TXT/Markdown, 15 MB for spreadsheets, and 25 MB for PDF/Word/PowerPoint/OpenDocument text and presentation | `KnowledgeContentTypeTests.assignsLimitsByFormatCostInsteadOfOneGlobalDocumentLimit`, `SourceUploadServiceTests.rejectsASpreadsheetAboveItsFormatLimitWithAnActionableMessage`, `source-upload-dialog.test.ts` |
| An extension outside the closed policy and a file exceeding its format limit are rejected before evidence is persisted | `KnowledgeContentTypeTests`, `SourceUploadServiceTests`, `source-upload-dialog.test.ts` |
| Browser users can select a spreadsheet and an HTML export and submit each as a governed multipart upload | `document-actions.spec.ts#uploads a spreadsheet and an HTML export through the governed document dialog` |

## Documents View And Retirement Coverage

Evidence classes: `SourceQueryServiceTests`, `SourceListingIntegrationTests`,
`SecureSourceVisibilityAdapterTests`, `SourceContentServiceTests`,
`SourceDocumentEvidenceQueryTests`,
`SecureSourceActionAuthorizationAdapterTests`,
`KnowledgeAssetLifecycleServiceTests`, `SourceLifecycleServiceTests`,
`SourceContentWebMvcTests`, `governed-document-viewer.test.tsx`,
`source-preview.test.ts`, `source-status.test.ts`, and
`document-actions.spec.ts`.

| Behavior | Automated evidence |
| --- | --- |
| Listing clamps page size, short-circuits an empty authorized set, bounds the owner leg and the union, and preserves the visibility adapter's fail-unavailable posture | `SourceQueryServiceTests`, `SecureSourceVisibilityAdapterTests` |
| Latest-revision listing keeps staged unpublished work visible, filters an invisible Space to empty, reports whole-result status counts, and keyset paging neither duplicates nor skips a stable row after intervening mutation | `SourceListingIntegrationTests` |
| The web sends Space, classification, status, debounced text, cursor, and page-size parameters; tab badges and polling use the page envelope rather than the loaded rows | `source-status.test.ts`, `document-actions.spec.ts` |
| Documents and graph search share debounce behavior without a graph submit action | `document-actions.spec.ts`, `knowledge-graph-layout.spec.ts` |
| Visible rows batch-resolve Space, department, and uploader provenance, and distinguish pending, published-out-of-scope, available, and deletable READY native uploads | `SourceQueryServiceTests`, `governed-document-viewer.test.tsx`, `document-actions.spec.ts` |
| Content rechecks the canonical evidence scope and missing/denied sources share one opaque response | `streamsOnlyTheCurrentPermissionVisibleReadyRevision`, `deniedAndMissingSourcesShareTheOpaqueNotFoundContract`, `missingAndDeniedSourcesAreWireEquivalent` |
| Source Ledger exposes current READY document/blob metadata without exporting persistence types | `SourceDocumentEvidenceQueryTests` |
| Integrity mismatch closes the stream, fails unavailable, and records an audit | `integrityFailureClosesTheObjectAndIsAudited` |
| Markdown is delivered as plain text with `no-store`, `nosniff`, and inline disposition | `streamsMarkdownAsPlainTextWithClosedDeliveryHeaders` |
| Delete resolves only a READY native upload, rechecks `can_delete`, retires both aggregates, and accepts a consistent retry | `deleteReadyUploadResolvesTheSourceThenRetiresBothAggregates`, `repeatedSourceDeleteReturnsTheExistingRetirementWithoutMutatingAgain`, `resolvesAndArchivesOnlyAReadyNativeUpload`, `rejectsConnectorAndNonReadySources` |
| Preview allowlist keeps active types exact, refines only delivered plain text plus declared Markdown into the restricted renderer, labels common formats concisely, and leaves every unsafe response download-only | `source-preview.test.ts` |
| Browser opens protected text, confirms/deletes an eligible row, and disables Delete for processing work | `document-actions.spec.ts#views protected evidence and deletes only an eligible ready upload` |
| Browser proves the Knowledge navigation hierarchy, compositional classification/Knowledge Space copy, desktop master-detail, mobile Sheet, visible FAILED/QUARANTINED details, corrected-upload action, safe rendered/raw Markdown, inline PDF and raster image, plain text, download-only Office, preview retry, and narrow-screen overflow behavior | `document-actions.spec.ts#Knowledge presents safe cross-format evidence in a responsive right-side reader` |

Gaps: physical evidence/projection/tuple erasure is retention-policy work, not
part of the Documents retirement command. Pre-publication cancellation remains
unimplemented until publication fencing and stale-worker race tests exist.
Manual FAILED retry is also intentionally absent until Source Ingestion gains a
never-reused claim epoch and an exact Asset-publication permit with
manifest-pinned recovery tests.

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
| A credential is checked by the adapter that claimed its source, a source with no probe is refused rather than answered, and two adapters cannot both probe one source | `ConnectorCredentialProbeRegistryTests` |
| A database that already holds evidence survives the split of `source_type` into `acl_authority` and `source_system` | `SourceObjectAclAuthorityMigrationTests.anExistingObjectKeepsItsSystemAndGainsTheRightAuthority` |
| A complete crawl retires what it stopped mentioning | `aCompleteCrawlRetiresWhatTheSourceNoLongerHas` |
| An incomplete crawl, and a complete crawl that enumerated nothing, retire nothing | `anIncompleteCrawlRetiresNothingItSimplyDidNotMention`, `aCompleteCrawlThatEnumeratedNothingIsRefused` |

## Shared Polling Driver Coverage

Evidence class:
`integrations/connectors/src/test/java/com/orgmemory/connectors/PollingConnectorBatchSourceTests.java`.

| Behavior | Automated evidence |
| --- | --- |
| A derived client is reused while credentials/settings are unchanged and replaced after either revision changes | `reusesAClientUntilTheCredentialOrClientConfigurationChanges`, `changingTheImpersonatedUserRebuildsTheCachedClient` |
| Missing credentials and disabled/deleted connections evict clients; recreation starts with clean due and served-crawl-now cadence state | `missingCredentialEvictsTheCachedClientBeforeARecovery`, `disablingThenRecreatingAConnectionRetiresClientAndBothCadenceMaps` |
| Below half failed is admitted, while exactly half and above half return no batch with `mostly_failed` activity | `appliesTheMostlyFailedBoundaryOnce` |
| Unknown runtime failures propagate rather than becoming activity, and the retained cache context has no credential field | `unknownRuntimeExceptionsEscapeInsteadOfLookingLikeConnectionActivity`, `cachedContextDoesNotHaveASecretValueField` |
| Full and permissions-only crawl/component cursor bytes remain exact across all three adapters, including Drive's historical prefix | each adapter's `pinsGoldenCursorBytesAcrossContentAndPermissionPasses` |

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
| A rejected credential and a mostly-unreadable workspace fail rather than report a crawl; the threshold failure uses `mostly_failed` | `refusesToCrawlWithACredentialSlackRejects`, `abandonsARunInWhichMostChannelsCouldNotBeRead` |
| Between content crawls no message body is read, and the cheap batch never claims completeness | `readsNoMessageBodiesBetweenContentCrawls`, `aPermissionsCrawlNeverClaimsCompleteness`, `reissuesAContentCrawlOnceTheIntervalElapses` |
| A permissions crawl omits objects whose channel it could not see rather than asserting nobody may read them | `aPermissionsCrawlLeavesOutObjectsWhoseChannelItCouldNotSee` |
| The adapter is present wherever the module is and crawls nothing until a connection says so | `contributesTheAdapterWhereverTheModuleIsPresent`, `contributesNothingToCrawlUntilAConnectionIsEnabled`, `producesNothingUntilAConnectionIsEnabled` |
| A connection with no stored credential produces nothing and says why, rather than being skipped silently | `reportsAConnectionWithNoStoredCredentialRatherThanSkippingItSilently` |
| A configuration change is picked up on the next poll, without a restart | `picksUpAConfigurationChangeWithoutARestart` |
| One unusable workspace does not cost the others their poll, and is still reported | `oneUnusableWorkspaceDoesNotCostTheOthersTheirPoll` |

## Google Drive Adapter Coverage

Evidence class: `integrations/connectors/src/test/java/com/orgmemory/connectors/googledrive/GoogleDriveConnectorBatchSourceTests.java`.
Run against recorded Drive responses with a service account key generated in the
test, so the RS256 signing path executes for real and no credential from anywhere
real exists in the repository. Nothing touches the network.

| Behavior | Automated evidence |
| --- | --- |
| A Drive becomes files, owners and per-file grants on the crawl contract | `turnsADriveIntoTheCrawlContract`, `observesOwnersAndSharedUsersAsVerifiedPeople` |
| A user, a group and a domain grant; an anyone-with-the-link permission grants nothing | `grantsUsersGroupsAndDomainsButNeverAPublicLink` |
| Drive uses stable permission IDs and leaves Google Group/domain membership incomplete rather than guessing from observed emails | `grantsUsersGroupsAndDomainsButNeverAPublicLink`, `aDomainGroupIsStableButItsMembershipRemainsIncomplete` |
| Completeness is claimed only by an unfiltered, uninterrupted crawl | `claimsCompletenessOnlyForAnUnfilteredUninterruptedCrawl`, `withdrawsTheCompletenessClaimWhenOnlySomeFoldersWereAskedFor`, `withdrawsTheCompletenessClaimWhenAFileCouldNotBeRead` |
| Between content crawls no document body is read, and that pass never claims completeness | `readsNoDocumentBodiesBetweenContentCrawls`, `reissuesAContentCrawlOnceTheIntervalElapses` |
| The content revision is the text, so an unchanged document costs nothing and an edit is a new revision | `anEditedDocumentGetsANewContentRevisionAndAnUnchangedOneDoesNot` |
| A missing credential and a credential that is not a service account key are reported, and the refusal describes the credential rather than repeating any of it | `reportsAConnectionWithNoStoredCredentialRatherThanSkippingItSilently`, `reportsACredentialThatIsNotAServiceAccountKey` |
| A shared-drive file's `permissionIds` are followed instead of its absent inline permissions being read as an empty ACL | `followsPermissionIdsForASharedDriveFileInsteadOfSealingAnEmptyAcl` |
| Sharing that could not be read leaves the object out of the payload rather than granting nobody | `leavesOutAnObjectWhoseSharingCouldNotBeReadRatherThanGrantingNobody` |
| Google reporting an incomplete search withdraws the completeness claim | `withdrawsTheCompletenessClaimWhenGoogleReportsAnIncompleteSearch` |
| A scoped folder means its subtree, not its immediate children | `crawlsTheWholeSubtreeUnderAScopedFolder` |
| Replacing one reader with another changes the cursor, so the driver cannot skip the batch | `changesTheCursorWhenAReaderIsReplacedByAnotherRatherThanCounting` |
| A file past the size bound is not read, and that withdraws the claim because the bound is ours | `skipsAFileLargerThanTheBoundAndSaysTheCrawlIsNoLongerComplete` |
| A rate limit is waited out rather than failing the connection | `waitsOutARateLimitAndCompletesTheCrawl` |
| A failed content crawl does not consume the content interval | `aFailedContentCrawlDoesNotConsumeTheContentInterval` |
| Every type the adapter indexes is a type it asks Drive for, so none is silently dropped | `GoogleDriveDocumentTypesTests.everyTypeThisIndexesIsAlsoATypeItAsksDriveFor` |
| Exactly half of attempted files unreadable produces no batch and reports `mostly_failed` | `halfUnreadableFilesProduceMostlyFailedActivityInsteadOfABatch` |
| Changing the impersonated user rebuilds the cached client; after the token lifetime the reused client obtains a fresh access token | `changingTheImpersonatedUserRebuildsTheCachedClient`, `reissuesAContentCrawlOnceTheIntervalElapses` |

## GitHub Adapter Coverage

Evidence classes under
`integrations/connectors/src/test/java/com/orgmemory/connectors/github/`:
`GitHubApiClientTests`, `GitHubConnectorBatchSourceTests`, and
`GitHubConnectorAutoConfigurationTests`. The vertical retrieval proof is the
GitHub phase of
`apps/worker/src/test/java/com/orgmemory/worker/connector/ConnectorStagingIngestionIntegrationTests.java`.
Tests generate a non-production RSA key and use recorded GitHub responses; none
touches the network.

| Behavior | Automated evidence |
| --- | --- |
| GitHub App JWT is RS256, bounded to ten minutes, and private material is redacted | `signsTheDocumentedRs256JwtWithoutLeakingThePrivateKey` |
| Installation token is cached and every collaborator page is followed through `Link` | `followsLinkPaginationAndCachesTheInstallationToken` |
| A pagination link outside `api.github.com` is rejected before the installation token can be forwarded | `neverForwardsAnInstallationTokenToAPaginationHostGitHubDoesNotOwn` |
| Rate limits retry, while a plain permission refusal fails closed | `waitsOutARateLimitAndRetries`, `doesNotRetryAPlainPermissionRefusal` |
| A private repository becomes an issue/PR object, effective user identities, one repository-reader group, and a stable group ACL | `mapsARepositoryAudienceAndWorkItemToStableNativeIds` |
| No GitHub email is trusted implicitly | same mapping test; all source users have null email and `ssoVerified=false` |
| A collaborator removal changes the membership cursor without recrawling content | `teamDerivedReaderRemovalChangesOnlyMembershipOnTheNextPoll` |
| A below-threshold collaborator refusal marks permission and membership incomplete and never becomes an empty ACL | `oneOfThreeCollaboratorFailuresKeepsFailClosedHealthyProgress` |
| Public/inadmissible repositories are rejected rather than interpreted with a narrower ACL | `configuredPublicRepositoryIsRejectedRatherThanGivenANarrowAcl` |
| A content bound marks only content incomplete; complete authorization evidence remains usable | `issueBoundMarksOnlyContentIncomplete` |
| A repository request failure is counted at most once; below half keeps fail-closed healthy progress, while one-of-one or one-of-two returns no batch with `mostly_failed` and therefore stalls all checkpoint/revocation progress for that connection | `oneRepositoryCollaboratorFailureIsMostlyFailedAndProducesNoBatch`, `oneOfThreeCollaboratorFailuresKeepsFailClosedHealthyProgress`, `oneOfTwoCollaboratorFailuresCountsOnceAndRejectsAtHalf`, `oneOfTwoContentRequestFailuresRejectsAtHalf` |
| Permissions-only passes count collaborator request failures only; truncation and incomplete/unrepresentable fields do not become request failures | `permissionPassCountsOnlyCollaboratorRequestFailures`, `issueBoundMarksOnlyContentIncomplete`, `incompleteIssueFieldsDoNotCountAsAnUnreachableRepository`, `unrepresentableCollaboratorDataIsIncompleteButNotARequestFailure` |
| The generic connector surface is contributed but classpath presence alone performs no crawl | `contributesProfileProbeScopesAndBatchSource`, `classpathPresenceDoesNotAuthorizeACrawl` |
| Removing an explicitly mapped GitHub collaborator revokes retrieval while revision, chunks, and resource ACL generation remain unchanged | GitHub phase of `slackChannelBecomesGovernedAndConvergesOnMembership` |

## Connector Sync Correctness Coverage

| Behavior | Automated evidence |
| --- | --- |
| Content, permission, and membership cursors advance independently | `ConnectorCrawlCheckpointIntegrationTests.membershipChangeDoesNotReplayUnchangedContentOrPermissions` |
| Source-declared incomplete state is observable without becoming last-successful authorization state | `ConnectorCrawlCheckpointIntegrationTests.incompleteObservationDoesNotAdvanceLastSuccessfulAuthorizationState` |
| Per-item failure records `PARTIAL`, advances successful components, and leaves the failed component pending | `ConnectorCrawlCheckpointIntegrationTests.aPartialBatchAdvancesOnlySuccessfulComponentsAndRetriesTheFailure` |
| Incomplete permission evidence neither materializes content nor rotates an ACL | `ConnectorIngestionServiceTests.incompletePermissionEvidenceCannotMaterializeContentOrRotateAcl` |

The shared-drive proof was verified by removing the `permissions.list` fallback
and watching `followsPermissionIdsForASharedDriveFileInsteadOfSealingAnEmptyAcl`
fail, then restoring it.

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
| A second source is configured over the same endpoints and stored under the name its adapter declared | `asecondSourceIsConfiguredOverTheSameEndpoints` |

Gaps: there is no real blob-store/scan/parser integration test yet (the connector
proofs mock object storage and OpenFGA). The Slack adapter is proved against
recorded responses only, and so is the Google Drive adapter — no run against a
real workspace or a real Drive has happened yet, which is the remaining
`slack-connector-live` work. The administration screens have no
browser test; their proofs are at the API boundary, and the catalogue, the field
descriptor and the connection detail page have no automated proof at all — they
are covered by lint, typecheck and build only. Only one migration is proved
against a database that already holds rows; the rest are proved against an empty
schema, where a data-transforming statement cannot fail.
