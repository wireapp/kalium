# NSE receiver extraction status

This note records the compile-time closure inspected while implementing the receiver-extraction milestone described by
`nse-safe-multi-process-event-processing.md`. It supplements that plan without changing its design or the NSE runtime scope.

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

Supporting ID, folder, feature-config, self-deletion, and supported-protocol mappers were moved to `:data:data-mappers`.
The outgoing message-entity mapper and the link-preview, mention, attachment, encryption, and conversation-protocol
mappers in its closure were also moved there so the focused message repository does not depend on `:logic`.
The broad repositories in `:logic` delegate the extracted operations to the same lower-level concrete implementations;
there is no NSE-specific or duplicate repository implementation.

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
- application-message routing (`ApplicationMessageHandler`) and its asset, call, reaction, receipt, delete, edit,
  multipart, last-read, and clear-content handlers. Its button and data-transfer leaves are reusable below `:logic`, but
  the facade stays in `:logic` until the remaining branches move;
- MLS recovery/key-package work (`RefillKeyPackagesUseCase`, `PendingProposalScheduler`, `StaleEpochVerifier`,
  `ResetMLSConversationUseCase`, `JoinExistingMLSConversationUseCase`);
- certificate and legal-hold checks (`CertificateRevocationListRepository`, `RevocationListChecker`,
  `LegalHoldHandler`);
- broad conversation, message, asset, call, client, user, and receipt repositories.

Several of those repositories mix DAO operations with authenticated networking, call lifecycle, worker scheduling,
observers, or crypto recovery. The next extraction must split their receiver-required local operations into focused
lower-level implementations first, then move handlers from the leaves toward `NewMessageEventHandler`. Moving only the
receiver class, defining NSE-only adapters, or copying the repositories would leave an incomplete graph and is therefore
intentionally not done here.

The recommended next coherent slice is `ReceiptMessageHandler`: split message-status updates and receipt insertion from
the broad message/receipt repositories into focused DAO-backed implementations, move the receipt mapper/model closure as
needed, and preserve status-update-before-receipt-insert ordering plus the read-receipt hook timing. `DeleteForMeHandler`
is smaller at the mutation point but remains coupled to `IsMessageSentInSelfConversationUseCase`, whose concrete
verification path still closes over client registration and cached MLS/Proteus self-conversation IDs from broad client
and conversation repositories. The lifecycle handlers remain blocked on the remote-fetch, MLS, legal-hold, call,
notification, user, and system-message closures above.
`ConversationEventReceiverImpl` remains blocked until those lifecycle handlers plus `NewMessageEventHandler`, both
unpackers/failure handlers, MLS recovery, certificate/legal-hold checks, and pending-side-effect flushing are all reusable
below `:logic`.
