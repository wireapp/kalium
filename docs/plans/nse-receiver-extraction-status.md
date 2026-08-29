# NSE receiver extraction status

This note records the compile-time closure inspected while implementing the receiver-extraction milestone described by
`nse-safe-multi-process-event-processing.md`. It supplements that plan without changing its design or the NSE runtime scope.

Per-slice entries retain the state and wording relevant when each slice landed. A later boundary cleanup stopped exposing
`:domain:messaging:receiving` transitively from `:logic` and removed `@InternalKaliumApi` from declarations owned by the
internal-only receiving and event-processing modules. Their required public Kotlin visibility is module-composition
surface, not exported `KaliumLogic` product API. Annotation references in historical slice plans describe that earlier
intermediate state; annotations on shared or data-mapper APIs remain where the source still declares them.

## Historical audit note

The extraction history contains one broken intermediate commit: `e7e597b5d6d7` declared
`conversationLifecycleEventRepository` after an eager consumer in `UserSessionScope`, causing session-scope construction
to fail. Commit `66df93492633` immediately moved the lazy declaration before every consumer and restored the baseline.
The current branch is not affected, but the interval from `e7e597b5d6d7` through the parent of `66df93492633` must not be
used as a runnable bisect point.

## Completed MLS dependency-boundary prerequisite slice

This dependency-boundary-only prerequisite prepares `MLSMessageUnpacker` for a later mechanical move without moving it,
rewiring it, or changing runtime composition:

1. `ProtocolInfoMapper` and its existing tests now have one owner in `:data:data-mappers` under their existing package and
   FQCN. Mapping behavior and the existing test fixtures are unchanged, including the currently mistaken Proteus fixture.
   The only construction change is the lower-level equivalent `IdMapper()` default required to remove the logic-owned
   `MapperProvider` dependency.
2. `ConversationProtocolGetter` and `ConversationProtocolGetterImpl` now have one owner in
   `:domain:messaging:shared`, retaining the exact DAO lookup, `wrapStorageRequest` `DataNotFound`/exception/cancellation
   behavior, protocol mapping, and `Either` result. `ConversationRepository` extends the focused getter contract while
   retaining its existing implementation and narrower `StorageFailure` result.
3. `SubconversationGroupInfoProvider` exposes only the existing in-memory
   `getSubconversationInfo(conversationId, subconversationId): GroupID?` lookup. `SubconversationRepository` extends this
   contract without changing its implementation instance, mutex, map, readers, writers, fallback behavior, or remote
   methods. This in-memory provider is process-local and is not safe as an NSE-process coordination mechanism.
4. The exact receiver-facing `ApplicationMessage` and `DecryptedMessageBundle` models now have one owner in
   `:domain:messaging:receiving` with their existing package, FQCNs, fields, byte-array equality, and hash behavior.
   `DecryptedMessageBundle.identity` remains `WireIdentity?`. The receiving-owned `MLSMessageDecryptor` contract exposes
   only the existing `decryptMessage` and `getLocalGroupEpoch` operations used by MLS unpacking.
5. `MLSConversationRepository` extends `MLSMessageDecryptor` without changing its CoreCrypto wrappers, CRL checks,
   result mapping, mutex/flow behavior, or other repository operations. `ObservableMLSConversationRepository` still
   decorates the same successful decrypt result with the existing crypto-state hook and delegates failures unchanged.
   `DecryptedMessageBundleMapper` remains in `:logic` with unchanged mapping behavior.
6. `UserSessionScope` construction and all concrete repository objects are unchanged. `MLSMessageUnpacker`, its tests,
   its concrete repository-typed constructor, `MLSMessageFailureHandler`, `PendingProposalScheduler`, and
   `KaliumSyncException` remain in `:logic`. Proposal scheduling is still a later app-owned/process-coordination boundary.
7. Shared and receiving retain their dependency guards and have no dependency on `:logic`; data-mappers likewise has no
   logic dependency. This slice adds no NSE runtime wiring, storage, adapter, lock, queue, retry, flag, or async redesign.
   It does not claim that MLS receiving is ready to instantiate in an NSE.

## Completed MLS receiving extraction slice

The follow-up move is now complete as a pure ownership refactor:

1. `KaliumSyncException` now has one owner in `:domain:event-processing` under its existing package and FQCN. The
   cross-module public surface is limited to the class, constructor, and `coreFailureCause` property that were internal
   while the type and its callers shared `:logic`; message, cause storage, and runtime exception behavior are unchanged.
2. The `PendingProposalScheduler` contract now has one owner in `:domain:messaging:receiving` under its existing package
   and FQCN. `PendingProposalSchedulerImpl`, its eager initialization, limited-parallelism dispatcher, coroutine scope,
   incremental-sync observer, timer flow, delays, CoreCrypto transaction, repository calls, cleanup classification,
   and tests remain unchanged in `:logic`.
3. `MLSMessageFailureHandler` and `MLSMessageFailureResolution` now have one owner in
   `:domain:messaging:shared` under their existing package and FQCNs. The complete network-MLS normalization and failure
   classification table is unchanged and is characterized in the owning module because join and meeting consumers also
   use it outside receive unpacking.
4. `MLSMessageUnpacker`, `MLSMessageUnpackerImpl`, and their tests now have one owner in
   `:domain:messaging:receiving` under their existing package and FQCNs. The constructor now depends only on
   `ConversationProtocolGetter`, `SubconversationGroupInfoProvider`, `MLSMessageDecryptor`,
   `PendingProposalScheduler`, and `ProtoContentDecoder`; `selfUserId` and the default `MapperProvider` construction were
   removed because unpacking does not otherwise use them.
5. The exact existing lookup, fallback, protocol selection, Base64, decrypt, buffered diagnostics, proposal scheduling,
   protobuf decode, result ordering, logging, exception, and cancellation behavior is unchanged. The receiving tests now
   additionally pin the subconversation-first Elvis fallback, Base64-before-decrypt ordering, empty-bundle handshake,
   sequential schedule-before-decode behavior, original buffered failure return, diagnostics-only epoch lookup, external
   protobuf exception, and cancellation propagation.
6. `UserSessionScope` passes the same runtime objects: `conversationRepository` as the protocol getter, the stable
   process-local `subconversationRepository`, `mlsConversationRepository` as the decryptor, the existing eager
   `PendingProposalSchedulerImpl`, and `protoContentMapper` as the decoder. The decryptor is therefore still the
   `ObservableMLSConversationRepository` wrapper, so CRL checking and mapping remain in the delegate path and the crypto
   state hook still runs only after successful decrypt.
7. This slice adds no NSE runtime wiring, retry, queue, locking, rollout switch, no-op scheduler, or async redesign. NSE
   composition still requires durable `(conversationId, subconversationId) -> groupId` state, scheduler ownership plus a
   durable outbox and main-app executor, the Kalium account event lock, and validation of the assumed CoreCrypto process
   serialization. No separate Kalium CoreCrypto database-lock implementation is planned. The full protobuf encoder/mapper
   and broad `AssetMapper` graph remain in `:logic` behind `ProtoContentDecoder`.

## Completed quoted-message and link-preview boundary slice

1. `MessageContentEncoder` and its common tests now live in `:domain:messaging:shared` with the same package, FQCN,
   encoding implementation, and supported/unsupported behavior. The `@InternalKaliumApi` public surface exposes only the
   encoder construction, encode operation, returned type, and hash/hex values needed by existing outgoing/debug callers
   and incoming quote verification. The shared module adds only its direct cryptography and date-time dependencies and
   retains its guard against dependencies on `:logic`, `:domain:messaging:receiving`, and `:domain:messaging:sending`.
2. Incoming quoted-message verification now has a concrete receiving-owned `IncomingQuotedMessageVerifierImpl` backed
   directly by the existing user `MessageDAO` and the shared `MessageContentEncoder`. Its focused stored-hash-input mapper
   reproduces text bodies, asset IDs, location coordinates, and multipart body plus attachment ordering/filtering/UUID
   semantics without moving or copying the broad `MessageRepository` or `MessageMapper` below `:logic`.
3. Quote verification retains the null-hash no-lookup branch, qualified conversation/message lookup, `wrapStorageRequest`
   `DataNotFound`/`Generic` handling, cancellation propagation, escaping mapping/encoding exceptions, byte-array content
   comparison, exact verification logging order/text, and copies the original reference changing only `isVerified`.
   `UserSessionScope` constructs one verifier with the existing user `MessageDAO` and one encoder instance captured for
   that verifier; neither object is recreated per message.
4. The public `LinkPreviewImagesResolver` contract now lives in `:domain:messaging:shared` with its existing package and
   FQCN. `LinkPreviewImagesResolverImpl`, its broad repositories, filesystem/network behavior, coroutine scope/dispatcher,
   feature flag, tests, and `UserSessionScope` lifecycle remain in `:logic`; successful text persistence invokes the same
   focused contract exactly as before.

## Completed ApplicationMessageHandler extraction slice

1. `ApplicationMessageHandler`, `ApplicationMessageHandlerImpl`, and the complete common test suite now live in
   `:domain:messaging:receiving` with their existing package and FQCNs. Only the public interface, implementation,
   constructor, and interface-method visibility required by existing `:logic` callers was added.
2. The complete regular/signaling envelope construction, routing branches, exact leaf arguments and order, quote
   verification, persistence/link-preview sequencing, asset exception boundary, unsupported history behavior,
   decryption-error persistence, and pending last-read flushing are unchanged.
3. The moved suite replaces only broad logic test fixtures with equivalent local conversation, sender, client, timestamp,
   self-user, and transaction-context values. Its unused file-sharing configuration setup was removed because the asset
   leaf remains mocked; all behavior assertions and asset-handler verification remain intact.
4. The deprecated `AssetContent` compatibility extensions remain unchanged in a small `:logic`-owned source file rather
   than becoming part of the receiving facade.
5. The shared `LinkPreviewImagesResolver` contract remains the facade dependency. Its app-owned implementation,
   repositories, filesystem/network work, coroutine lifecycle, and feature flag remain in `:logic` behind that contract.
6. `UserSessionScope` construction and `NewMessageEventHandler` routing were unchanged by that slice. At that point the
   handler remained in `:logic`; it is now receiving-owned as documented below.
7. The receiving module still has no dependency on `:logic`. This extraction adds no NSE runtime facade or wiring, locks,
   retries, async redesign, outbox, or process coordination.

## Completed Proteus incoming-message unpacking slice

Inspection of the existing Proteus unpacker, failure classification, protobuf mapper, `UserSessionScope`,
`NewMessageEventHandler`, `MLSMessageUnpacker`, `LegalHoldHandler`, and their tests produced this completed
behavior-preserving extraction:

1. `ProtoContentDecoder` now lives in `:domain:messaging:shared` under the existing
   `com.wire.kalium.logic.data.message` package and exposes only `decodeFromProtobuf(PlainMessageBlob): ProtoContent`.
   It is annotated `@InternalKaliumApi` for the cross-module boundary. `ProtoContentMapper` remains an internal
   `:logic` contract, now extends this narrow decoder, and `ProtoContentMapperImpl`, all broad mapper dependencies,
   `MapperProvider` composition, encoding behavior, and mapper tests remain in `:logic`.
2. `MessageUnpackResult`, `ProteusMessageFailureResolution`, `ProteusMessageFailureHandler`, `ProteusMessageUnpacker`,
   and `ProteusMessageUnpackerImpl` now live in `:domain:messaging:receiving` with their existing package/FQCNs. The
   visibility added to their cross-module surface is limited to what the then logic-owned `MLSMessageUnpacker`,
   `LegalHoldHandler`, and `NewMessageEventHandler` required. The MLS unpacker and new-message handler have since moved to
   the same receiving module; legal hold remains app-owned behind a callback.
