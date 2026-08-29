# NSE receiver extraction status

This note records the compile-time closure inspected while implementing the receiver-extraction milestone described by
`nse-safe-multi-process-event-processing.md`. It supplements that plan without changing its design or the NSE runtime scope.

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

Supporting ID, folder, feature-config, self-deletion, and supported-protocol mappers were moved to `:data:data-mappers`.
The outgoing message-entity mapper and the link-preview, mention, attachment, encryption, and conversation-protocol
mappers in its closure were also moved there so the focused message repository does not depend on `:logic`.
The broad repositories in `:logic` delegate the extracted operations to the same lower-level concrete implementations;
there is no NSE-specific or duplicate repository implementation.

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
  `MemberLeaveEventHandler`, `MemberChangeEventHandler`, `RenamedConversationEventHandler`;
- `MLSWelcomeEventHandler`, `MLSResetConversationEventHandler`, `ProtocolUpdateEventHandler`;
- `ReceiptModeUpdateEventHandler`, `ConversationMessageTimerEventHandler`, `AccessUpdateEventHandler`, and
  `ChannelAddPermissionUpdateEventHandler`;
- `CodeUpdatedHandler`, `CodeDeletedHandler`, `TypingIndicatorHandler`, and `NewMessageEventHandler`.

The complete `NewMessageEventHandler` branch additionally closes over:

- Proteus and MLS unpacking/failure handling (`ProteusMessageUnpacker`, `ProteusMessageFailureHandler`,
  `MLSMessageUnpacker`, `MLSMessageFailureHandler`, `MessageUnpackResult`);
- application-message routing (`ApplicationMessageHandler`) and its asset, call, reaction, receipt, delete, edit,
  composite, multipart, last-read, button-action, data-transfer, and clear-content handlers;
- MLS recovery/key-package work (`RefillKeyPackagesUseCase`, `PendingProposalScheduler`, `StaleEpochVerifier`,
  `ResetMLSConversationUseCase`, `JoinExistingMLSConversationUseCase`);
- certificate and legal-hold checks (`CertificateRevocationListRepository`, `RevocationListChecker`,
  `LegalHoldHandler`);
- broad conversation, message, asset, call, client, user, receipt, composite-message, and metadata repositories.

Several of those repositories mix DAO operations with authenticated networking, call lifecycle, worker scheduling,
observers, or crypto recovery. The next extraction must split their receiver-required local operations into focused
lower-level implementations first, then move handlers from the leaves toward `NewMessageEventHandler`. Moving only the
receiver class, defining NSE-only adapters, or copying the repositories would leave an incomplete graph and is therefore
intentionally not done here.
