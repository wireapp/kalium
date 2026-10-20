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
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallModerationActionsDataSource
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.call.InCallReactionsDataSource
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCaseImpl
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCaseImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.client.LegacyClientBinding
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.getServerConfig
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.StaleEpochVerifierImpl
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolverImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.AppMLSReceiveRecovery
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.PollVoteHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings5 {
    @Provides
    @UserSessionBinding("reactionRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideReactionRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ReactionRepositoryImpl =
        ReactionRepositoryImpl(userId(), userStorage().database.reactionDAO)

    @Provides
    @UserSessionBinding("receiptRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideReceiptRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ReceiptRepositoryImpl =
        ReceiptRepositoryImpl(userStorage().database.receiptDAO)

    @Provides
    @UserSessionBinding("persistReaction")
    fun userSessionProvidePersistReaction(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("reactionRepository")
        reactionRepository: Provider<ReactionRepositoryImpl>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistReactionUseCase =
        PersistReactionUseCaseImpl(
            reactionRepository = reactionRepository(),
            selfUserId = userId(),
            persistenceEventHookNotifier = currentPersistenceEventHookNotifier(),
        )

    @Provides
    @UserSessionBinding("mlsUnpacker")
    fun userSessionProvideMlsUnpacker(
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("pendingProposalScheduler")
        pendingProposalScheduler: Provider<PendingProposalScheduler>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MLSMessageUnpacker =
        MLSMessageUnpackerImpl(
            conversationRepository = conversationRepository(),
            subconversationRepository = subconversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            pendingProposalScheduler = pendingProposalScheduler(),
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("proteusUnpacker")
    fun userSessionProvideProteusUnpacker(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ProteusMessageUnpacker =
        ProteusMessageUnpackerImpl(
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("messageEncoder")
    fun userSessionProvideMessageEncoder(): MessageContentEncoder =
        MessageContentEncoder()

    @Provides
    @UserSessionBinding("systemMessageInserter")
    fun userSessionProvideSystemMessageInserter(
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SystemMessageInserterImpl =
        SystemMessageInserterImpl(userId(), persistMessage())

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
    @UserSessionBinding("isMessageSentInSelfConversation")
    fun userSessionProvideIsMessageSentInSelfConversation(
        @UserSessionBinding("selfConversationIdProvider")
        selfConversationIdProvider: Provider<SelfConversationIdProvider>,
    ): IsMessageSentInSelfConversationUseCase =
        IsMessageSentInSelfConversationUseCaseImpl(selfConversationIdProvider())

    @Provides
    @UserSessionBinding("assetMessageHandler")
    fun userSessionProvideAssetMessageHandler(
        @UserSessionBinding("validateAssetMimeType")
        validateAssetMimeType: Provider<ValidateAssetFileTypeUseCase>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
    ): AssetMessageHandler =
        AssetMessageHandlerImpl(
            messageRepository(),
            persistMessage(),
            userConfigRepository(),
            validateAssetMimeType()
        )

    @Provides
    @UserSessionBinding("buttonActionConfirmationHandler")
    fun userSessionProvideButtonActionConfirmationHandler(
        @UserSessionBinding("compositeMessageRepository")
        compositeMessageRepository: Provider<CompositeMessageRepository>,
        @UserSessionBinding("messageMetadataRepository")
        messageMetadataRepository: Provider<MessageMetadataRepository>,
    ): ButtonActionConfirmationHandler =
        ButtonActionConfirmationHandlerImpl(compositeMessageRepository(), messageMetadataRepository())

    @Provides
    @UserSessionBinding("dataTransferEventHandler")
    fun userSessionProvideDataTransferEventHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DataTransferEventHandler =
        DataTransferEventHandlerImpl(
            userId(),
            userConfigRepository(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("inCallReactionsRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideInCallReactionsRepository(): InCallReactionsRepository =
        InCallReactionsDataSource()

    @Provides
    @UserSessionBinding("callModerationActionsRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideCallModerationActionsRepository(): CallModerationActionsRepository =
        CallModerationActionsDataSource()

    @Provides
    @UserSessionBinding("buttonActionHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideButtonActionHandler(
        @UserSessionBinding("compositeMessageRepository")
        compositeMessageRepository: Provider<CompositeMessageRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ButtonActionHandler =
        ButtonActionHandlerImpl(userId(), compositeMessageRepository(), userScopedLogger())

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
    @UserSessionBinding("linkPreviewsResolver")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLinkPreviewsResolver(
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("assetRepository")
        assetRepository: Provider<AssetRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): LinkPreviewImagesResolver =
        LinkPreviewImagesResolverImpl(
            messageRepository = messageRepository(),
            assetRepository = assetRepository(),
            scope = sessionCoroutineScope(),
            dispatcher = KaliumDispatcherImpl,
            linkPreviewEnabled = kaliumConfigs().linkPreviewEnabled
        )

    @Provides
    @UserSessionBinding("staleEpochVerifier")
    fun userSessionProvideStaleEpochVerifier(
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
    ): StaleEpochVerifier =
        StaleEpochVerifierImpl(
            systemMessageInserter = systemMessageInserter(),
            fetchConversationUseCase = fetchConversationUseCase(),
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            joinExistingMLSConversation = joinExistingMLSConversationUseCase(),
            subconversationRepository = subconversationRepository()
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
    @UserSessionBinding("newGroupConversationSystemMessagesCreator")
    fun userSessionProvideNewGroupConversationSystemMessagesCreator(
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): NewGroupConversationSystemMessagesCreator =
        NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage = persistMessage(),
            selfTeamIdProvider = selfTeamId(),
            qualifiedIdMapper = qualifiedIdMapper(),
            selfUserId = userId()
        )

    @Provides
    @UserSessionBinding("newConversationHandler")
    fun userSessionProvideNewConversationHandler(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("persistConversationUseCase")
        persistConversationUseCase: Provider<PersistConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
    ): NewConversationEventHandler =
        NewConversationEventHandlerImpl(
            conversationRepository(),
            userRepository(),
            selfTeamId(),
            newGroupConversationSystemMessagesCreator(),
            oneOnOneResolver(),
            persistConversationUseCase()
        )

    @Provides
    @UserSessionBinding("deletedConversationHandler")
    fun userSessionProvideDeletedConversationHandler(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("deleteConversationUseCase")
        deleteConversationUseCase: Provider<DeleteConversationUseCase>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeletedConversationEventHandler =
        DeletedConversationEventHandlerImpl(
            userRepository(),
            conversationRepository(),
            meetingRepository(),
            notificationEventsManager(),
            deleteConversationUseCase(),
            currentPersistenceEventHookNotifier(),
            userId(),
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
    @UserSessionBinding("memberChangeHandler")
    fun userSessionProvideMemberChangeHandler(
        @UserSessionBinding("fetchConversationIfUnknownUseCase")
        fetchConversationIfUnknownUseCase: Provider<FetchConversationIfUnknownUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): MemberChangeEventHandler =
        MemberChangeEventHandlerImpl(
            conversationRepository = conversationRepository(),
            fetchConversationIfUnknown = fetchConversationIfUnknownUseCase(),
            persistMessage = persistMessage(),
            selfUserId = userId(),
        )

    @Provides
    @UserSessionBinding("mlsWelcomeHandler")
    fun userSessionProvideMlsWelcomeHandler(
        @LegacyClientBinding("refillKeyPackages")
        clientRefillKeyPackages: Provider<RefillKeyPackagesUseCase>,
        @UserSessionBinding("joinExistingMLSConversationUseCase")
        joinExistingMLSConversationUseCase: Provider<JoinExistingMLSConversationUseCase>,
        @UserSessionBinding("fetchConversationIfUnknownUseCase")
        fetchConversationIfUnknownUseCase: Provider<FetchConversationIfUnknownUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
    ): MLSWelcomeEventHandler =
        MLSWelcomeEventHandlerImpl(
            conversationRepository = conversationRepository(),
            oneOnOneResolver = oneOnOneResolver(),
            refillKeyPackages = clientRefillKeyPackages(),
            joinExistingMLSConversation = joinExistingMLSConversationUseCase(),
            fetchConversationIfUnknown = fetchConversationIfUnknownUseCase()
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

}