3. `ProteusMessageUnpackerImpl` receives the shared `ProtoContentDecoder` and the existing lower-level `IdMapper()` API.
   `UserSessionScope` supplies the existing logic-owned `ProtoContentMapper` instance as that decoder, so there is one
   broad mapper graph and no `MapperProvider` dependency in receiving. No NSE runtime wiring or lifecycle redesign was
   introduced.
4. The moved unpacker retains the exact Base64 decoding, Proteus crypto-session ID construction and decrypt callback,
   `PlainMessageBlob` conversion, protobuf decode, external AES-256 resolution, null-external-content failure,
   nested-external-message rejection, Either flattening, logging/classification, exception propagation, cancellation
   propagation, and decode/callback/failure/return ordering. The Proteus failure classification table remains exactly
   shared by the moved unpacker and the now receiving-owned `NewMessageEventHandler`.
5. The `ProteusMessageUnpackerTest` and `ProteusMessageFailureHandlerTest` suites now have one owning copy in receiving.
   Only the former logic-only event/crypto/assertion fixtures were replaced with local equivalents; all existing
   assertions and characterized external-content and failure-classification behavior remain covered.
6. Receiving and shared retain their dependency guards and do not depend on `:logic`. At the time of that slice,
   `NewMessageEventHandler` and MLS unpacking remained in `:logic`; both are now receiving-owned. The broad legal-hold,
   stale-epoch, reset/rejoin, self-deletion, confirmation-delivery, and pending-side-effect implementations remain
   app-owned behind the focused boundaries documented below.

## Completed NewMessageEventHandler extraction slice

1. `NewMessageEventHandler`, `NewMessageEventHandlerImpl`, and all 20 pre-existing tests now have one owner in
   `:domain:messaging:receiving` under their existing package and FQCNs. Only the public visibility required for
   cross-module composition was added.
2. The handler retains direct object dependencies on `ProteusMessageUnpacker`, `MLSMessageUnpacker`,
   `ApplicationMessageHandler`, and `selfUserId`. The broad app implementations cross the boundary only as focused
   callbacks:
   - legal hold: `suspend (MessageUnpackResult.ApplicationMessage, Boolean) -> Either<CoreFailure, Unit>`;
   - stale epoch: `suspend (CryptoTransactionContext, ConversationId, SubconversationId?, Instant?) -> Either<CoreFailure, Unit>`;
   - MLS reset: `suspend (ConversationId, CryptoTransactionContext) -> Either<CoreFailure, Unit>`.
   The existing `(ConversationId, String) -> Unit` self-deletion and
   `suspend (ConversationId, String) -> Unit` confirmation-delivery callbacks retain their meaning.
3. `UserSessionScope` supplies the exact same actions through `legalHoldHandler::handleNewMessage`,
   `staleEpochVerifier::verifyEpoch`, `messages.ephemeralMessageDeletionHandler.startSelfDeletion`, and
   `messages.confirmationDeliveryHandler.enqueueConfirmationDelivery`. It captures the getter-backed
   `resetMlsConversation` once per handler construction, preserving the former per-handler use-case instance, and the
   reset callback invokes that captured instance with `(conversationId, transactionContext)` before converting its
   result to `Either`.
4. Proteus unpacking and failure persistence, MLS transaction wrapping and classifications, parent-only MLS
   decryption-error persistence, OutOfSync/reset arguments, batch filtering/order/logger reset, legal-hold/content/
   insertion-side-effect order, confirmation and self-deletion filters, pending-side-effect flushing, ignored callback
   results, exceptions, and cancellation remain unchanged.
5. The moved suite uses local event/transaction fixtures and focused callback recorders instead of logic-only fixtures
   and broad mocks. No existing test was dropped. Focused characterization now pins ResetConversation reset exclusivity,
   exact one-call flush delegation, legal-hold-before-content-before-confirmation/self-deletion order, ignored callback
   `Either` values, ordinary exception propagation, and coroutine-cancellation propagation.
6. `LegalHoldHandler`/`Impl`, `StaleEpochVerifier`/`Impl`, `ResetMLSConversationUseCase`/`Impl`, repositories, scheduler
   implementation, filesystem work, observers, and side-effect executors remain in `:logic`; no NSE runtime wiring,
   locks, retries, queues, durable actions, async redesign, or rollout switches were added.
7. This does not make NSE runtime composition ready. Legal hold, stale-epoch recovery, reset/rejoin,
   confirmation/self-deletion execution, and pending-side-effect durability still need explicit NSE ownership/adapters
   or a durable action/outbox design. Durable subconversation mapping, pending-proposal ownership/outbox/execution, and
   validation of the assumed CoreCrypto process serialization remain separate work. At this slice, conversation lifecycle
   handlers still blocked the complete `ConversationEventReceiverImpl` move; that ownership move has since completed.

## Completed AssetMessageHandler extraction slice

Inspection of `AssetMessageHandlerImpl`, `UserConfigRepository`, `FeatureConfigRepositoryImpl`, `MessageRepository`,
`MessageMapper`, `MessageDAO`, `PersistMessageUseCase`, `ValidateAssetFileTypeUseCase`, `UserSessionScope`, and their tests
produced this completed behavior-preserving extraction:

1. `AssetMessageHandler` and `AssetMessageHandlerImpl` now live in `:domain:messaging:receiving` with their existing
   package and only the cross-module visibility needed for composition. `ApplicationMessageHandler` routing and the
   incoming asset call path remain unchanged.
2. The handler now depends on the focused `FileSharingStatusProvider` contract in `:domain:messaging:receiving`.
   The existing DAO-backed `FeatureConfigRepositoryImpl` extends that contract, and `UserSessionScope` shares one instance
   with `UserConfigDataSource`, feature-config handlers, and `AssetMessageHandler`. Both broad app queries and incoming
   asset handling therefore retain the same live allowed-file-types provider and status derivation.
3. The handler now uses `IncomingAssetMessageLookup`, a concrete `MessageDAO.getMessageById` adapter in
   `:domain:messaging:receiving`, instead of broad `MessageRepository.getMessageById`. The broad repository method remains
   unchanged for other callers, and neither broad `MessageMapper` nor logic-only `MapperProvider` moved below `:logic`.
4. `StoredIncomingAssetMessage` represents only the receiver-relevant classifications: regular asset, restricted asset,
   unsupported regular content, and system content. Its focused mapper retains sender verification, existing log type/IDs,
   asset metadata and remote data, and every stored regular field that influences the follow-up `Message.Regular`:
   date, client, status/read count, edit state, expiration/self-deletion state, visibility baseline, sender name,
   self-message state, read-confirmation expectation, reactions, and delivery status. Follow-ups replace only remote data
   and recalculate visibility from `remoteData.hasValidData()` as before.
5. The lookup uses the existing qualified-ID mapping and `wrapStorageRequest`: a missing row is `DataNotFound`, ordinary
   DAO exceptions are wrapped, and cancellation escapes. Every returned lookup `Left` still enters the existing
   missing-message path; no failure-kind filtering was introduced.
6. `ValidateAssetFileTypeUseCase` and its implementation now live in `:domain:messaging:receiving` with the same package
   and behavior. Filename extension parsing and precedence, MIME fallback, case-sensitive matching, logging, and the
   existing duplicate-key MIME map contents—including their current result—remain unchanged. Existing outgoing `:logic`
   callers use the moved implementation without adapters.
7. The handler still reuses `PersistMessageUseCase` unchanged. Restricted, new-preview, and follow-up persistence retain
   the same ignored returned `Left`, escaping exception/cancellation, notification, receipt-mode, persistence-hook, and
   conversation-order behavior. This slice adds no filesystem work, async work, retries, outbox, process lock, rollout
   switch, or NSE runtime wiring.
8. Focused lower-module tests cover lookup classifications, exact asset and stored-field mapping, missing rows, wrapping,
   cancellation, status-provider derivation, and validator quirks. The handler test suite now lives in the receiving
   module and continues covering early return, every file-sharing state, null/empty-name conditional restriction, all
   lookup/persist outcomes, preview visibility and image metadata, sender/type rejection, exact merge preservation,
   arguments, counts, and order.

The asset slice mechanically moved its handler and tests without changing `ApplicationMessageHandler` routing or
`UserSessionScope` construction. The following calling slices first decoupled the normal-forwarding and remote-mute
dependency closures, then mechanically moved `CallingMessageHandler` and its tests.

## Completed CallingMessageHandler extraction slice

Inspection of `CallingMessageHandlerImpl`, `CallManager`, the remote-mute policy and effects, `UserSessionScope`, and their
tests produced this completed behavior-preserving extraction slice:

1. `CallingMessageHandler` and `CallingMessageHandlerImpl` now live in `:domain:messaging:receiving` with their existing
   package and only the cross-module visibility needed for composition. Non-`REMOTEMUTE` messages cross the one-method
   `IncomingCallingMessageConsumer` port, forwarding the exact same signaling message and calling content once.
2. `UserSessionScope` supplies a lambda whose body remains the existing
   `callManager.value.onCallingMessageReceived(message, content)` call. This new normal-message forwarding adapter
   evaluates `callManager.value` only when its consumer is invoked, preserving the `CallManager` lazy lifecycle and
   identity for that path. `REMOTEMUTE` bypasses this adapter, but a successful remote mute may still access the same
   `CallManager` through the existing `calls.muteCall` to `MuteCallUseCaseImpl` path exactly as before.
3. The pure `ShouldRemoteMuteChecker`, `ShouldRemoteMuteCheckerImpl`, and their tests now live in
   `:domain:messaging:receiving`, retaining their package and exact admin, target-domain, user/client, null, and
   case-sensitive matching behavior. Only the public visibility required for cross-module use was added.
4. The remote-mute closure is now decoupled from its broad `:logic` contracts. Current client lookup is the precise
   `suspend () -> Either<CoreFailure, ClientId>` function dependency and `UserSessionScope` supplies it exactly as
   `currentClientIdProvider = clientIdProvider::invoke`. Conversation members cross the focused
   `ConversationMembersProvider` boundary owned by `:domain:messaging:receiving`; its `DaoConversationMembersProvider`
   uses the existing `MemberDAO` flow and maps qualified IDs plus `Admin`, `Member`, and named `Unknown` roles exactly as
   the former broad repository path did. `ConversationDataSource.observeConversationMembers` delegates to this same
   focused provider instead of retaining a second DAO observation/mapping path.
5. Remote-mute effects now cross the receiving-owned `RemoteMuteCall` and `RemoteMuteActionRecorder` ports.
   While constructing its lazy handler, `UserSessionScope` resolves and captures the existing `calls.muteCall` use-case
   instance and `callModerationActionsRepository` instance once, preserving their former initialization lifecycle. The
   adapters call those captured instances with `(conversationId, true)` and `(conversationId, action)`, respectively.
   `CallManager`, `MuteCallUseCase`, `CallsScope`, and the process-local `CallModerationActionsRepository` remain in
   `:logic` behind these receiving-owned ports. The handler still builds the exact
   `CallModerationAction(message.id, message.senderUserId, MUTED)`, mutes first, and records second.
