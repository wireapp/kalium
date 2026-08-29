# NSE receiver extraction status

This note records the compile-time closure inspected while implementing the receiver-extraction milestone described by
`nse-safe-multi-process-event-processing.md`. It supplements that plan without changing its design or the NSE runtime scope.

## Completed clear-conversation-content application-message slice

Inspection of `ClearConversationContentHandlerImpl`, `ConversationRepository.clearContent`, `ConversationDAO`,
`ClearConversationAssetsLocallyUseCase`, `DeleteConversationUseCase`, persistence hooks, `ApplicationMessageHandler`,
`UserSessionScope`, and their tests produced this completed behavior-preserving extraction:

1. `ClearConversationContentHandler` and its implementation now live in `:domain:messaging:receiving`.
   `ApplicationMessageHandler` and `NewMessageEventHandler` remain in `:logic`, and the existing `MessageContent.Cleared`
   routing expression is unchanged.
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
   ID-based delete-notification, named asset-cleanup, and persistence-hook contracts. `ApplicationMessageHandler` and
   `NewMessageEventHandler` remain in `:logic` and route the same delete branch synchronously.
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

## Current text/multipart-edit application-message slice plan

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
   `ApplicationMessageHandler` and `NewMessageEventHandler` in `:logic` and delegate only their existing two branches.
8. Move and strengthen both handler suites in the owning module; add focused persistence mapping/failure/cancellation
   coverage and exact app-facade routing coverage; validate receiving/logic JVM tests, root detekt, iOS Simulator ARM64
   compilation, and diff hygiene; then stop before quote handling, ignored/no-op extraction, deletion, asset/calling,
   facade/orchestration, NSE runtime, or process locking.

## Current in-call-emoji application-message slice plan

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
4. Delegate only the `InCallEmoji` branch from `ApplicationMessageHandlerImpl`; keep both application-message facades in
   `:logic` and leave every other signaling branch unchanged.
5. Compose one lazy session-scoped data-source instance in `UserSessionScope`, use it for the focused incoming handler,
   and continue passing that same instance to `CallsScope` observers so producer and consumer never split across streams.
6. Keep the flow process-local and ephemeral. Do not add NSE runtime wiring, durability, process locking, retries, rollout
   switches, or error conversion in this slice.
7. Move repository tests to the owning module, add focused forwarding/propagation and logic routing/shared-stream tests,
   validate JVM/iOS compilation and detekt, then stop before `Ignored`, delete/edit/calling/asset/clear-content leaves,
   facade extraction, or broader orchestration.

## Current client-action application-message slice plan

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
   retained by `ApplicationMessageHandlerImpl` for regular messages. Keep both application-message facades in `:logic`.
5. Add focused mapping/result/propagation tests plus exact-envelope routing coverage, validate JVM/iOS compilation and
   detekt, then stop before Ignored, InCallEmoji, delete/edit leaves, facade extraction, or NSE runtime work.

## Current availability application-message slice plan

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

## Current composite-edit application-message slice plan

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
5. Remove only the now-unused composite-update facade from `MessageRepository`; retain `getMessageById` and keep
   `ApplicationMessageHandler`/`NewMessageEventHandler` in `:logic`. Move and expand focused handler/DAO-adapter tests,
   retain routing coverage in `:logic`, validate JVM/iOS compilation and detekt, then stop before other edit/delete leaves,
   notification side effects, or NSE orchestration.

## Current delete-for-me application-message slice plan

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
   `:domain:messaging:shared` to its existing outgoing messaging, analytics, and calling consumers, and keep
   `ApplicationMessageHandler` in `:logic` while rewiring only its delete-for-me leaf. Do not add NSE-specific adapters,
   runtime switches, or process locking.
7. Keep the shared module narrow: do not move `SelfTeamIdProvider`, `CurrentClientIdProvider`, unrelated session state, or
   introduce a public generic cache framework in this milestone. The success-only cache helper remains an implementation
   detail of self-conversation resolution; `:domain:userstorage` continues to own database-instance caching and its
   separate configurable cache-scope policy.
8. Move and expand provider/verifier characterization tests, and add focused tests for storage mapping, `DataNotFound`,
   wrapped exceptions, cancellation, success-only caching, call order, fail-closed verification, ignored delete failures,
   delete/hook order, exact hook payload, and the unverified no-op path. Retain application-message routing coverage in
   `:logic`, validate shared/receiving/logic JVM tests, detekt, and iOS Simulator ARM64 compilation, then stop before
   delete-for-everyone, edit, last-read, clear-content, asset/notification side effects, unpacking, or NSE orchestration.

## Current last-read application-message slice plan

Inspection of `LastReadContentHandlerImpl`, `ConversationRepository.updateReadDatesAndGetHasUnreadEvents`,
`ConversationDAO`, `ApplicationMessageHandler`, `UserSessionScope`, and their tests gives this concrete extraction plan:

1. Add a focused `IncomingLastReadPersistence` contract in `:domain:messaging:receiving`, backed directly by
   `ConversationDAO.updateReadDatesAndGetHasUnreadEvents`. Preserve qualified-ID mapping in both directions,
   `wrapStorageRequest`, the exact `Either<StorageFailure, Map<ConversationId, Boolean>>` result, and cancellation
   propagation.
2. Move `LastReadContentHandler` and `LastReadContentHandlerImpl` to `:domain:messaging:receiving` without changing their
   package, and expose cross-module construction through `@InternalKaliumApi`. Keep `ApplicationMessageHandler` and
   `NewMessageEventHandler` in `:logic`.
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

## Current reaction application-message slice plan

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
   `ApplicationMessageHandler` in `:logic` and rewire it only through existing composition; do not add an NSE adapter or
   runtime switch.
4. Move the use-case characterization tests with the implementation, add focused DAO forwarding/failure tests, and retain
   broad-repository integration coverage in `:logic`. Stop before delete-for-me, edit/delete, last-read, clear-content,
   call, unpacking, or NSE orchestration work.

## Current receipt application-message slice plan

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

## Current data-transfer receiver slice plan

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
   concrete DAO-backed storage and extracted handler from the existing `UserSessionScope`; keep
   `ApplicationMessageHandler`, `NewMessageEventHandler`, and `ConversationEventReceiverImpl` in `:logic`.
4. Preserve the early return for another sender or a null identifier; read-before-compare behavior; no-op for an unchanged
   identifier; previous-before-current write order; continuation after a caught setter failure; and the two existing
   success log points. Move the existing handler characterization tests with the implementation and add focused adapter
   tests for DAO forwarding plus setter-catching/getter-propagation behavior.
5. Stop before delete-for-me, receipts, message unpacking, MLS, legal hold, or pending side effects. Reassess the
   delete-for-me and receipt DAO leaves only after this isolated slice is validated on JVM and iOS Simulator ARM64.

## Extracted coherent slice

The following concrete receivers now live in `:domain:messaging:receiving`, and `:logic` composes those same implementations:

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
  - `UserSessionScope` owns one shared handler instance for application-message handling and pending-side-effect flushes,
    while `ApplicationMessageHandler` and `NewMessageEventHandler` remain in `:logic`.
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
  - `ApplicationMessageHandlerImpl` delegates only its ClientAction branch and remains in `:logic` with
    `NewMessageEventHandler`.
- The in-call-emoji application-message leaf
  - `InCallEmojiMessageHandlerImpl`, retaining signaling-envelope conversation/sender IDs and forwarding the existing
    emoji-key set exactly once, with direct suspension, exception, and cancellation behavior unchanged;
  - `InCallReactionsRepository` and `InCallReactionsDataSource` retain their existing package and exact process-local
    `MutableSharedFlow` replay, extra-buffer, overflow, emission, and conversation-filtering behavior;
  - `UserSessionScope` owns one shared repository instance used by both the incoming handler and `CallsScope` observers;
    the stream intentionally remains ephemeral and is not an NSE cross-process transport or durable persistence layer;
  - `ApplicationMessageHandlerImpl` delegates only its `InCallEmoji` branch and remains in `:logic` with
    `NewMessageEventHandler`.
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
  - `ApplicationMessageHandlerImpl` delegates only its existing `DeleteMessage` branch and remains in `:logic` with
    `NewMessageEventHandler`; no asynchronous execution, outbox, pending action, retry, or NSE-specific adapter was added.
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
    `MLSConversationRepository`, `DeleteConversationUseCase`, `CryptoTransactionContext` wrapping, CoreCrypto wiping,
    proposal timers, and both application-message facades remain in `:logic`.

Supporting ID, folder, feature-config, self-deletion, and supported-protocol mappers were moved to `:data:data-mappers`.
The outgoing message-entity mapper and the link-preview, mention, attachment, encryption, and conversation-protocol
mappers in its closure were also moved there so the focused message repository does not depend on `:logic`.
The broad repositories in `:logic` delegate still-used overlapping operations to the same lower-level concrete
implementations, while internal facades left without production callers are removed; there is no NSE-specific or
duplicate repository implementation.

The extraction-induced Konsist failures are also resolved. App layer rules now inspect only `:logic`, feature-config
receiver dependencies live outside the app `feature` package, and explicitly `@InternalKaliumApi` use-case implementations
may retain public constructors for cross-module composition.

## Remaining User receiver closure

`UserEventReceiverImpl` is not yet safe to move as a coherent concrete graph. Its compile-time closure contains these
unextracted concrete paths:

- client, user, and connection event mutations in `ClientRepository`, `UserRepository`, and `ConnectionRepository`;
- account lifecycle through `LogoutUseCase` and `LogoutReason`;
- one-to-one resolution through `OneOnOneResolver`;
- system-message insertion through `NewGroupConversationSystemMessagesCreator`;
- token/session recovery through `SessionRefreshSuggestedEventHandler`, `AuthenticatedNetworkContainer`, and
  `SessionManager`;
- legal-hold processing through `LegalHoldRequestHandler` and `LegalHoldHandler`.

