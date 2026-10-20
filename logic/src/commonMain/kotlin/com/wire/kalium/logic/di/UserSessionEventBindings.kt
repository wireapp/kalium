/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.LegalHoldClientsImpl
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.mls.ConversationProtocolGetterImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandlerImpl
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.ProteusMigrationRecoveryHandlerImpl
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.getServerConfig
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.incremental.EventBatchProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.AppMLSReceiveRecovery
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.PollVoteHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.UserPropertiesEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldSystemMessagesHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldTransitionJournal
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldUserTransitionJournal
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Event construction keeps computed arguments, concrete captures and omitted defaults explicit. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionEventBindings {
    @Provides
    @UserSessionBinding("allowedGlobalOperationsHandler")
    fun userSessionProvideAllowedGlobalOperationsHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AllowedGlobalOperationsHandler =
        AllowedGlobalOperationsHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("appLockConfigHandler")
    fun userSessionProvideAppLockConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AppLockConfigHandler =
        AppLockConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("applicationMessageHandler")
    fun userSessionProvideApplicationMessageHandler(
        @UserSessionBinding("applicationMessageHandlerClearConversationContentHandlerImpl")
        applicationMessageHandlerClearConversationContentHandlerImpl: Provider<ClearConversationContentHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerMessageCompositeEditHandlerImpl")
        applicationMessageHandlerMessageCompositeEditHandlerImpl: Provider<MessageCompositeEditHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerMessageMultipartEditHandlerImpl")
        applicationMessageHandlerMessageMultipartEditHandlerImpl: Provider<MessageMultipartEditHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerLastReadContentHandlerImpl")
        applicationMessageHandlerLastReadContentHandlerImpl: Provider<LastReadContentHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerMessageTextEditHandlerImpl")
        applicationMessageHandlerMessageTextEditHandlerImpl: Provider<MessageTextEditHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerDeleteMessageHandlerImpl")
        applicationMessageHandlerDeleteMessageHandlerImpl: Provider<DeleteMessageHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerDeleteForMeHandlerImpl")
        applicationMessageHandlerDeleteForMeHandlerImpl: Provider<DeleteForMeHandlerImpl>,
        @UserSessionBinding("applicationMessageHandlerPollVoteHandlerImpl")
        applicationMessageHandlerPollVoteHandlerImpl: Provider<PollVoteHandlerImpl>,
        @UserSessionBinding("buttonActionConfirmationHandler")
        buttonActionConfirmationHandler: Provider<ButtonActionConfirmationHandler>,
        @UserSessionBinding("inCallReactionsRepository")
        inCallReactionsRepository: Provider<InCallReactionsRepository>,
        @UserSessionBinding("dataTransferEventHandler")
        dataTransferEventHandler: Provider<DataTransferEventHandler>,
        @UserSessionBinding("callingMessageHandler")
        callingMessageHandler: Provider<CallingMessageHandler>,
        @UserSessionBinding("receiptMessageHandler")
        receiptMessageHandler: Provider<ReceiptMessageHandlerImpl>,
        @UserSessionBinding("linkPreviewsResolver")
        linkPreviewsResolver: Provider<LinkPreviewImagesResolver>,
        @UserSessionBinding("assetMessageHandler")
        assetMessageHandler: Provider<AssetMessageHandler>,
        @UserSessionBinding("buttonActionHandler")
        buttonActionHandler: Provider<ButtonActionHandler>,
        @UserSessionBinding("persistReaction")
        persistReaction: Provider<PersistReactionUseCase>,
        @UserSessionBinding("messageEncoder")
        messageEncoder: Provider<MessageContentEncoder>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ApplicationMessageHandler =
        ApplicationMessageHandlerImpl(
            userRepository(),
            userStorage().database.messageDAO,
            assetMessageHandler(),
            persistMessage(),
            persistReaction(),
            applicationMessageHandlerMessageTextEditHandlerImpl(),
            applicationMessageHandlerMessageMultipartEditHandlerImpl(),
            applicationMessageHandlerLastReadContentHandlerImpl(),
            applicationMessageHandlerClearConversationContentHandlerImpl(),
            applicationMessageHandlerDeleteForMeHandlerImpl(),
            applicationMessageHandlerDeleteMessageHandlerImpl(),
            messageEncoder(),
            receiptMessageHandler(),
            buttonActionConfirmationHandler(),
            dataTransferEventHandler(),
            inCallReactionsRepository(),
            buttonActionHandler(),
            applicationMessageHandlerMessageCompositeEditHandlerImpl(),
            callingMessageHandler(),
            linkPreviewsResolver(),
            applicationMessageHandlerPollVoteHandlerImpl(),
            userId()
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerClearConversationContentHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerClearConversationContentHandlerImpl(
        @LegacyConversationBinding("clearConversationAssetsLocally")
        conversationClearConversationAssetsLocally: Provider<ClearConversationAssetsLocallyUseCase>,
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("isMessageSentInSelfConversation")
        isMessageSentInSelfConversation: Provider<IsMessageSentInSelfConversationUseCase>,
        @UserSessionBinding("pendingConversationCleanup")
        pendingConversationCleanup: Provider<PendingConversationCleanup>,
        @UserSessionBinding("deleteConversationUseCase")
        deleteConversationUseCase: Provider<DeleteConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ClearConversationContentHandlerImpl =
        ClearConversationContentHandlerImpl(
            conversationRepository(),
            userId(),
            isMessageSentInSelfConversation(),
            conversationClearConversationAssetsLocally(),
            deleteConversationUseCase(),
            currentPersistenceEventHookNotifier(),
            recoverNSEClear = { message, content ->
                enableDurableMLSReceiveRecovery() &&
                    pendingConversationCleanup().recover(message.conversationId.toDao(), message.id, content.conversationId.toDao())
            },
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerDeleteForMeHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerDeleteForMeHandlerImpl(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("isMessageSentInSelfConversation")
        isMessageSentInSelfConversation: Provider<IsMessageSentInSelfConversationUseCase>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeleteForMeHandlerImpl =
        DeleteForMeHandlerImpl(
            userStorage().database.messageDAO,
            isMessageSentInSelfConversation(),
            currentPersistenceEventHookNotifier(),
            userId(),
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerDeleteMessageHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerDeleteMessageHandlerImpl(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("assetRepository")
        assetRepository: Provider<AssetRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeleteMessageHandlerImpl =
        DeleteMessageHandlerImpl(
            messageRepository(),
            userStorage().database.messageDAO,
            assetRepository(),
            notificationEventsManager(),
            userId(),
            currentPersistenceEventHookNotifier()
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerMessageTextEditHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerMessageTextEditHandlerImpl(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageTextEditHandlerImpl =
        MessageTextEditHandlerImpl(messageRepository(), userStorage().database.messageDAO, notificationEventsManager())

    @Provides
    @UserSessionBinding("assetAuditLogConfigHandler")
    fun userSessionProvideAssetAuditLogConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AssetAuditLogConfigHandler =
        AssetAuditLogConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("callingMessageHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideCallingMessageHandler(
        @UserSessionBinding("freshMuteCall")
        freshMuteCall: Provider<MuteCallUseCase>,
        @UserSessionBinding("callModerationActionsRepository")
        callModerationActionsRepository: Provider<CallModerationActionsRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): CallingMessageHandler =
        CallingMessageHandlerImpl(
            selfUserId = userId(),
            currentClientIdProvider = clientIdProvider(),
            callManager = callManager().value,
            conversationRepository = conversationRepository(),
            callModerationActionsRepository = callModerationActionsRepository(),
            muteCall = freshMuteCall(),
        )

    @Provides
    @UserSessionBinding("cellsConfigHandler")
    fun userSessionProvideCellsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): CellsConfigHandler =
        CellsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("classifiedDomainsConfigHandler")
    fun userSessionProvideClassifiedDomainsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ClassifiedDomainsConfigHandler =
        ClassifiedDomainsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("conferenceCallingConfigHandler")
    fun userSessionProvideConferenceCallingConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ConferenceCallingConfigHandler =
        ConferenceCallingConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("conversationAccessUpdateEventHandler")
    fun userSessionProvideConversationAccessUpdateEventHandler(
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccessUpdateEventHandler =
        AccessUpdateEventHandler(
            conversationDAO = userStorage().database.conversationDAO,
            selfUserId = userId(),
            systemMessageInserter = systemMessageInserter()
        )

    @Provides
    @UserSessionBinding("conversationCodeDeletedHandler")
    fun userSessionProvideConversationCodeDeletedHandler(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CodeDeletedHandler =
        CodeDeletedHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO
        )

    @Provides
    @UserSessionBinding("conversationCodeUpdateHandler")
    fun userSessionProvideConversationCodeUpdateHandler(
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CodeUpdatedHandler =
        CodeUpdateHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO,
            sessionManager().getServerConfig().links
        )

    @Provides
    @UserSessionBinding("conversationMessageTimerEventHandler")
    fun userSessionProvideConversationMessageTimerEventHandler(
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConversationMessageTimerEventHandler =
        ConversationMessageTimerEventHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO,
            messageDAO = userStorage().database.messageDAO,
            persistMessage = persistMessage()
        )

    @Provides
    @UserSessionBinding("deleteConversationReminderEventHandler")
    fun userSessionProvideDeleteConversationReminderEventHandler(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): DeleteConversationReminderEventHandler =
        DeleteConversationReminderEventHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO,
        )

    @Provides
    @UserSessionBinding("e2eiConfigHandler")
    fun userSessionProvideE2eiConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): E2EIConfigHandler =
        E2EIConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("enableUserProfileQRCodeConfigHandler")
    fun userSessionProvideEnableUserProfileQRCodeConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): EnableUserProfileQRCodeConfigHandler =
        EnableUserProfileQRCodeConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("eventBatchProcessor")
    fun userSessionProvideEventBatchProcessor(
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("eventRepository")
        eventRepository: Provider<EventRepository>,
        @UserSessionBinding("receiveRecovery")
        receiveRecovery: Provider<UserSessionValue<suspend () -> AppMLSReceiveRecovery?>>,
        @UserSessionBinding("eventProcessor")
        eventProcessor: Provider<EventProcessor>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): EventBatchProcessor =
        if (enableDurableMLSReceiveRecovery()) {
            EventBatchProcessor.durable(
                eventProcessor = eventProcessor(),
                transactionProvider = cryptoTransactionProvider(),
                databaseBuilder = userStorage().database,
                recovery = { checkNotNull(receiveRecovery().value()) },
                logger = userScopedLogger(),
            )
        } else {
            EventBatchProcessor.standard(
                eventProcessor = eventProcessor(),
                transactionProvider = cryptoTransactionProvider(),
                databaseBuilder = userStorage().database,
                eventRepository = eventRepository(),
                logger = userScopedLogger(),
            )
        }

    @Provides
    @UserSessionBinding("federationEventReceiver")
    fun userSessionProvideFederationEventReceiver(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("connectionRepository")
        connectionRepository: Provider<ConnectionRepository>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FederationEventReceiver =
        FederationEventReceiverImpl(
            conversationRepository(),
            connectionRepository(),
            userRepository(),
            userStorage().database.memberDAO,
            persistMessage(),
            userId()
        )

    @Provides
    @UserSessionBinding("fileSharingConfigHandler")
    fun userSessionProvideFileSharingConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): FileSharingConfigHandler =
        FileSharingConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("guestRoomConfigHandler")
    fun userSessionProvideGuestRoomConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): GuestRoomConfigHandler =
        GuestRoomConfigHandler(userConfigRepository(), kaliumConfigs().guestRoomLink)

    @Provides
    @UserSessionBinding("legalHoldHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLegalHoldHandler(
        @UserSessionBinding("legalHoldHandlerLegalHoldUserTransitionJournal")
        legalHoldHandlerLegalHoldUserTransitionJournal: Provider<LegalHoldUserTransitionJournal>,
        @UserSessionBinding("legalHoldHandlerLegalHoldTransitionJournal")
        legalHoldHandlerLegalHoldTransitionJournal: Provider<LegalHoldTransitionJournal>,
        @UserSessionBinding("legalHoldHandlerLegalHoldClientsImpl")
        legalHoldHandlerLegalHoldClientsImpl: Provider<LegalHoldClientsImpl>,
        @UserSessionBinding("legalHoldSystemMessagesHandler")
        legalHoldSystemMessagesHandler: Provider<LegalHoldSystemMessagesHandlerImpl>,
        @UserSessionBinding("membersHavingLegalHoldClient")
        membersHavingLegalHoldClient: Provider<MembersHavingLegalHoldClientUseCase>,
        @UserSessionBinding("observeLegalHoldStateForUser")
        observeLegalHoldStateForUser: Provider<ObserveLegalHoldStateForUserUseCase>,
        @UserSessionBinding("fetchUsersClientsFromRemote")
        fetchUsersClientsFromRemote: Provider<FetchUsersClientsFromRemoteUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("observeSyncState")
        observeSyncState: Provider<ObserveSyncStateUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LegalHoldHandlerImpl =
        LegalHoldHandlerImpl(
            selfUserId = userId(),
            fetchUsersClientsFromRemote = fetchUsersClientsFromRemote(),
            observeLegalHoldStateForUser = observeLegalHoldStateForUser(),
            membersHavingLegalHoldClient = membersHavingLegalHoldClient(),
            userConfigRepository = userConfigRepository(),
            conversationRepository = conversationRepository(),
            transitions = legalHoldHandlerLegalHoldTransitionJournal(),
            userTransitions = legalHoldHandlerLegalHoldUserTransitionJournal(),
            legalHoldClients = legalHoldHandlerLegalHoldClientsImpl(),
            observeSyncState = observeSyncState(),
            legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler(),
        )

    @Provides
    @UserSessionBinding("meetingsConfigHandler")
    fun userSessionProvideMeetingsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
    ): MeetingsConfigHandler =
        MeetingsConfigHandler(userConfigRepository(), slowSyncRepository())

    @Provides
    @UserSessionBinding("memberJoinHandler")
    fun userSessionProvideMemberJoinHandler(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MemberJoinEventHandler =
        MemberJoinEventHandlerImpl(
            conversationRepository = conversationRepository(),
            userRepository = userRepository(),
            persistMessage = persistMessage(),
            legalHoldHandler = legalHoldHandler(),
            newGroupConversationSystemMessagesCreator = newGroupConversationSystemMessagesCreator(),
            selfUserId = userId(),
            fetchConversation = fetchConversationUseCase(),
            kaliumConfigs = kaliumConfigs()
        )

    @Provides
    @UserSessionBinding("memberLeaveHandler")
    fun userSessionProvideMemberLeaveHandler(
        @UserSessionBinding("updateConversationClientsForCurrentCall")
        updateConversationClientsForCurrentCall: Provider<UserSessionValue<Lazy<UpdateConversationClientsForCurrentCallUseCase>>>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MemberLeaveEventHandler =
        MemberLeaveEventHandlerImpl(
            memberDAO = userStorage().database.memberDAO,
            userRepository = userRepository(),
            conversationRepository = conversationRepository(),
            persistMessage = persistMessage(),
            updateConversationClientsForCurrentCall = updateConversationClientsForCurrentCall().value,
            legalHoldHandler = legalHoldHandler(),
            selfTeamIdProvider = selfTeamId(),
            mlsConversationRepository = mlsConversationRepository(),
            meetingRepository = meetingRepository(),
            notificationEventsManager = notificationEventsManager(),
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("newMessageHandler")
    fun userSessionProvideNewMessageHandler(
        @LegacyMessageBinding("ephemeralMessageDeletionHandler")
        messageEphemeralMessageDeletionHandler: Provider<EphemeralMessageDeletionHandler>,
        @LegacyMessageBinding("confirmationDeliveryHandler")
        messageConfirmationDeliveryHandler: Provider<ConfirmationDeliveryHandler>,
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("applicationMessageHandler")
        applicationMessageHandler: Provider<ApplicationMessageHandler>,
        @UserSessionBinding("resetMlsConversation")
        resetMlsConversation: Provider<ResetMLSConversationUseCase>,
        @UserSessionBinding("staleEpochVerifier")
        staleEpochVerifier: Provider<StaleEpochVerifier>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("proteusUnpacker")
        proteusUnpacker: Provider<ProteusMessageUnpacker>,
        @UserSessionBinding("receiveRecovery")
        receiveRecovery: Provider<UserSessionValue<suspend () -> AppMLSReceiveRecovery?>>,
        @UserSessionBinding("mlsUnpacker")
        mlsUnpacker: Provider<MLSMessageUnpacker>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): NewMessageEventHandler =
        NewMessageEventHandlerImpl(
            proteusUnpacker(),
            mlsUnpacker(),
            applicationMessageHandler(),
            legalHoldHandler(),
            { conversationId, messageId ->
                messageEphemeralMessageDeletionHandler().startSelfDeletion(conversationId, messageId)
            },
            { conversationId, messageId ->
                messageConfirmationDeliveryHandler().enqueueConfirmationDelivery(conversationId, messageId)
            },
            userId(),
            staleEpochVerifier(),
            resetMlsConversation(),
            receiveRecovery = if (enableDurableMLSReceiveRecovery()) {
                { context, event, wrongEpoch -> receiveRecovery().value()?.handle(context, event, wrongEpoch) == true }
            } else null,
        )

    @Provides
    @UserSessionBinding("newMlsMissingUsersRejectionHandler")
    fun userSessionProvideNewMlsMissingUsersRejectionHandler(
        @UserSessionBinding("mlsRejectionConversationProtocolGetter")
        mlsRejectionConversationProtocolGetter: Provider<ConversationProtocolGetterImpl>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): MLSMissingUsersMessageRejectionHandlerImpl =
        MLSMissingUsersMessageRejectionHandlerImpl(
            mlsConversationRepository(),
            mlsRejectionConversationProtocolGetter(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("preventAdminlessGroupsConfigHandler")
    fun userSessionProvidePreventAdminlessGroupsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): PreventAdminlessGroupsConfigHandler =
        PreventAdminlessGroupsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("proteusMigrationRecoveryHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideProteusMigrationRecoveryHandler(
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
    ): ProteusMigrationRecoveryHandler =
        ProteusMigrationRecoveryHandlerImpl(lazy { logout() })

    @Provides
    @UserSessionBinding("protocolUpdateEventHandler")
    fun userSessionProvideProtocolUpdateEventHandler(
        @UserSessionBinding("updateConversationProtocolUseCase")
        updateConversationProtocolUseCase: Provider<UpdateConversationProtocolUseCase>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
    ): ProtocolUpdateEventHandler =
        ProtocolUpdateEventHandlerImpl(
            systemMessageInserter = systemMessageInserter(),
            callRepository = callRepository(),
            updateConversationProtocolUseCase()
        )

    @Provides
    @UserSessionBinding("receiptMessageHandler")
    fun userSessionProvideReceiptMessageHandler(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ReceiptMessageHandlerImpl =
        ReceiptMessageHandlerImpl(
            selfUserId = userId(),
            database = userStorage().database,
            persistenceEventHookNotifier = currentPersistenceEventHookNotifier(),
        )

    @Provides
    @UserSessionBinding("receiptModeUpdateEventHandler")
    fun userSessionProvideReceiptModeUpdateEventHandler(
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ReceiptModeUpdateEventHandler =
        ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO,
            messageDAO = userStorage().database.messageDAO,
            persistMessage = persistMessage()
        )

    @Provides
    @UserSessionBinding("renamedConversationHandler")
    fun userSessionProvideRenamedConversationHandler(
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): RenamedConversationEventHandler =
        RenamedConversationEventHandlerImpl(
            userStorage().database.conversationDAO,
            userStorage().database.messageDAO,
            persistMessage()
        )

    @Provides
    @UserSessionBinding("selfDeletingMessagesConfigHandler")
    fun userSessionProvideSelfDeletingMessagesConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): SelfDeletingMessagesConfigHandler =
        SelfDeletingMessagesConfigHandler(userConfigRepository(), kaliumConfigs().selfDeletingMessages)

    @Provides
    @UserSessionBinding("sessionRefreshSuggestedEventHandler")
    fun userSessionProvideSessionRefreshSuggestedEventHandler(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("sessionManager")
        sessionManager: Provider<SessionManager>,
    ): SessionRefreshSuggestedEventHandler =
        SessionRefreshSuggestedEventHandlerImpl(
            authenticatedNetworkContainer(),
            sessionManager()
        )

    @Provides
    @UserSessionBinding("typingIndicatorHandler")
    fun userSessionProvideTypingIndicatorHandler(
        @LegacyConversationBinding("typingIndicatorIncomingRepository")
        conversationTypingIndicatorIncomingRepository: Provider<TypingIndicatorIncomingRepositoryImpl>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): TypingIndicatorHandler =
        TypingIndicatorHandlerImpl(userId(), conversationTypingIndicatorIncomingRepository())

    @Provides
    @UserSessionBinding("userEventReceiver")
    fun userSessionProvideUserEventReceiver(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("sessionRefreshSuggestedEventHandler")
        sessionRefreshSuggestedEventHandler: Provider<SessionRefreshSuggestedEventHandler>,
        @UserSessionBinding("legalHoldRequestHandler")
        legalHoldRequestHandler: Provider<LegalHoldRequestHandlerImpl>,
        @UserSessionBinding("connectionRepository")
        connectionRepository: Provider<ConnectionRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserEventReceiver =
        UserEventReceiverImpl(
            clientRepository(),
            connectionRepository(),
            userRepository(),
            logout(),
            oneOnOneResolver(),
            userId(),
            clientIdProvider(),
            lazy { newGroupConversationSystemMessagesCreator() },
            legalHoldRequestHandler(),
            legalHoldHandler(),
            sessionRefreshSuggestedEventHandler()
        )

    @Provides
    @UserSessionBinding("userPropertiesEventReceiverUserPropertiesEventHandler")
    fun userSessionProvideUserPropertiesEventReceiverUserPropertiesEventHandler(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): UserPropertiesEventHandler =
        UserPropertiesEventHandler(
            userStorage().database.userPrefsDAO,
            userStorage().database.conversationFolderDAO
        )
}