6. The handler still performs JSON decoding and `REMOTEMUTE` branching before touching remote-mute dependencies. Its
   remote-mute sequence remains current client ID, unavailable-client return, member-flow `first()`, checker, logging,
   then accepted-only mute and action recording. Target-conversation selection, normal-message forwarding, dependency
   exception/cancellation propagation, and short-circuiting are unchanged. No queue, async dispatch, outbox, retry,
   fallback, exception handling, NSE runtime wiring, or eager call-object evaluation was added.
7. Focused tests now characterize normal-path isolation, all self/non-self target-conversation cases, the missing-client
   return, first member emission, checker rejection, exact accepted effect order and action identity, and same-instance
   exception/cancellation propagation with later-work suppression at every remote-mute dependency. DAO-backed provider
   tests cover qualified member IDs, every role mapping including named unknown roles, and multiple flow emissions.
8. The handler suite now lives in `:domain:messaging:receiving`; only its former broad `:logic` fixture references were
   replaced with equivalent local IDs. The mechanical move did not change `ApplicationMessageHandler` routing or
   `UserSessionScope` construction, including the current-client provider and once-captured mute/moderation adapters.

## Completed clear-conversation-content application-message slice

Inspection of `ClearConversationContentHandlerImpl`, `ConversationRepository.clearContent`, `ConversationDAO`,
`ClearConversationAssetsLocallyUseCase`, `DeleteConversationUseCase`, persistence hooks, `ApplicationMessageHandler`,
`UserSessionScope`, and their tests produced this completed behavior-preserving extraction:

1. `ClearConversationContentHandler` and its implementation now live in `:domain:messaging:receiving`.
   The existing `MessageContent.Cleared` routing expression is unchanged. At the time of this leaf extraction,
   `NewMessageEventHandler` remained in `:logic`; it is now receiving-owned.
2. Incoming content clearing now uses `ConversationLifecycleEventRepository.clearContent`, backed by the existing
   `ConversationDAO.clearContent` operation and `wrapStorageRequest`. `ConversationDataSource.clearContent` delegates to
   the same lifecycle-repository instance composed in `UserSessionScope`, so the extraction adds neither duplicate DAO
   logic nor an NSE-specific repository.
3. The extracted handler reuses `IsMessageSentInSelfConversationUseCase` unchanged. It still compares the signaling
   sender with the self user, verifies the same full signaling envelope, and returns without effects when those two
   booleans differ.
4. Authorized effects retain the exact payload-ID sequence: clear database content, clear local conversation assets,
   notify `onConversationCleared` with the payload conversation and self user, then optionally delete the whole
   conversation. Whole-conversation deletion still requires both `needToRemoveLocally` and a self sender.
5. Returned clear-content and asset-cleanup failures remain ignored so later work continues; the optional deletion result
   is likewise ignored. Exceptions and cancellation remain uncaught and stop all later operations at each dependency.
6. Filesystem and asset behavior remains in `:logic` behind the named `ClearConversationAssetsLocally` port. The
   main-app `ClearConversationAssetsLocallyUseCaseAdapter` delegates directly to the existing
   `ClearConversationAssetsLocallyUseCase`; `AssetRepository`, `MessageRepository`, `AssetDataSource`, and
   `KaliumFileSystem` did not move.
7. MLS-aware deletion remains in `:logic` behind the named `WholeConversationDeletion` port. The main-app
   `DeleteConversationUseCaseAdapter` forwards the same `CryptoTransactionContext` and conversation ID to the existing
   `DeleteConversationUseCase`; its Proteus local deletion, MLS local deletion plus CoreCrypto wipe, persistence hook,
   and proposal-timer behavior did not move or change.
8. The moved handler suite covers the full sender/self-conversation authorization matrix, exact arguments and order,
   optional deletion, returned `Left` continuation, exact hook identity, and exception/cancellation short-circuiting at
   every dependency. Focused lifecycle-persistence, broad-facade delegation, adapter-transparency, and exact application
   routing tests cover the new boundaries.

## Completed delete-message application-message slice

Inspection of `DeleteMessageHandlerImpl`, `MessageRepository`, `MessageDeletionPersistence`, `MessageDAO`,
`AssetRepository`, `AssetDAO`, `KaliumFileSystem`, `NotificationEventsManager`, persistence hooks, `UserSessionScope`, and
their tests produced this completed behavior-preserving extraction:

1. `DeleteMessageHandler` now lives in `:domain:messaging:receiving` and depends on focused incoming message-deletion,
   ID-based delete-notification, named asset-cleanup, and persistence-hook contracts. The application facade still routes
   the same delete branch synchronously. At the time of this leaf extraction, `NewMessageEventHandler` remained in
   `:logic`; it is now receiving-owned.
2. `IncomingMessageDeletionPersistence` loads only the stored message ID/conversation, original sender,
   regular-message ephemeral state, and optional remote asset ID directly from `MessageDAO`. The existing
   `MessageDeletionPersistence` hard-delete path remains the shared base contract, while the incoming extension adds the
   same DAO-backed tombstone operation; broad `MessageRepository` and the full app `MessageMapper` remain in `:logic`.
3. Filesystem infrastructure remains in `:logic`. The lower-level handler owns only the named
   `DeleteMessageAssetCleanup` port; the main-app `AssetRepositoryDeleteMessageAssetCleanup` adapter delegates directly
   to the existing `AssetRepository.deleteAssetLocally`. `AssetDataSource`, `AssetDAO`, and `KaliumFileSystem` production
   behavior are unchanged, including lookup/file/row ordering, wrapped failures, escaping exceptions, and cancellation.
4. Deletion rules remain exact: a self-authored ephemeral original can be hard-deleted without verifying the delete
   sender; otherwise the delete sender must be the original sender or self; verified ephemeral messages are hard-deleted;
   and verified non-ephemeral messages notify before being marked deleted.
5. The current unintuitive side-effect behavior remains characterized, not redesigned: asset cleanup runs
   after a successful lookup even for an unverified delete sender; returned hard-delete, tombstone, and asset-cleanup
   failures are ignored; and the persistence hook runs after lookup `Left` and returned mutation failures but is skipped
   when an exception or cancellation escapes before it.
6. Delete-notification scheduling uses a narrow stored-conversation/message-ID contract backed by the same
   `NotificationEventsManagerImpl`; the existing `Message`-taking API delegates to the same ID-based mapping and remains
   available to other callers. Hook payloads still use the incoming payload conversation/message IDs and self user.
7. `UserSessionScope` composes one focused message-deletion persistence shared with `MessageDataSource`, plus one
   session-scoped main-app asset-cleanup adapter around the existing `AssetRepository`. Handler, persistence, direct asset
   infrastructure, adapter, broad message delegation, mapper, and application-routing tests cover the preserved branches,
   ordering, failures, and cancellation.

At the later durable-outbox transition, both main-app and NSE handler composition will replace the direct cleanup adapter
with the same durable enqueue implementation. The main-app effect executor will then consume that action through the
existing direct `AssetRepository` filesystem cleanup path; neither process will silently omit cleanup or use a no-op.

Deferred design checkpoint for the durable-outbox milestone:

- Sender verification plus the authoritative message hard-delete or tombstone remain synchronous. An event must not be
  marked processed while that database mutation is merely queued for the main app.
- When side effects are deferred, the authoritative mutation and deduplicated outbox rows are committed together before
  processed marking. The outbox stores source event ID, effect type, stable payload/target, state, lease, acknowledgement,
  and deduplication data; the existing `PendingActions` table is not extended for this purpose.
- Capture the remote asset ID and every other required target before a destructive message delete. The main app later
  claims slow/app-only work such as filesystem and asset-row cleanup. Notification effects may be claimed by the bounded
  NSE request when required for its result, with main-app recovery through the same claim/acknowledgement protocol.
- Classify persistence hooks individually and defer only non-authoritative work. This later change is intentional new
  behavior with its own crash, retry, ordering, and cross-process tests; it is not part of the extraction slice.

## Historical text/multipart-edit application-message slice plan

Inspection of `MessageTextEditHandlerImpl`, `MessageMultipartEditHandlerImpl`, `MessageRepository`/`MessageDataSource`,
`MessageDAO`, `NotificationEventsManager`, `UserSessionScope`, the message/link-preview/mention/attachment mappings, and
their tests gives this combined extraction plan:

1. Add one focused `MessageEditPersistence` contract in `:domain:messaging:receiving`, backed by `MessageDAO`, exposing
   only minimal sender/arbitration state, text and multipart edit writes, and the resulting-message `SENT` update. Map
   only stored text value/mentions and multipart value/mentions/attachments plus the regular-message edit timestamp; do
   not move broad `MessageRepository`, `getMessageById`, or the full app `MessageMapper`.
2. Preserve exact persistence behavior: qualified IDs, `wrapStorageRequest`, mention self-state, stored multipart
   attachment ordering/filtering, text link-preview mapping, multipart null-text fallback to an empty string, and no
   multipart attachment writes. Map the status operation to `MessageEntity.Status.SENT` exactly.
3. Move `MessageTextEditHandler`/`Impl` and `MessageMultipartEditHandler`/`Impl` together to
   `:domain:messaging:receiving`, retaining their packages and separate recognizable control flow, with cross-module
   contracts and construction exposed through `@InternalKaliumApi`.
4. Preserve sender verification and arbitration exactly: lookup first; unchanged lookup failures; the existing warning
   and `DataNotFound` on mismatch; arbitration only for an already-edited regular message of the matching content type;
   strict local-timestamp `>` comparison; and equality on the incoming path.
5. Preserve local-newer payload copies exactly. Text replaces only new content and mentions, retaining incoming link
   previews; multipart replaces text, mentions, and attachments. Both retain the incoming signaling ID, use the local
   edit timestamp, skip notifications/status, and return the edit result unchanged.
6. Preserve incoming/non-arbitrated ordering and short-circuiting: notify before edit; update status only after a
   successful incoming edit of an already-edited matching message; perform no status update for other stored states;
   propagate returned failures, exceptions, and cancellation exactly.
7. Compose one session-scoped DAO-backed persistence instance and supply it to both extracted handlers and to
   `MessageDataSource`, whose still-used outgoing text/multipart edit methods delegate to the same implementation. Keep
   application-message routing unchanged and delegate only the existing two branches. This historical plan kept
   `NewMessageEventHandler` in `:logic`; the later handler extraction is documented above.
8. Move and strengthen both handler suites in the owning module; add focused persistence mapping/failure/cancellation
   coverage and exact app-facade routing coverage; validate receiving/logic JVM tests, root detekt, iOS Simulator ARM64
   compilation, and diff hygiene; then stop before quote handling, ignored/no-op extraction, deletion, asset/calling,
   facade/orchestration, NSE runtime, or process locking.

## Historical in-call-emoji application-message slice plan

Inspection of the inline `InCallEmoji` branch, `InCallReactionsRepository`/`InCallReactionsDataSource`, `CallsScope`,
`ObserveInCallReactionsUseCase`, `ApplicationMessageHandler`, `UserSessionScope`, and their tests gives this concrete
extraction plan:

1. Move `InCallReactionsRepository` and `InCallReactionsDataSource` to `:domain:messaging:receiving` without changing
   their package, and expose their cross-module contract and construction through `@InternalKaliumApi`.
2. Preserve the single `MutableSharedFlow` exactly: no replay, 32 slots of extra buffer capacity, `DROP_OLDEST` overflow,
   one emission per handled message, and filtering by the observer's conversation ID.
3. Add `InCallEmojiMessageHandler` and its implementation below `:logic`. Forward the signaling envelope's conversation
   ID and sender user ID plus the existing `content.emojis.keys` set directly, retaining suspension, exception, and
   cancellation behavior.
