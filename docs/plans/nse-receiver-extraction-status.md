# NSE receiver extraction status

This note records the compile-time closure inspected while implementing the receiver-extraction milestone described by
`nse-safe-multi-process-event-processing.md`. It supplements that plan without changing its design or the NSE runtime scope.

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
- application-message routing (`ApplicationMessageHandler`) and its asset, call, reaction, delete, edit, multipart,
  last-read, and clear-content handlers. Its button, data-transfer, and receipt leaves are reusable below `:logic`, but
  the facade stays in `:logic` until the remaining branches move;
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

Delete-for-me and its complete local verification/mutation closure are now extracted. Edit/delete handlers beyond
delete-for-me require message lookup and mapping plus notification/asset side effects, while
last-read and clear-content retain self-conversation, notification, pending-flush, asset, and conversation-deletion
dependencies. The lifecycle handlers remain blocked on the remote-fetch, MLS, legal-hold, call, notification, user, and
system-message closures above. The recommended next coherent application-message leaf is `DeleteMessageHandler`, but only
together with focused lower-level message lookup/deletion and the complete asset, notification, and persistence-hook
closure it currently requires.
`ConversationEventReceiverImpl` remains blocked until those lifecycle handlers plus `NewMessageEventHandler`, both
unpackers/failure handlers, MLS recovery, certificate/legal-hold checks, and pending-side-effect flushing are all reusable
below `:logic`.