The legal-hold branch also closes over `FetchSelfClientsFromRemoteUseCase`, `FetchUsersClientsFromRemoteUseCase`,
`MembersHavingLegalHoldClientUseCase`, `ObserveLegalHoldStateForUserUseCase`, `ObserveSyncStateUseCase`, `TriggerBuffer`,
`UserConfigRepository`, `ConversationRepository`, and the message-persistence/system-message path shared with Conversation.
Those repositories currently combine reusable local persistence with network fetches, sync observation, and lifecycle
behavior. They need focused local slices before their concrete handlers can move without duplication.

The MLS feature-config handlers share this remaining closure: `UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl`
depends on `UpdateSelfUserSupportedProtocolsUseCase` and `OneOnOneResolver`, while the slow-sync fallback path depends on
`CryptoTransactionProviderImpl` and its client providers. Event processing supplies an existing transaction context, but
the shared handlers keep the slow-sync path unchanged; these concrete dependencies therefore remain part of the User and
Conversation extraction rather than being duplicated in the receiving module.

## Remaining Conversation and message closure

`ConversationEventReceiverImpl` still composes these concrete event handlers in `:logic`:

- `NewConversationEventHandler`, `DeletedConversationEventHandler`, `MemberJoinEventHandler`,
  `MemberLeaveEventHandler`, and `MemberChangeEventHandler`;
- `MLSWelcomeEventHandler`, `MLSResetConversationEventHandler`, `ProtocolUpdateEventHandler`;
- `NewMessageEventHandler`.

The remaining lifecycle handlers now use focused local persistence, but their complete concrete closures are not yet
reusable. New-conversation still depends on conversation persistence, one-to-one resolution, unknown-user fetching, and
group-system-message creation. Delete still depends on MLS-aware deletion, user observation, notification scheduling, and
the persistence hook. Join still depends on remote conversation/user fetches, legal hold, group-system-message creation,
and conversation-type lookup. Leave still depends on user deletion/fetch, call updates, legal hold, team membership, and
MLS group cleanup. Member-change still depends on fetch-if-unknown before its extracted local member update.

The complete `NewMessageEventHandler` branch additionally closes over:

- Proteus and MLS unpacking/failure handling (`ProteusMessageUnpacker`, `ProteusMessageFailureHandler`,
  `MLSMessageUnpacker`, `MLSMessageFailureHandler`, `MessageUnpackResult`);
- application-message routing (`ApplicationMessageHandler`) and its remaining app-owned asset and call handlers. Its
  button, data-transfer, receipt, reaction, delete-for-me, last-read, composite-edit, availability, client-action,
  in-call-emoji, text-edit, multipart-edit, delete-message, and clear-content leaves are reusable below `:logic`, but the
  facade stays in `:logic` until the remaining branches move;
- MLS recovery/key-package work (`RefillKeyPackagesUseCase`, `PendingProposalScheduler`, `StaleEpochVerifier`,
  `ResetMLSConversationUseCase`, `JoinExistingMLSConversationUseCase`);
- certificate and legal-hold checks (`CertificateRevocationListRepository`, `RevocationListChecker`,
  `LegalHoldHandler`);
- broad conversation, message, asset, call, client, user, and reaction repositories.

Several of those repositories mix DAO operations with authenticated networking, call lifecycle, worker scheduling,
observers, or crypto recovery. The next extraction must split their receiver-required local operations into focused
lower-level implementations first, then move handlers from the leaves toward `NewMessageEventHandler`. Moving only the
receiver class, defining NSE-only adapters, or copying the repositories would leave an incomplete graph and is therefore
intentionally not done here.

Availability, ClientAction, InCallEmoji, TextEdited, MultipartEdited, DeleteMessage, and ClearConversationContent,
including their complete direct persistence and named app-owned side-effect boundaries, are now extracted. The direct
filesystem executor and MLS/CoreCrypto deletion implementation intentionally remain app infrastructure. The lifecycle
handlers remain blocked on the remote-fetch, MLS, legal-hold, call, notification, user, and system-message closures
above.

The next meaningful application-message slice is `AssetMessageHandler`. It is now the remaining regular-message leaf
with a tractable local boundary, but it first needs focused replacements for broad `MessageRepository.getMessageById`
mapping and `UserConfigRepository.isFileSharingEnabled`, while retaining the existing MIME validation and
`PersistMessageUseCase` behavior. `CallingMessageHandler` is a later, deeper slice because its current closure includes
`CallManager`, conversation-member observation, client identity, remote-mute policy, mute execution, and moderation
state. A standalone `Ignored` move is still not a meaningful milestone, and `History` remains existing unsupported
behavior rather than an extraction target. The extracted in-call reaction stream remains process-local and ephemeral,
so any future NSE cross-process or durability design stays outside this leaf.
`ConversationEventReceiverImpl` remains blocked until those lifecycle handlers plus `NewMessageEventHandler`, both
unpackers/failure handlers, MLS recovery, certificate/legal-hold checks, and pending-side-effect flushing are all reusable
below `:logic`.