4. Delegate only the `InCallEmoji` branch from `ApplicationMessageHandlerImpl`; leave every other signaling branch and
   `NewMessageEventHandler` routing unchanged.
5. Compose one lazy session-scoped data-source instance in `UserSessionScope`, use it for the focused incoming handler,
   and continue passing that same instance to `CallsScope` observers so producer and consumer never split across streams.
6. Keep the flow process-local and ephemeral. Do not add NSE runtime wiring, durability, process locking, retries, rollout
   switches, or error conversion in this slice.
7. Move repository tests to the owning module, add focused forwarding/propagation and logic routing/shared-stream tests,
   validate JVM/iOS compilation and detekt, then stop before `Ignored`, delete/edit/calling/asset/clear-content leaves,
   facade extraction, or broader orchestration.

## Historical client-action application-message slice plan

Inspection of the inline client-action branch, `Message.System`, `MessageContent.ClientAction`/`CryptoSessionReset`,
`PersistMessageUseCase`, `ApplicationMessageHandler`, `UserSessionScope`, and their tests gives this concrete extraction
plan:

1. Add `ClientActionMessageHandler` and `ClientActionMessageHandlerImpl` to `:domain:messaging:receiving` in the existing
   application-message handler package, with cross-module construction exposed through `@InternalKaliumApi`.
2. Preserve the exact two log messages and their order around construction of one crypto-session-reset `Message.System`,
   forwarding the signaling envelope's ID, conversation ID, date, sender user ID, status, and sender user name while
   keeping expiration data null.
3. Depend only on the existing `PersistMessageUseCase`; invoke it once, ignore either returned `Right` or `Left`, and let
   ordinary exceptions and cancellation escape unchanged without filtering, retries, hooks, or notifications.
4. Delegate only the ClientAction branch and compose the leaf in `UserSessionScope` with the same persistence instance
   retained by `ApplicationMessageHandlerImpl` for regular messages. This historical plan kept
   `NewMessageEventHandler` in `:logic`; the later handler extraction is documented above.
5. Add focused mapping/result/propagation tests plus exact-envelope routing coverage, validate JVM/iOS compilation and
   detekt, then stop before Ignored, InCallEmoji, delete/edit leaves, facade extraction, or NSE runtime work.

## Historical availability application-message slice plan

Inspection of the inline availability branch, `UserRepository.updateOtherUserAvailabilityStatus`,
`AvailabilityStatusMapper`, `UserDAO.updateUserAvailabilityStatus`, `ApplicationMessageHandler`, `UserSessionScope`, and
their tests gives this concrete extraction plan:

1. Move `AvailabilityStatusMapper` unchanged to `:data:data-mappers`, keep its package stable, expose cross-module use
   through `@InternalKaliumApi`, and retain exact model/DAO/protobuf mappings, including null and unrecognized values to
   `NONE`.
2. Add a focused `IncomingAvailabilityPersistence` in `:domain:messaging:receiving`, backed directly by
   `UserDAO.updateUserAvailabilityStatus`, with the same sender-ID and availability-status mapping.
3. Preserve the direct failure contract: do not wrap, catch, log, retry, or convert the DAO result; ordinary exceptions
   and cancellation continue escaping unchanged.
4. Add `AvailabilityMessageHandlerImpl` below `:logic`; keep the exact pre-persistence log message, signaling-envelope
   sender, content status, single persistence call, and absence of filters or side effects.
5. Delegate only the Availability branch, remove `UserRepository` from `ApplicationMessageHandlerImpl`, remove the
   now-unused repository facade, and compose one focused adapter and handler in `UserSessionScope` without NSE-specific
   wiring.
6. Move and expand mapper coverage, add focused adapter/handler forwarding and propagation tests, retain routing coverage
   in `:logic`, validate JVM/iOS compilation and detekt, then stop before every other application-message leaf or NSE
   orchestration.

## Historical composite-edit application-message slice plan

Inspection of `MessageCompositeEditHandlerImpl`, `MessageRepository.getMessageById` and `updateCompositeMessage`,
`MessageMetadataRepository`, `CompositeMessageRepository`, their DAO implementations, `ApplicationMessageHandler`,
`UserSessionScope`, and tests gives this concrete extraction plan:

1. Reuse `MessageMetadataRepository.originalSenderId` for sender verification. Its DAO reads the same stored
   `Message.sender_user_id` selected through the broad message-details lookup, returns null for a missing row, and retains
   `wrapStorageRequest` failure/cancellation behavior without moving broad message-entity mapping below `:logic`.
2. Extend `CompositeMessageRepository` with the existing incoming composite-content update, backed by the same
   `MessageDAO.updateCompositeMessageContent` call and exact text/button mapping. Compose one shared focused repository
   instance for incoming edits, incoming button handlers, and outgoing composite-message consumers.
3. Move `MessageCompositeEditHandler` and its implementation to `:domain:messaging:receiving` without changing their
   package, expose cross-module construction through `@InternalKaliumApi`, and depend only on the two focused repositories.
4. Preserve exact behavior: use envelope conversation/message metadata, return lookup failures unchanged, treat missing
   or mismatched senders as `DataNotFound`, skip rejected updates, forward the complete edit payload and signaling
   ID/date, preserve ordered button/text mapping, and return the update result unchanged.
5. Remove only the now-unused composite-update facade from `MessageRepository`; retain `getMessageById`. This historical
   plan kept `NewMessageEventHandler` in `:logic`; the later handler extraction is documented above. Move and expand
   focused handler/DAO-adapter tests, retain routing coverage,
   validate JVM/iOS compilation and detekt, then stop before other edit/delete leaves, notification side effects, or NSE
   orchestration.

## Historical delete-for-me application-message slice plan

Inspection of `DeleteForMeHandlerImpl`, `IsMessageSentInSelfConversationUseCaseImpl`, `SelfConversationIdProviderImpl`,
`ClientRepository.hasRegisteredMLSClient`, the cached MLS/Proteus self-conversation providers,
`MessageRepository.deleteMessage`, their DAO/storage paths, `ApplicationMessageHandler`, `UserSessionScope`, and existing
tests gives this concrete extraction plan:

1. Add a narrow `:domain:messaging:shared` module below both `:logic` and `:domain:messaging:receiving`, with no dependency
   on `:logic`, receiving, or sending. Move `SelfConversationIdProvider`, its implementation and success-only per-protocol
   cache there without changing their packages or contracts. Replace the provider's broad repository dependencies with a
   focused MLS-registration-status provider backed by `ClientRegistrationStorage` and focused self-conversation-ID
   persistence backed by `ConversationDAO`.
2. Preserve self-conversation resolution exactly: request Proteus first; read MLS-registration status and treat its
   wrapped failure as `false`; request MLS only when registered; return IDs in Proteus-then-MLS order; propagate protocol
   lookup failures; cache only successful protocol IDs; and make verification fail closed by converting a provider failure
   to an empty list before checking the signaling message's conversation ID.
3. Keep `ClientRepository.hasRegisteredMLSClient` because it has other production callers, but make `ClientDataSource`
   delegate it to the same focused registration-status implementation used by the shared self-conversation provider.
   Once the provider no longer calls `ConversationRepository.getProteusSelfConversationId` or
   `getMLSSelfConversationId`, remove those now-unused internal repository methods rather than retaining test-only API;
   keep the underlying DAO lookups solely behind the focused persistence implementation.
4. Add one focused message-deletion persistence contract backed by `MessageDAO.deleteMessage`, retaining qualified-ID
   mapping and `wrapStorageRequest`. Keep `MessageRepository.deleteMessage` because it has other production callers, and
   make `MessageDataSource` delegate it to the same focused implementation supplied to the extracted handler.
5. Move `IsMessageSentInSelfConversationUseCase`, `DeleteForMeHandler`, and `DeleteForMeHandlerImpl` to
   `:domain:messaging:receiving`, exposing cross-module construction only through `@InternalKaliumApi`. Preserve
   verification against the signaling envelope, deletion of the payload's conversation/message IDs, the ignored delete
   `Either`, delete-before-hook order, hook invocation after either returned `Right` or wrapped `Left`, skipped hook after
   an escaping exception/cancellation, hook payload/self-user ID, and the existing log-only unverified-sender branch.
6. Compose one instance of each focused implementation in `UserSessionScope`; continue supplying the provider from
   `:domain:messaging:shared` to its existing outgoing messaging, analytics, and calling consumers while rewiring only the
   application facade's delete-for-me leaf. Do not add NSE-specific adapters, runtime switches, or process locking.
7. Keep the shared module narrow: do not move `SelfTeamIdProvider`, `CurrentClientIdProvider`, unrelated session state, or
   introduce a public generic cache framework in this milestone. The success-only cache helper remains an implementation
   detail of self-conversation resolution; `:domain:userstorage` continues to own database-instance caching and its
   separate configurable cache-scope policy.
8. Move and expand provider/verifier characterization tests, and add focused tests for storage mapping, `DataNotFound`,
   wrapped exceptions, cancellation, success-only caching, call order, fail-closed verification, ignored delete failures,
   delete/hook order, exact hook payload, and the unverified no-op path. Retain application-message routing coverage in
   `:logic`, validate shared/receiving/logic JVM tests, detekt, and iOS Simulator ARM64 compilation, then stop before
   delete-for-everyone, edit, last-read, clear-content, asset/notification side effects, unpacking, or NSE orchestration.

## Historical last-read application-message slice plan

Inspection of `LastReadContentHandlerImpl`, `ConversationRepository.updateReadDatesAndGetHasUnreadEvents`,
`ConversationDAO`, `ApplicationMessageHandler`, `UserSessionScope`, and their tests gives this concrete extraction plan:

1. Add a focused `IncomingLastReadPersistence` contract in `:domain:messaging:receiving`, backed directly by
   `ConversationDAO.updateReadDatesAndGetHasUnreadEvents`. Preserve qualified-ID mapping in both directions,
   `wrapStorageRequest`, the exact `Either<StorageFailure, Map<ConversationId, Boolean>>` result, and cancellation
   propagation.
2. Move `LastReadContentHandler` and `LastReadContentHandlerImpl` to `:domain:messaging:receiving` without changing their
   package, and expose cross-module construction through `@InternalKaliumApi`. This historical plan kept
   `NewMessageEventHandler` in `:logic`; the later handler extraction is documented above.
3. Preserve filtering and aggregation exactly: invoke self-conversation verification for every handled message; buffer
   only self-user senders in a self conversation; retain only the newest timestamp per conversation; accumulate distinct
   conversations; and keep buffer mutation and snapshots under the existing mutex.
4. Preserve flush behavior exactly: return for an empty buffer; snapshot and clear before one persistence call; notify
   only returned entries without unread events in returned order; log a returned failure without requeueing; and retain
   existing exception, cancellation, and log timing behavior.
5. Remove the broad repository method after confirming the handler is its sole production caller, while retaining the DAO
   method used by persistence tests and benchmarks. Compose one session-scoped handler instance in `UserSessionScope` so
   handling and pending-side-effect flushes share the same in-memory buffer.
6. Move and expand handler characterization tests and add DAO-adapter tests for filtering, verifier invocation,
   aggregation, exact snapshot payloads, empty/repeated flushes, notification selection/order, returned failures without
   retry, input/output mapping, wrapped failures, and cancellation propagation.
7. Keep application-message routing and flush delegation unchanged, validate receiving/logic JVM tests, detekt, and iOS
   Simulator ARM64 compilation, then stop before message-composite-edit or any later extraction or NSE orchestration.

## Historical reaction application-message slice plan

Inspection of `PersistReactionUseCaseImpl`, `ReactionRepository.updateReaction`, `ReactionDAO.updateReactions`,
`ApplicationMessageHandler`, `UserSessionScope`, and their tests gives this concrete extraction plan:

1. Move `PersistReactionUseCase` and `PersistReactionUseCaseImpl` to `:domain:messaging:receiving` without changing their
   package names or `Either` contract. Preserve legacy heavy-black-heart normalization, conversion to a set, all incoming
   payload values, and the existing post-result hook timing.
2. Add one focused `IncomingReactionPersistence` contract backed directly by `ReactionDAO.updateReactions`, retaining
   qualified-ID mapping and `wrapStorageRequest`. After moving the use case leaves `ReactionRepository.updateReaction`
   unused outside tests, remove that method instead of retaining dead API while leaving outgoing insert/delete,
   self-reaction lookup, observation, and `ReactionsMapper` in `:logic`.
3. Compose one `IncomingReactionPersistenceImpl` in `UserSessionScope` and supply it to the extracted use case. Keep
   application-facade behavior unchanged and rewire it only through existing composition; do not add an NSE adapter or
   runtime switch.
4. Move the use-case characterization tests with the implementation, add focused DAO forwarding/failure tests, and retain
   broad-repository integration coverage in `:logic`. Stop before delete-for-me, edit/delete, last-read, clear-content,
   call, unpacking, or NSE orchestration work.

## Historical receipt application-message slice plan

Inspection of `ReceiptMessageHandler`, `MessageRepository.updateMessagesStatusIfNotRead`,
`ReceiptRepository.persistReceipts`, `MessageDAO`, `ReceiptDAO`, the receipt models/mappers and their tests, and
`UserSessionScope` gives this concrete extraction plan:

1. Add one focused `IncomingReceiptPersistence` contract in `:domain:messaging:receiving`, with an `Either`-returning
   referenced-message-status operation backed by `MessageDAO.updateMessagesStatusIfNotRead` and a direct receipt-insert
   operation backed by `ReceiptDAO.insertReceipts`. Preserve `wrapStorageRequest` for the status operation and direct
   exception propagation for receipt insertion.
2. Make the broad `ReceiptRepository` delegate its overlapping insert operation to that same focused implementation.
   After moving the handler leaves `MessageRepository.updateMessagesStatusIfNotRead` unused outside tests, remove that
   receipt-specific facade from `MessageRepository`/`MessageDataSource` instead of retaining dead API. Compose one
   DAO-backed instance in `UserSessionScope` for the broad receipt repository and extracted handler; do not introduce an
   NSE-specific adapter.
3. Move only the receipt-type-to-message-status and receipt-type-to-DAO-enum conversions into shared mapper functions
   below `:logic`. Keep detailed-receipt-to-`UserSummary`, entity-to-model observation mapping, and all unrelated mapper
   dependencies in `:logic`.
4. Move `ReceiptMessageHandler` and `ReceiptMessageHandlerImpl` to `:domain:messaging:receiving`, with cross-module access
   guarded by `@InternalKaliumApi`. Preserve the self-sender early return, ignored status-update `Either`,
   status-before-receipt order, direct receipt-insert failure propagation, and read-hook timing and payload exactly.
5. Keep the existing repository-backed characterization tests in `:logic`, and add focused receiving-module tests for
   the combined DAO adapter plus handler mapping, operation order, caught status-write failures, propagated receipt-write
   failures, self-sender filtering, and `READ`-only post-insert hook invocation.
6. Stop before delete-for-me, edit, clear-content, calling, unpacking, MLS, legal hold, pending side effects, or any NSE
   facade/orchestration work. After this slice, reassess the remaining application-message leaves by their concrete
   local-persistence closures.

## Historical data-transfer receiver slice plan

Inspection of `DataTransferEventHandler`, `UserConfigRepository`, and `UserConfigDAO` gives this concrete extraction plan:

1. Add a focused `TrackingIdentifierStorage` contract in `:domain:messaging:receiving`, backed directly by the existing
   `UserConfigDAO`. Limit it to the handler's current-identifier read and current/previous-identifier writes. Keep writes
   wrapped by `wrapStorageRequest` so non-cancellation failures are caught and logged, while leaving the current-identifier
   read as a direct DAO call so getter exceptions continue to propagate.
2. Make `UserConfigDataSource` delegate its overlapping current read and current/previous writes to that same focused
   implementation. Leave observation, previous-identifier reads/deletion, and every unrelated user-config operation in
   `:logic` because they are not part of the handler's compile-time closure.
3. Move `DataTransferEventHandler` and `DataTransferEventHandlerImpl` unchanged in behavior to
   `:domain:messaging:receiving`, with cross-module construction exposed only through `@InternalKaliumApi`. Compose the
   concrete DAO-backed storage and extracted handler from the existing `UserSessionScope`. This historical plan kept
   `NewMessageEventHandler` and `ConversationEventReceiverImpl` in `:logic`; both have since moved in later extraction
   slices.
4. Preserve the early return for another sender or a null identifier; read-before-compare behavior; no-op for an unchanged
   identifier; previous-before-current write order; continuation after a caught setter failure; and the two existing
   success log points. Move the existing handler characterization tests with the implementation and add focused adapter
   tests for DAO forwarding plus setter-catching/getter-propagation behavior.
5. Stop before delete-for-me, receipts, message unpacking, MLS, legal hold, or pending side effects. Reassess the
   delete-for-me and receipt DAO leaves only after this isolated slice is validated on JVM and iOS Simulator ARM64.

## Extracted coherent slice

The following concrete receivers now live in `:domain:messaging:receiving`, and `:logic` composes those same implementations:

- The new-message orchestration slice
  - `NewMessageEventHandlerImpl`, with the same Proteus/MLS classification, persistence, batch, logging, legal-hold,
    content-routing, confirmation, self-deletion, flush, exception, and cancellation behavior;
  - the complete handler suite, using local event/transaction fixtures and focused callback recorders without dropping
    any pre-existing test;
  - legal hold, stale-epoch verification, and MLS reset cross only focused suspend callbacks, while `UserSessionScope`
    supplies the same `LegalHoldHandler`, `StaleEpochVerifier`, `ResetMLSConversationUseCase`, ephemeral self-deletion,
    and confirmation-delivery actions.
- `UserPropertiesEventReceiverImpl`
  - local user-property persistence through `UserConfigStorage`;
  - local conversation-folder persistence through `ConversationFolderDAO`.
- `TeamEventReceiverImpl`
  - local user/member cleanup through `UserDAO`;
  - the existing `PersistMessageUseCaseImpl` message-persistence path;
  - DAO-backed message insertion and receipt-mode lookup through the focused `EventMessageRepositoryImpl`.
- `FederationEventReceiverImpl`
  - local connection cleanup through `ConnectionDAO` and `UserDAO`;
  - federated-member lookup and cleanup through `MemberDAO`;
  - the same message-persistence path used by the app.
- `FeatureConfigEventReceiverImpl` and all handlers routed by it
  - local feature persistence through `UserConfigStorage` and `UserConfigDAO`;
  - meeting slow-sync invalidation through the focused `MetadataDAO` implementation;
  - existing supported-protocol and crypto-transaction operations through lower-level contracts implemented by the app's
  existing concrete types.
- The first conversation-event leaf slice
  - `TypingIndicatorHandlerImpl`, `ReceiptModeUpdateEventHandlerImpl`,
    `ConversationMessageTimerEventHandlerImpl`, `AccessUpdateEventHandlerImpl`,
    `ChannelAddPermissionUpdateEventHandlerImpl`, `CodeUpdateHandlerImpl`, and `CodeDeletedHandlerImpl`;
  - receipt-mode, message-timer, access-role, channel-permission, and guest-link writes through the focused DAO-backed
    `ConversationEventRepositoryImpl`;
  - the shared `SystemMessageInserterImpl` used by the access handler and the shared incoming-typing cache behind the
    focused `TypingIndicatorStatusProvider` boundary.
- The lifecycle-persistence and rename slice
  - `RenamedConversationEventHandlerImpl`, with the same success-only rename system-message persistence and event logging;
  - the separate DAO-backed `ConversationLifecycleEventRepositoryImpl` for rename, modified-date, local deletion,
    deleted-flag, member insert/delete, member-role read/update, muted-status, and archive-status operations;
  - `ConversationDataSource` delegates every overlapping local operation to that same implementation, while the
    new-conversation, join, leave, and member-change handlers use the focused repository directly for their extracted
    local mutations.
- The grouped small conversation-state lifecycle slice
  - `MemberChangeEventHandlerImpl`, retaining muted/archive local writes and success logs, unsupported-variant skipping,
    role-read-before-fetch behavior, fetch logging, update-after-fetch-failure behavior, update-controlled promotion,
    the exact promotion message, ignored persistence results, and direct exception/cancellation propagation;
  - fetch-if-unknown crosses only the focused
    `suspend (CryptoTransactionContext, ConversationId) -> Either<CoreFailure, Unit>` callback, while `UserSessionScope`
    captures one exact `FetchConversationIfUnknownUseCase` instance per handler construction and preserves its default
    `ConversationSyncReason.Other` invocation;
  - `MLSResetConversationEventHandlerImpl`, retaining null-MLS no-op behavior and exact leave/check/conditional-epoch/
    update ordering, ignored leave/update results, failed-check fallback, exact epoch/state mapping, and uncaught
    exception/cancellation behavior;
  - `MLSResetEventRepository` exposes only those three reset operations; the existing broad `MLSConversationRepository`
    extends it, and `UserSessionScope` passes the exact observable repository wrapper constructed for the handler so the
    success-only leave hook and all delegate behavior remain unchanged.
- The protocol-update conversation slice
  - `ProtocolUpdateEventHandlerImpl`, its private no-conversation classifier, and its complete tests retain update-first,
    message-before-call-query ordering, deleted-conversation classification, logging, and `Either` behavior;
  - protocol mutation and established-call state cross only focused suspend callbacks, while the receiving-owned
    `SystemMessageInserter` remains a direct dependency;
  - `UserSessionScope` captures the exact update use case, call repository, and system-message inserter once per handler
    construction; the call callback retains `establishedCallsFlow().first().isNotEmpty()` and remains evaluated before
    the `MIXED` protocol check.
- The first application-message leaf slice
  - `ButtonActionHandlerImpl` and `ButtonActionConfirmationHandlerImpl`;
  - the existing `CompositeMessageDataSource` and `MessageMetadataSource` DAO adapters used by both incoming button
    handlers and the app's outgoing composite-message paths.
- The data-transfer application-message leaf
  - `DataTransferEventHandlerImpl`, retaining its self-sender/null-identifier filtering, read-before-compare behavior,
    previous-before-current writes, no-op case, and existing analytics logging;
  - the focused DAO-backed `TrackingIdentifierStorageImpl` for the handler's current-identifier read and
    current/previous-identifier writes;
  - `UserConfigDataSource` delegates those three overlapping operations to the same implementation composed by
    `UserSessionScope`, while tracking-identifier observation and previous-identifier reads/deletion remain app-owned.
- The receipt application-message leaf
  - `ReceiptMessageHandlerImpl`, retaining the self-sender early return, status-before-receipt ordering, ignored
    status-update failure, direct receipt-insert exception propagation, and `READ`-only post-insert hook;
  - the focused DAO-backed `IncomingReceiptPersistenceImpl`, retaining the existing `Either`/`wrapStorageRequest`
    contract for status updates and direct DAO failure behavior for receipt insertion;
  - `ReceiptRepositoryImpl` delegates its overlapping insert operation to the same focused instance composed by
    `UserSessionScope` and supplied to the extracted handler; the now-unused receipt-status facade was removed from
    `MessageRepository`/`MessageDataSource`, while the underlying DAO operation remains behind the focused adapter;
  - only receipt-type-to-message-status and receipt-type-to-DAO-enum mapping moved to `:data:data-mappers`; detailed
    receipt/user-summary observation mapping remains in `:logic`.
- The reaction application-message leaf
  - `PersistReactionUseCaseImpl`, retaining legacy `"❤"` to `"❤️"` normalization, conversion of the resulting emojis to
    a set, the original message/conversation/sender/date payload, and its existing `Either` result;
  - the focused DAO-backed `IncomingReactionPersistenceImpl`, retaining qualified-ID mapping and `wrapStorageRequest` for
    `ReactionDAO.updateReactions`;
  - the now-unused `ReactionRepository.updateReaction` facade was removed instead of retaining test-only API, while
    outgoing insert/delete, self-reaction lookup, observation, `ReactionsMapper`, and user-summary mapping remain in
    `:logic`;
  - the reaction-persisted hook still runs after either `Right` or wrapped `Left`, and is skipped when cancellation or
    another uncaught failure escapes before the DAO operation returns.
- The delete-for-me application-message leaf
  - `DeleteForMeHandlerImpl` and `IsMessageSentInSelfConversationUseCaseImpl`, retaining signaling-envelope verification,
    payload-ID deletion, the ignored delete result, delete-before-hook ordering, exact hook payload/self-user ID, skipped
    hooks after escaping failures, and the existing log-only unverified branch;
  - the shared `SelfConversationIdProviderImpl` and its private success-only per-protocol caches now live in the narrow
    `:domain:messaging:shared` module, with Proteus-first resolution and DAO-backed MLS/Proteus lookups;
  - the focused `MLSClientRegistrationStatusProviderImpl` and `MessageDeletionPersistenceImpl` retain
    `wrapStorageRequest` behavior, ID mapping, and cancellation propagation;
  - `ClientDataSource` and `MessageDataSource` delegate their still-used broad operations to the same focused instances
    composed in `UserSessionScope`, while the now-unused Proteus/MLS self-conversation methods were removed from
    `ConversationRepository`.
- The last-read application-message leaf
  - `LastReadContentHandlerImpl`, retaining per-message self-conversation verification, self-sender filtering,
    newest-per-conversation buffering, mutex-protected snapshot-and-clear behavior, and empty-flush short-circuiting;
  - the focused DAO-backed `IncomingLastReadPersistenceImpl`, retaining qualified-ID mapping in both directions,
    `wrapStorageRequest`, returned storage failures, and cancellation propagation;
  - returned entries without unread events still schedule conversation-seen notifications in iteration order, while
    returned persistence failures leave the cleared snapshot unqueued; the now-unused broad repository facade was removed
    while the underlying DAO method remains available to its focused adapter, benchmarks, and persistence tests;
  - `UserSessionScope` owns one shared application handler instance for message handling and pending-side-effect flushes;
    the receiving-owned `NewMessageEventHandler` delegates to that same receiving-owned application facade.
- The composite-edit application-message leaf
  - `MessageCompositeEditHandlerImpl`, retaining signaling-envelope conversation/message ID/date forwarding, exact
    original-sender enforcement, the existing mismatch warning and `DataNotFound` result, lookup/update failure
    propagation, and no update after a rejected lookup;
  - the existing `MessageMetadataSource` performs the sender lookup, while the extended `CompositeMessageDataSource`
    maps optional text and ordered buttons to the same `MessageDAO.updateCompositeMessageContent` call with
    `wrapStorageRequest` and cancellation behavior unchanged;
  - `UserSessionScope` shares one focused composite repository across this handler, incoming button handlers, and
    outgoing composite-message consumers; the now-unused broad composite-update facade was removed from
    `MessageRepository`, while broad message lookup/mapping remains in `:logic` for its other callers.
- The availability application-message leaf
  - `AvailabilityMessageHandlerImpl`, retaining the exact pre-persistence log message and forwarding the signaling
    envelope sender plus content status exactly once without filtering or additional side effects;
  - the focused DAO-backed `IncomingAvailabilityPersistenceImpl`, retaining direct exception and cancellation
    propagation plus the same qualified-ID and status mappings for `UserDAO.updateUserAvailabilityStatus`;
  - `AvailabilityStatusMapper` now lives in `:data:data-mappers` with model/DAO/protobuf mappings unchanged, including
    null and unrecognized values to `NONE`; the now-unused availability facade was removed from `UserRepository`, and
    `ApplicationMessageHandlerImpl` no longer depends on that broad repository.
- The client-action application-message leaf
  - `ClientActionMessageHandlerImpl`, retaining both exact logs in their original order and mapping one signaling envelope
    to the same crypto-session-reset `Message.System` with null expiration data;
  - the handler uses the same `PersistMessageUseCase` instance retained by `ApplicationMessageHandlerImpl` for regular
    messages, invokes it once, ignores either returned `Right` or `Left`, and lets exceptions and cancellation escape;
  - `ApplicationMessageHandlerImpl` delegates only its ClientAction branch; the receiving-owned
    `NewMessageEventHandler` continues to invoke the same application facade.
- The in-call-emoji application-message leaf
  - `InCallEmojiMessageHandlerImpl`, retaining signaling-envelope conversation/sender IDs and forwarding the existing
    emoji-key set exactly once, with direct suspension, exception, and cancellation behavior unchanged;
  - `InCallReactionsRepository` and `InCallReactionsDataSource` retain their existing package and exact process-local
    `MutableSharedFlow` replay, extra-buffer, overflow, emission, and conversation-filtering behavior;
  - `UserSessionScope` owns one shared repository instance used by both the incoming handler and `CallsScope` observers;
    the stream intentionally remains ephemeral and is not an NSE cross-process transport or durable persistence layer;
  - `ApplicationMessageHandlerImpl` delegates only its `InCallEmoji` branch; the receiving-owned
    `NewMessageEventHandler` continues to invoke the same application facade.
- The paired text/multipart-edit application-message slice
  - `MessageTextEditHandlerImpl` and `MessageMultipartEditHandlerImpl`, retaining lookup-first behavior, exact sender
    enforcement/warning, matching-content and edited-state arbitration, strict local `>` timestamp comparison, and
    equality on the incoming path;
  - local-newer text edits retain incoming link previews while copying stored text/mentions, and local-newer multipart
    edits copy stored value/mentions/attachments; both use the incoming signaling ID and local edit timestamp without
    notification or status mutation;
  - incoming and non-arbitrated paths retain notification-before-edit ordering, while only a successful incoming edit of
    an already-edited matching message proceeds to mark the resulting message `SENT`;
  - the focused DAO-backed `MessageEditPersistenceImpl` maps only sender plus the minimal arbitration fields, preserves
    qualified IDs, mention/link-preview mapping, multipart null-text fallback, stored attachment mapping, wrapped
    failures/cancellation, and the current absence of multipart attachment writes;
  - `UserSessionScope` supplies one persistence instance to both handlers and `MessageDataSource`, whose still-used broad
    outgoing text/multipart edit methods delegate to that same implementation; broad message lookup and unrelated
    repository APIs remain in `:logic`.
- The delete-message application-message leaf
  - `DeleteMessageHandlerImpl`, retaining lookup-first behavior, stored-sender verification, the self-authored ephemeral
    exception, hard-delete versus notification-before-tombstone selection, post-lookup asset cleanup even for an
    unverified sender, ignored returned failures, and incoming-ID hook payloads in the original order;
  - the focused DAO-backed `IncomingMessageDeletionPersistence` maps only stored IDs, sender, regular-message ephemeral
    state, and optional remote asset ID, while reusing the existing `MessageDeletionPersistence` hard-delete path and
    adding the same wrapped tombstone operation;
  - the named `DeleteMessageAssetCleanup` port keeps asset-cleanup policy below `:logic`, while the session-scoped
    main-app adapter delegates directly to the unchanged `AssetRepository.deleteAssetLocally` implementation; direct
    `AssetDAO`/`KaliumFileSystem` infrastructure remains app-owned and retains its characterized failure behavior;
  - `MessageDataSource` delegates hard-delete and tombstone operations to the same focused persistence supplied to the
    handler, and both delete-notification entry points use the same ID-based mapper/manager implementation;
  - `ApplicationMessageHandlerImpl` delegates only its existing `DeleteMessage` branch; the receiving-owned
    `NewMessageEventHandler` continues to invoke the same application facade, and no asynchronous execution, outbox,
    pending action, retry, or NSE-specific adapter was added.
- The clear-conversation-content application-message leaf
  - `ClearConversationContentHandlerImpl`, retaining full signaling-envelope self-conversation verification, the exact
    sender/verifier authorization equality check, payload conversation IDs, clear/assets/hook/delete order, optional
    self-sender deletion rule, ignored returned failures, and uncaught exception/cancellation short-circuiting;
  - `ConversationLifecycleEventRepositoryImpl` now owns the existing wrapped `ConversationDAO.clearContent` operation,
    and `ConversationDataSource` delegates its overlapping broad API to the same instance composed in
    `UserSessionScope`;
  - the named `ClearConversationAssetsLocally` and `WholeConversationDeletion` ports keep policy visible below `:logic`,
    while thin main-app adapters delegate to the unchanged filesystem/asset cleanup and MLS-aware deletion use cases;
  - `AssetRepository`, `MessageRepository`, `AssetDataSource`, `KaliumFileSystem`, `ConversationRepository`,
    `MLSConversationRepository`, `DeleteConversationUseCase`, CoreCrypto wiping, and proposal timers remain in `:logic`;
    `NewMessageEventHandler` is receiving-owned and continues to invoke these app actions only through its focused
    composition callbacks and application facade.

Supporting ID, folder, feature-config, self-deletion, and supported-protocol mappers were moved to `:data:data-mappers`.
The outgoing message-entity mapper and the link-preview, mention, attachment, encryption, and conversation-protocol
mappers in its closure were also moved there so the focused message repository does not depend on `:logic`.
The broad repositories in `:logic` delegate still-used overlapping operations to the same lower-level concrete
implementations, while internal facades left without production callers are removed; there is no NSE-specific or
duplicate repository implementation.

The extraction-induced Konsist failures are also resolved. App layer rules now inspect only `:logic`, feature-config
receiver dependencies live outside the app `feature` package, and internal-only lower-module implementations may retain
the public constructors required for cross-module composition without becoming exported `KaliumLogic` product API.

## Completed ProtocolUpdateEventHandler extraction slice

1. `ProtocolUpdateEventHandler`, `ProtocolUpdateEventHandlerImpl`, and every pre-existing test now have one owner in
   `:domain:messaging:receiving` under their existing packages and FQCNs. No-conversation classification now reuses
   `CoreFailure.isConversationNotFoundError`; only cross-module public visibility was added.
2. `SystemMessageInserter` remains a direct receiving-owned dependency. The logic-owned
   `UpdateConversationProtocolUseCase` and `CallRepository` remain unexposed and cross the boundary only as
   `suspend (CryptoTransactionContext, ConversationId, Conversation.Protocol, Boolean) -> Either<CoreFailure, Boolean>`
   and `suspend () -> Boolean` callbacks.
3. `UserSessionScope` uses named constructor arguments and captures exactly one update use-case instance, the existing
   stable call-repository object, and one system-message-inserter instance for each handler construction. The callbacks
   do not re-resolve those getter-backed objects per event. Protocol updates still pass `localOnly = true`; call state
   still uses `establishedCallsFlow().first().isNotEmpty()` rather than a cached/current value.
4. The handler still updates first. Only a `ServerMiscommunication` wrapping an `InvalidRequestError` for a missing
   conversation, classified by `CoreFailure.isConversationNotFoundError`, becomes `Right(false)` with the existing
   informational log; all other returned failures are propagated. `Right(false)` skips both messages and the call query.
   `Right(true)` inserts the protocol message, queries established calls before testing `protocol == MIXED`, conditionally
   inserts the during-call message, logs success, and maps to `Unit` exactly as before.
5. The moved suite keeps every original test name and assertion intent, replaces logic-only fixtures with local event and
   network failures plus focused callback recorders, and adds exact order/argument, skip, non-`MIXED` query,
   first-flow-emission, classification, ordinary-exception, and cancellation characterization at every callback/message
   stage.
6. No update use-case/repository implementation, receiver, NSE runtime wiring, CoreCrypto process-safety change, retry,
   queue, durable action/outbox, rollout switch, or unrelated lifecycle handler moved in this slice.
   `ConversationEventReceiverImpl` was logic-owned at this slice and has since moved.

## Completed MLSWelcomeEventHandler extraction slice

1. `MLSWelcomeEventHandler`, `MLSWelcomeEventHandlerImpl`, their private helpers and structured outcome constants, and
   every pre-existing test now have one owner in `:domain:messaging:receiving` under their existing package and FQCNs.
   Only cross-module public visibility was added.
2. The focused receiving-owned `MLSWelcomeEventRepository` extends `ConversationProtocolGetter` and adds only
   `updateConversationGroupState(GroupID, GroupState): Either<StorageFailure, Unit>` and
   `observeConversationDetailsById(ConversationId): Flow<Either<StorageFailure, ConversationDetails>>`.
   The existing logic-owned `ConversationRepository` extends this contract and reuses its existing implementations and
   exact object identity; no broad conversation repository/data source, second adapter, or duplicate state was added.
3. The other logic-owned dependencies cross the boundary only as focused callbacks for fetch-if-unknown, one-to-one
   resolution, key-package refill, CRL checking, CRL persistence, and external-commit rejoin. `UserSessionScope` captures
   exactly one conversation repository, resolver, refill use case, CRL checker, CRL repository, join use case, and fetch
   use case per handler construction in precisely their original written evaluation order. Fetch and join retain their
   two-argument calls and defaults; refill maps only its returned `Success`/`Failure` result to `Either` without catching.
4. The handler still reads MLS first; runs fetch, Base64/process, ordered CRL checks/persistence, establish, first-detail
   observation, and optional one-to-one resolution in the same order; and preserves every returned-failure short circuit.
   Conversation-not-found, already-existing, orphan/local-established, external-commit recovery/failure, all other
   failure classifications, exact logs and outcome strings, refill behavior, exceptions, and cancellation are unchanged.
   The original `wrapInMLSContext` semantics are now supplied by the shared helper in
   `:domain:messaging:shared`.
5. The moved suite preserves all 14 original test names and assertion intent, uses local model/network/transaction
   fixtures plus a focused repository and callback recorders, and adds null-MLS, exact order/arguments, `Flow.first`,
   multiple-CRL, short-circuit, ignored-refill-failure, already-existing/orphan variants, join-failure, wrapped-exception,
   ordinary-exception, and cancellation characterization.
6. No receiver, NSE runtime wiring, CoreCrypto process-safety change, retry, queue, durable action/outbox, rollout switch,
   other lifecycle handler, or `UserEventReceiverImpl` work moved in this slice. `ConversationEventReceiverImpl` was
   logic-owned at this slice and has since moved.

## Completed grouped NewConversationEventHandler and DeletedConversationEventHandler extraction slice

1. `NewConversationEventHandler`, `DeletedConversationEventHandler`, their implementations, and all 13 pre-existing
   named tests now have one owner in `:domain:messaging:receiving` under their existing packages and FQCNs. Only the
   public visibility required for cross-module composition was added.
2. The focused receiving-owned `ConversationEventUserRepository` contains exactly unknown-user fetch and single-user
   observation. `NewConversationSystemMessagesCreator` contains exactly the five operations called by new-conversation
   handling with their original types/defaults. `DeletedConversationEventRepository` contains only conversation lookup.
   The existing broad logic repositories and system-message creator extend these contracts and reuse their current
   implementations; no broad repository/data source, adapter, or duplicate state moved below `:logic`.
3. `ConversationSyncReason` and its public artifact ownership remain unchanged in `:logic`. New-conversation crosses a
   focused two-argument event-persistence callback; main-app composition invokes the captured persistence use case with
   `reason = ConversationSyncReason.Event`, preserving the exact event persistence behavior without exposing the reason
   type below `:logic`.
4. New-conversation composition captures the existing lifecycle repository, user repository, self-team provider,
   system-message creator, one-to-one resolver, and persistence use case exactly once in original written argument order.
   Deleted-conversation composition captures the existing user repository, conversation repository, notification manager,
   deletion use case, hook notifier, and self user ID exactly once in original written argument order. The deletion use
   case is still constructed after the independent lookup-repository capture. Named callback wiring never re-resolves a
   getter during event handling, and type mapping composes the unchanged internal `toConversationType` extension.
5. New-conversation retains logger creation, ignored self-team failure, explicit event persistence, mapping/resolution,
   modified-date timing, unknown-user fetch, conditional system-message creation, exact five-message order, independently
   ignored returned message failures, and success/failure logging. Deleted-conversation retains lookup/delete chaining,
   `Flow.firstOrNull`, exact ephemeral notification data, meeting suppression after user observation, the normally
   unconditional handler hook after returned failures, and thrown-exception/cancellation short-circuiting before that hook.
6. The moved suites preserve all 6 original New names and all 7 original Deleted names and assertion intent. Local event,
   model, user, transaction, and time fixtures plus focused recorders replace logic-only fixtures and broad mocks. Added
   characterization pins exact order/identity/arguments, mapper count and group/one-to-one results, millisecond time bounds,
   returned failures versus thrown failures, every ignored system-message result, first/empty/null user-flow semantics,
   meeting behavior, final-hook behavior, ordinary exceptions, and cancellation at every stage.
7. No receiver routing, NSE runtime wiring, lock, retry, queue/outbox, async redesign, rollout switch, member join/leave,
   or `UserEventReceiverImpl` work moved in this slice. `MemberJoinEventHandler` and `MemberLeaveEventHandler` are the only
   remaining concrete conversation handlers in `:logic` at this slice; they and `ConversationEventReceiverImpl` have since
   moved. Durable or asynchronous deletion and main-app side-effect execution remain documented future work.

## Completed grouped MemberJoinEventHandler and MemberLeaveEventHandler extraction slice

1. `MemberJoinEventHandler`, `MemberLeaveEventHandler`, their implementations, all 14 pre-existing join tests, and all
   11 pre-existing leave tests now have one owner in `:domain:messaging:receiving` under their existing packages and
   FQCNs. The handler contracts remain ordinary interfaces; only the public visibility required for cross-module
   composition was added.
2. Both handlers reuse `ConversationLifecycleEventRepository`, `EventMessagePersistence`,
   `NewConversationSystemMessagesCreator`, `ConversationProtocolGetter`, and `MLSResetEventRepository`. The new focused
   `MemberJoinEventRepository`, `MemberJoinEventUserRepository`, and `MemberLeaveEventUserRepository` expose only the
   remaining lookup and user operations these handlers call. Existing broad logic repositories extend those contracts
   and provide the same objects and implementations without adapters, duplicate persistence, or moved broad graphs.
3. Fetch-conversation, legal-hold refresh, current-call client update, and self-team lookup cross the boundary as focused
   callbacks. `ConversationSyncReason` and `UpdateConversationClientsForCurrentCallUseCase` keep their existing public
   ownership in `:logic`. The captured fetch use case is invoked with its unchanged two-argument call, retaining the
   default `ConversationSyncReason.Other` behavior.
4. `UserSessionScope` captures every original constructor argument exactly once and in original written argument order.
   The existing call-update `Lazy` is captured once and its `.value` remains untouched until the callback runs after a
   successful member deletion. MLS cleanup keeps the event transaction's nullable MLS context and the same disabled
   fallback; no CoreCrypto context is acquired early or substituted.
5. Join preserves fetch-first behavior, ignored fetch/user-fetch/deleted-flag/lookup/message/action/legal-hold results,
   member-persistence result ownership, all conversation-type branches, one-to-one activation, exact warning and
   member-added messages, empty-ID UUID fallback, logging, exceptions, and cancellation. Leave preserves mark-first and
   delete/call/MLS/fetch/count/message/persist/legal-hold order, every ignored result, the zero-count short circuit,
   self-only MLS cleanup, team-deletion classification, exact message fields, legal-hold result ownership, logging,
   exceptions, and cancellation.
6. The moved suites retain every original test name and assertion intent. Focused local fixtures replace logic-only broad
   mocks and add exact order, arguments and context identity, zero-count and branch characterization, returned-failure
   ownership, lazy-stage timing, ordinary-exception, and cancellation coverage.
7. No receiver routing, NSE runtime wiring, CoreCrypto process-safety change, rollout flag, retry/outbox redesign, async
   side-effect redesign, broad repository/use-case/legal-hold/call graph, or `UserEventReceiverImpl` work moved in this
   slice. `ConversationEventReceiverImpl` is now the remaining conversation extraction target;
   `UserEventReceiverImpl` remains explicitly outside this goal.

## Completed ConversationEventReceiverImpl extraction slice

1. `ConversationEventReceiverImpl` and its focused test suite now have one owner in
   `:domain:messaging:receiving` under their existing package and FQCN. The `ConversationEventReceiver` contract remains
   owned by `:domain:event-processing`. Only the public class/constructor visibility required for cross-module construction
   was added; the class remains a normal class with the exact constructor parameter list, order, and types.
2. `UserSessionScope` continues to construct the same FQCN with the same dependency expressions and evaluation order, so
   main-app composition and receiver routing are unchanged. No handler contract, handler implementation, event contract,
   or module dependency changed in this slice, and receiving retains its guard against depending on `:logic`.
3. The original `when` branch order and result ownership are unchanged. Proteus/MLS messages, new/deleted conversations,
   member change, MLS welcome, rename, receipt mode, and MLS reset retain their existing ignored-result/`Right(Unit)`
   behavior; member join/leave, access, timer, code, typing, protocol, and channel-add-permission routes retain their
   existing returned `Either`. Transaction, event, and delivery objects are forwarded unchanged, and thrown exceptions
   and cancellation still escape.
4. `flushPendingSideEffects` still invokes `NewMessageEventHandler.flushPendingSideEffects` exactly once and then returns
   `Right(Unit)`; exceptions and cancellation still escape unchanged.
5. The moved suite preserves all 19 existing test names and assertion intent while replacing logic-only `TestEvent`,
   `TestUser`, and transaction-arrangement fixtures with focused local events, mocks, and reference-identity call
   recording. Added characterization pins join/leave `Left` propagation, ignored MLS-welcome `Left`, protocol/channel
   propagation, MLS-reset routing, flush behavior, exact transaction/event/delivery identity, and ordinary exception and
   cancellation propagation for event handling and flush.
6. This completes the conversation-handler ownership extraction goal only. It does not add NSE runtime composition,
   shared-storage bootstrap, changes to CoreCrypto process-safety behavior, rollout behavior, durable side effects,
   retries, queues/outboxes, or asynchronous redesign. `UserEventReceiverImpl` remains logic-owned and explicitly outside
   this goal.

## Completed MeetingEventReceiver extraction slice

1. `MeetingEventReceiverImpl`, its Create/Delete/Update/MemberAdd handlers, their shared fetch-and-upsert helper, and all
   five pre-existing test suites now have one owner in `:domain:messaging:receiving` under their existing packages and
   FQCNs. The `MeetingEventReceiver` contract remains owned by `:domain:event-processing`; only the public visibility
   required for cross-module composition was added.
2. The handlers now depend on the focused receiving-owned `MeetingEventRepository`. It exposes only event-triggered
   fetch-and-persist plus local deletion, and returns the neutral `MeetingEventFetchResult` classification for success,
   unsupported API, unsupported meeting model, and server-side absence. No broad meeting repository or transport
   exception type crosses into receiving.
3. The existing logic-owned `MeetingRepository` extends that focused contract and maps the same outcomes at the module
   boundary. `NetworkFailure.FeatureNotSupported`, `MeetingDataSource.MeetingNotSupportedFailure`, and the exact
   `404 meeting-not-found` response are still treated as successful skipped events; every other failure is propagated by
   identity. Successful fetches still persist the meeting before the handler logs event success.
4. `UserSessionScope` continues to pass the same `meetingRepository` instance to all four handlers and constructs the
   same receiver with the same handler order. Delete still performs only local deletion. Event transaction and delivery
   information remain accepted by the receiver contract and routing still forwards each meeting event to exactly one
   matching handler.
5. The moved handler suites cover every neutral skip result, failure propagation, local deletion, and all four receiver
   routes. Logic-side repository tests characterize the transport/model-to-event-result adapter so the behavior that
   formerly lived in the handlers remains protected at its new owner.
6. This is an ownership and dependency-boundary refactor only. It adds no NSE composition, network client, shared-storage
   bootstrap, lock, retry, queue/outbox, rollout switch, or asynchronous behavior. Meeting API/persistence construction
   remains a main-app concern until an NSE facade supplies an implementation of the focused contract.

After this slice, `UserEventReceiverImpl` is the only concrete event receiver still owned by `:logic`.

## Completed User receiver handler extraction slices 1-4

The first four planned User receiver slices are now complete without moving the legal-hold handlers or the final router:

1. `UserUpdateEventHandlerImpl` now lives in `:domain:messaging:receiving` behind `UserUpdateEventRepository`. It retains
   the exact successful update logging, local `DataNotFound` skip classification, other-failure propagation, and event
   arguments. The broad logic-owned `UserRepository` implements the focused contract with its existing method.
2. `NewClientEventHandlerImpl`, `ClientRemoveEventHandlerImpl`, and `UserDeleteEventHandlerImpl` are receiving-owned.
   `NewClientEventRepository` exposes only event persistence; current-client lookup and the two distinct logout actions
   cross captured callbacks. Current-client lookup failure still causes NewClient persistence but propagates from
   ClientRemove, same-client NewClient events are still skipped, self deletion still performs a completed-account logout,
   and other-user deletion still returns the repository result after mapping its unused conversation list to `Unit`.
3. `SessionRefreshSuggestedEventHandlerImpl` is receiving-owned and now owns the original live-failure versus
   pending-event-skip classification and logging. The focused `SessionRefreshRepository` is implemented by the
   logic-owned `SessionRefreshRepositoryImpl`, which retains refresh-token lookup, access-token refresh, cache clearing,
   missing-token failure, and `FailureToRefreshTokenException` mapping. Network container and session-manager types no
   longer cross into receiving.
4. `NewConnectionEventHandlerImpl` is receiving-owned behind `NewConnectionEventUserRepository` and
   `NewConnectionEventRepository`. The broad `UserRepository` classifies a successful fetch, HTTP not-found, and ordinary
   failure at the logic boundary; the broad `ConnectionRepository` exposes only previous connection status plus event
   insertion. One-to-one scheduling, lazy unverified-warning persistence, and legal-hold follow-up cross captured
   callbacks backed by the same objects formerly stored directly in `UserEventReceiverImpl`.
5. NewConnection preserves fetch-first behavior, the not-found continuation and warning, previous-status lookup with all
   lookup failures treated as absent, insertion ownership, accepted-only scheduling, live three-second versus pending
   zero delay, missing-consent warning suppression, warning-result short circuiting, legal-hold ordering/result ownership,
   success/failure logging, and exact transaction/event/delivery forwarding.
6. `UserSessionScope` captures one instance each of the existing client, connection, user, logout, one-to-one resolver,
   lazy system-message creator, and legal-hold dependencies before constructing the new handlers. The token-refresh
   adapter receives the same authenticated network container and session manager. No app-owned action is duplicated or
   moved below `:logic`.
7. The 23 pre-existing User receiver behavior tests now live with their six receiving-owned handlers; the logic receiver
   suite is routing-only and additionally covers all three legal-hold routes. The existing token-refresh suite remains in
   logic beside its adapter, and focused repository tests cover user-fetch classification and connection-status mapping.
8. These slices add no NSE runtime composition, shared-storage bootstrap, account lock, retry, queue/outbox, rollout
   switch, async redesign, or legal-hold ownership change. They prepare the router for its final move but do not claim an
   NSE-safe legal-hold implementation.

## Remaining User receiver closure

`UserEventReceiverImpl` is now a thin logic-owned router. Client, user, connection, logout, one-to-one resolution,
system-message insertion, and session refresh cross the focused handlers, ports, callbacks, and adapter documented above.
They no longer form part of the router's concrete compile-time closure.

The remaining direct logic-owned paths are `LegalHoldRequestHandler` and `LegalHoldHandler`, covering LegalHoldRequest,
LegalHoldEnabled, and LegalHoldDisabled. These must receive focused receiving-owned boundaries before the final router can
move without exposing the app graph below `:logic`.

The legal-hold branch also closes over `FetchSelfClientsFromRemoteUseCase`, `FetchUsersClientsFromRemoteUseCase`,
`MembersHavingLegalHoldClientUseCase`, `ObserveLegalHoldStateForUserUseCase`, `ObserveSyncStateUseCase`, `TriggerBuffer`,
`UserConfigRepository`, `ConversationRepository`, and the message-persistence/system-message path shared with Conversation.
Those repositories combine reusable local persistence with network fetches, sync observation, and lifecycle behavior.
They need focused legal-hold ports and explicit NSE side-effect ownership before their concrete handlers can move without
duplication.

The MLS feature-config handlers share parts of this app-owned graph but no longer block User receiver handler ownership:
`UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl`
depends on `UpdateSelfUserSupportedProtocolsUseCase` and `OneOnOneResolver`, while the slow-sync fallback path depends on
`CryptoTransactionProviderImpl` and its client providers. Event processing supplies an existing transaction context, but
the shared handlers keep the slow-sync path unchanged; these concrete dependencies remain separate app-composition and
NSE-runtime concerns rather than being duplicated in the receiving module.

## Completed Conversation and message ownership closure

`MemberJoinEventHandler` and `MemberLeaveEventHandler` are now receiving-owned. They reuse the focused lifecycle,
message, system-message, protocol, and MLS contracts; their remaining app-owned fetch, legal-hold, call-update, and
self-team actions cross captured callbacks. The broad implementations remain main-app composition concerns without
preventing handler ownership below `:logic`. `MemberChangeEventHandler` crosses fetch-if-unknown through its focused
callback, while `MLSResetConversationEventHandler` uses the focused reset repository; both are receiving-owned.

`NewConversationEventHandler` and `DeletedConversationEventHandler` are now receiving-owned. They use the focused shared
user boundary, focused system-message/lookup contracts, and captured persistence, resolver, mapper, and deletion callbacks
described above. The underlying remote/MLS-aware actions, notification execution, hooks, and broad repository graphs remain
main-app composition concerns and still need explicit NSE ownership/adapters or durable follow-up design before runtime
composition. Durable/asynchronous deletion and main-app side-effect execution are future work, not part of this extraction.

`MLSWelcomeEventHandler` is now receiving-owned. It uses the focused `MLSWelcomeEventRepository` plus captured callbacks
for one-to-one resolution, refill, CRL work, join, and fetch while preserving the original repository object and use-case
defaults. Those logic-owned actions still need explicit NSE ownership/adapters or durable follow-up design before runtime
composition, but they no longer prevent handler ownership below `:logic`.

`NewMessageEventHandler` is now receiving-owned. Proteus and MLS unpacking plus application-message routing are direct
receiving dependencies. Legal hold, stale-epoch verification, and MLS reset cross focused callbacks while
`LegalHoldHandler`/`Impl`, `StaleEpochVerifier`/`Impl`, and `ResetMLSConversationUseCase`/`Impl` remain in `:logic`.
Self-deletion and confirmation delivery retain their existing callbacks, and pending-side-effect flushing still delegates
to the same application handler. These app-owned actions do not prevent handler ownership below `:logic`, but they still
need explicit NSE ownership/adapters or a durable action/outbox design before runtime composition is possible.

`ApplicationMessageHandler` and its Availability, ClientAction, InCallEmoji, TextEdited, MultipartEdited, DeleteMessage,
and ClearConversationContent leaves, including their complete direct persistence and named app-owned side-effect
boundaries, are now extracted. The direct filesystem executor, link-preview implementation, and MLS/CoreCrypto deletion
implementation intentionally remain app infrastructure.

`AssetMessageHandler` and `CallingMessageHandler`, with their focused dependency closures and tests, are now extracted to
`:domain:messaging:receiving`. Normal forwarding, client identity, conversation-member observation, mute execution,
moderation state, and the pure remote-mute checker cross focused boundaries owned below `:logic`. `CallManager`,
`MuteCallUseCase`, `CallsScope`, the process-local `CallModerationActionsRepository`, and AVS/calling infrastructure remain
in `:logic` behind the receiving-owned ports.
A standalone `Ignored` move is still not a meaningful milestone, and `History` remains existing unsupported behavior
rather than an extraction target. The extracted in-call reaction stream remains process-local and ephemeral, so any
future NSE cross-process or durability design stays outside this leaf.

`ConversationEventReceiverImpl` and its focused suite are now receiving-owned, while the
`ConversationEventReceiver` contract remains event-processing-owned. This completes the conversation-handler ownership
extraction goal. `MeetingEventReceiverImpl` and its four handlers are also receiving-owned behind the focused
`MeetingEventRepository`; `UserEventReceiverImpl` is now a thin router whose only unextracted routes are legal hold, and
it remains the only concrete receiver still logic-owned. Separately, NSE
runtime composition still lacks explicit ownership/adapters or durability for legal hold, stale-epoch recovery, reset/rejoin,
confirmation delivery, self deletion, and pending side effects. Shared-storage bootstrap, durable subconversation
mapping, pending-proposal ownership/outbox/execution, the Kalium account event lock, validation of the assumed CoreCrypto
process serialization, and rollout work also remain separate work. No separate Kalium CoreCrypto database-lock
implementation is planned.

For module-graph readiness, `:domain:messaging:receiving` now depends directly on `:data:network-model` rather than the
transport implementation. `:data:network` is still present transitively through `:core:common` because `CoreFailure`
contains transport-specific failure representation. The development `:nse` vertical slice may retain that resolved graph,
but the split is required before production Wire iOS integration. It is not part of Milestone 3 shared-storage work.
