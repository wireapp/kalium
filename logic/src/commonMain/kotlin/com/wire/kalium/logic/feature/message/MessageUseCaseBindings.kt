/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:Suppress("LargeClass", "LongParameterList", "TooManyFunctions")

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.notification.NotificationEventsManagerImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserSessionLifetime
import com.wire.kalium.logic.di.UserSessionScopedFactory
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetImageAssetMessagesForConversationUseCase
import com.wire.kalium.logic.feature.asset.GetImageAssetMessagesForConversationUseCaseImpl
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.ObserveAssetStatusesUseCase
import com.wire.kalium.logic.feature.asset.ObserveAssetStatusesUseCaseImpl
import com.wire.kalium.logic.feature.asset.ObserveAssetUploadStateUseCase
import com.wire.kalium.logic.feature.asset.ObserveAssetUploadStateUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCaseImpl
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.feature.asset.upload.PersistNewAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.upload.PersistNewAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.upload.UploadAssetUseCase
import com.wire.kalium.logic.feature.asset.upload.UploadAssetUseCaseImpl
import com.wire.kalium.logic.feature.incallreaction.SendInCallReactionUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionConfirmationMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonMessageUseCase
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandlerImpl
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCase
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCaseImpl
import com.wire.kalium.logic.feature.message.draft.GetMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.GetMessageDraftUseCaseImpl
import com.wire.kalium.logic.feature.message.draft.RemoveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.RemoveMessageDraftUseCaseImpl
import com.wire.kalium.logic.feature.message.draft.SaveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.SaveMessageDraftUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandlerImpl
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCase
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@BindingContainer
internal object MessageUseCaseBindings {

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMessageDraftRepository(
        factory: UserSessionScopedFactory<MessageDraftRepository>,
    ): MessageDraftRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideClientRepository(factory: UserSessionScopedFactory<ClientRepository>): ClientRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideClientRemoteRepository(
        factory: UserSessionScopedFactory<ClientRemoteRepository>,
    ): ClientRemoteRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun providePreKeyRepository(factory: UserSessionScopedFactory<PreKeyRepository>): PreKeyRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideReactionRepository(factory: UserSessionScopedFactory<ReactionRepository>): ReactionRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideReceiptRepository(factory: UserSessionScopedFactory<ReceiptRepository>): ReceiptRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideIncrementalSyncRepository(
        factory: UserSessionScopedFactory<IncrementalSyncRepository>,
    ): IncrementalSyncRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMessageMetadataRepository(
        factory: UserSessionScopedFactory<MessageMetadataRepository>,
    ): MessageMetadataRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideCompositeMessageRepository(
        factory: UserSessionScopedFactory<CompositeMessageRepository>,
    ): CompositeMessageRepository = factory()

    @Provides
    fun provideMessageSendFailureHandler(
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        clientRemoteRepository: ClientRemoteRepository,
        messageRepository: MessageRepository,
        messageSendingScheduler: MessageSendingScheduler,
        fetchConversationUseCase: FetchConversationUseCase,
    ): MessageSendFailureHandler = MessageSendFailureHandlerImpl(
        userRepository,
        clientRepository,
        clientRemoteRepository,
        messageRepository,
        messageSendingScheduler,
        fetchConversationUseCase,
    )

    @Provides
    fun provideSessionEstablisher(preKeyRepository: PreKeyRepository): SessionEstablisher =
        SessionEstablisherImpl(preKeyRepository)

    @Provides
    fun provideMessageEnvelopeCreator(
        conversationRepository: ConversationRepository,
        legalHoldStatusMapper: LegalHoldStatusMapper,
        selfUserId: QualifiedID,
        protoContentMapper: ProtoContentMapper,
    ): MessageEnvelopeCreator = MessageEnvelopeCreatorImpl(
        conversationRepository = conversationRepository,
        legalHoldStatusMapper = legalHoldStatusMapper,
        selfUserId = selfUserId,
        protoContentMapper = protoContentMapper,
    )

    @Provides
    fun provideMLSMessageCreator(
        conversationRepository: ConversationRepository,
        legalHoldStatusMapper: LegalHoldStatusMapper,
        mlsConversationRepository: MLSConversationRepository,
        joinExistingConversationUseCase: JoinExistingMLSConversationUseCase,
        selfUserId: QualifiedID,
        protoContentMapper: ProtoContentMapper,
    ): MLSMessageCreator = MLSMessageCreatorImpl(
        conversationRepository = conversationRepository,
        legalHoldStatusMapper = legalHoldStatusMapper,
        mlsConversationRepository = mlsConversationRepository,
        joinExistingConversationUseCase = joinExistingConversationUseCase,
        selfUserId = selfUserId,
        protoContentMapper = protoContentMapper,
    )

    @Provides
    fun provideMessageSendingInterceptor(messageRepository: MessageRepository): MessageSendingInterceptor =
        MessageSendingInterceptorImpl(MessageContentEncoder(), messageRepository)

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMessageSender(
        messageRepository: MessageRepository,
        conversationRepository: ConversationRepository,
        syncManager: SyncManager,
        messageSendFailureHandler: MessageSendFailureHandler,
        legalHoldHandler: LegalHoldHandler,
        sessionEstablisher: SessionEstablisher,
        messageEnvelopeCreator: MessageEnvelopeCreator,
        mlsMessageCreator: MLSMessageCreator,
        messageSendingInterceptor: MessageSendingInterceptor,
        userRepository: UserRepository,
        staleEpochVerifier: StaleEpochVerifier,
        transactionProvider: CryptoTransactionProvider,
        mlsMissingUsersMessageRejectionHandler: MLSMissingUsersMessageRejectionHandler,
        ephemeralMessageDeletionHandler: Lazy<EphemeralMessageDeletionHandler>,
        scope: CoroutineScope,
    ): MessageSender = MessageSenderImpl(
        messageRepository,
        conversationRepository,
        syncManager,
        messageSendFailureHandler,
        legalHoldHandler,
        sessionEstablisher,
        messageEnvelopeCreator,
        mlsMessageCreator,
        messageSendingInterceptor,
        userRepository,
        staleEpochVerifier,
        transactionProvider,
        mlsMissingUsersMessageRejectionHandler,
        { message, expirationData -> ephemeralMessageDeletionHandler.value.enqueueSelfDeletion(message, expirationData) },
        scope,
    )

    @Provides
    fun providePersistMessage(
        messageRepository: MessageRepository,
        selfUserId: QualifiedID,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): PersistMessageUseCase = PersistMessageUseCaseImpl(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        notificationEventsManager = NotificationEventsManagerImpl,
        persistMessageHookNotifier = persistenceEventHookNotifier,
    )

    @Provides
    fun provideDeleteEphemeralMessageForSelfUserAsSender(
        messageRepository: MessageRepository,
        assetRepository: AssetRepository,
    ): DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl = DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(
        messageRepository = messageRepository,
        assetRepository = assetRepository,
    )

    @Provides
    fun provideDeleteEphemeralMessageForSelfUserAsReceiver(
        messageRepository: MessageRepository,
        assetRepository: AssetRepository,
        currentClientIdProvider: CurrentClientIdProvider,
        messageSender: MessageSender,
        selfUserId: QualifiedID,
        selfConversationIdProvider: SelfConversationIdProvider,
        syncManager: SyncManager,
    ): DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl = DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
        messageRepository = messageRepository,
        assetRepository = assetRepository,
        currentClientIdProvider = currentClientIdProvider,
        messageSender = messageSender,
        selfUserId = selfUserId,
        selfConversationIdProvider = selfConversationIdProvider,
        syncManager = syncManager,
    )

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideEphemeralMessageDeletionHandler(
        scope: CoroutineScope,
        messageRepository: MessageRepository,
        deleteForReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl,
        deleteForSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl,
        selfUserId: QualifiedID,
        kaliumLogger: KaliumLogger,
    ): EphemeralMessageDeletionHandler = EphemeralMessageDeletionHandlerImpl(
        userSessionCoroutineScope = scope,
        messageRepository = messageRepository,
        deleteEphemeralMessageForSelfUserAsReceiver = deleteForReceiver,
        deleteEphemeralMessageForSelfUserAsSender = deleteForSender,
        selfUserId = selfUserId,
        kaliumLogger = kaliumLogger,
    )

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideEnqueueMessageSelfDeletion(
        handler: EphemeralMessageDeletionHandler,
    ): EnqueueMessageSelfDeletionUseCase = EnqueueMessageSelfDeletionUseCaseImpl(handler)

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideDeleteEphemeralMessageEndDate(
        handler: EphemeralMessageDeletionHandler,
    ): DeleteEphemeralMessagesAfterEndDateUseCase = DeleteEphemeralMessagesAfterEndDateUseCaseImpl(handler)

    @Provides
    fun provideSendDeliverSignal(
        selfUserId: QualifiedID,
        messageSender: MessageSender,
        currentClientIdProvider: CurrentClientIdProvider,
        kaliumLogger: KaliumLogger,
    ): SendDeliverSignalUseCase = SendDeliverSignalUseCaseImpl(
        selfUserId = selfUserId,
        messageSender = messageSender,
        currentClientIdProvider = currentClientIdProvider,
        kaliumLogger = kaliumLogger,
    )

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConfirmationDeliveryHandler(
        syncManager: SyncManager,
        conversationRepository: ConversationRepository,
        sendDeliverSignalUseCase: SendDeliverSignalUseCase,
        kaliumLogger: KaliumLogger,
    ): ConfirmationDeliveryHandler = ConfirmationDeliveryHandlerImpl(
        syncManager = syncManager,
        conversationRepository = conversationRepository,
        sendDeliverSignalUseCase = sendDeliverSignalUseCase,
        kaliumLogger = kaliumLogger,
    )

    @Provides
    fun provideSendTextMessage(
        persistMessage: PersistMessageUseCase,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        assetRepository: AssetRepository,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        userPropertyRepository: UserPropertyRepository,
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        scope: CoroutineScope,
        kaliumConfigs: KaliumConfigs,
    ): SendTextMessageUseCase = SendTextMessageUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        assetDataSource = assetRepository,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        selfDeleteTimer = observeSelfDeletingMessages,
        scope = scope,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideSendMultipartMessage(
        persistMessage: PersistMessageUseCase,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        assetRepository: AssetRepository,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        userPropertyRepository: UserPropertyRepository,
        conversationRepository: ConversationRepository,
        attachmentsRepositoryFactory: MessageDependencyFactory<MessageAttachmentDraftRepository>,
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        publishAttachmentsFactory: MessageDependencyFactory<PublishAttachmentsUseCase>,
        removeAttachmentDraftsFactory: MessageDependencyFactory<RemoveAttachmentDraftsUseCase>,
        sendAssetMessage: ScheduleNewAssetMessageUseCase,
        scope: CoroutineScope,
    ): SendMultipartMessageUseCase = SendMultipartMessageUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        assetDataSource = assetRepository,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        conversationRepository = conversationRepository,
        attachmentsRepository = attachmentsRepositoryFactory(),
        selfDeleteTimer = observeSelfDeletingMessages,
        publishAttachments = publishAttachmentsFactory(),
        removeAttachmentDrafts = removeAttachmentDraftsFactory(),
        sendAssetMessage = sendAssetMessage,
        scope = scope,
    )

    @Provides
    fun provideSendEditTextMessage(
        messageRepository: MessageRepository,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        kaliumConfigs: KaliumConfigs,
    ): SendEditTextMessageUseCase = SendEditTextMessageUseCase(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideSendEditMultipartMessage(
        messageRepository: MessageRepository,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        getMessageAttachmentsFactory: MessageDependencyFactory<GetMessageAttachmentsUseCase>,
    ): SendEditMultipartMessageUseCase = SendEditMultipartMessageUseCase(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        getMessageAttachments = getMessageAttachmentsFactory(),
    )

    @Provides
    fun provideGetAssetMessageTransferStatus(
        messageRepository: MessageRepository,
        dispatcher: KaliumDispatcher,
    ): GetAssetMessageTransferStatusUseCase = GetAssetMessageTransferStatusUseCaseImpl(messageRepository, dispatcher)

    @Provides
    fun provideUpdateAssetMessageTransferStatus(
        messageRepository: MessageRepository,
    ): UpdateAssetMessageTransferStatusUseCase = UpdateAssetMessageTransferStatusUseCaseImpl(messageRepository)

    @Provides
    fun providePersistNewAssetMessage(
        persistMessage: PersistMessageUseCase,
        currentClientIdProvider: CurrentClientIdProvider,
        userPropertyRepository: UserPropertyRepository,
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        assetRepository: AssetRepository,
        dispatcher: KaliumDispatcher,
    ): PersistNewAssetMessageUseCase = PersistNewAssetMessageUseCaseImpl(
        persistMessage,
        currentClientIdProvider,
        userPropertyRepository,
        observeSelfDeletingMessages,
        assetRepository,
        dispatcher,
    )

    @Provides
    fun provideUpdateAudioMessageNormalizedLoudness(
        messageRepository: MessageRepository,
    ): UpdateAudioMessageNormalizedLoudnessUseCase =
        UpdateAudioMessageNormalizedLoudnessUseCaseImpl(messageRepository = messageRepository)

    @Provides
    fun provideUploadAsset(
        assetRepository: AssetRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        updateAudioNormalizedLoudness: UpdateAudioMessageNormalizedLoudnessUseCase,
        persistMessage: PersistMessageUseCase,
        audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
        dispatcher: KaliumDispatcher,
        kaliumConfigs: KaliumConfigs,
    ): UploadAssetUseCase = UploadAssetUseCaseImpl(
        assetDataSource = assetRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
        updateAudioNormalizedLoudness = updateAudioNormalizedLoudness,
        persistMessage = persistMessage,
        audioNormalizedLoudnessBuilder = audioNormalizedLoudnessBuilder,
        dispatcher = dispatcher,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideSendAssetMessage(
        persistNewAssetMessage: PersistNewAssetMessageUseCase,
        uploadAsset: UploadAssetUseCase,
        updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        selfUserId: QualifiedID,
        slowSyncRepository: SlowSyncRepository,
        messageRepository: MessageRepository,
        observeFileSharingStatusUseCase: ObserveFileSharingStatusUseCase,
        messageSendFailureHandler: MessageSendFailureHandler,
        scope: CoroutineScope,
        dispatcher: KaliumDispatcher,
        kaliumConfigs: KaliumConfigs,
    ): ScheduleNewAssetMessageUseCase = ScheduleNewAssetMessageUseCaseImpl(
        persistNewAssetMessage = persistNewAssetMessage,
        uploadAsset = uploadAsset,
        updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
        userId = selfUserId,
        slowSyncRepository = slowSyncRepository,
        messageRepository = messageRepository,
        observeFileSharingStatus = observeFileSharingStatusUseCase,
        validateAssetFileUseCase = ValidateAssetFileTypeUseCaseImpl(),
        messageSendFailureHandler = messageSendFailureHandler,
        scope = scope,
        dispatcher = dispatcher,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideSendPendingAssetMessage(
        assetRepository: AssetRepository,
        persistMessage: PersistMessageUseCase,
        updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
        kaliumConfigs: KaliumConfigs,
    ): SendPendingAssetMessageUseCase = SendPendingAssetMessageUseCaseImpl(
        assetRepository = assetRepository,
        persistMessage = persistMessage,
        updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
        getAssetMessageTransferStatus = getAssetMessageTransferStatus,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        audioNormalizedLoudnessBuilder = audioNormalizedLoudnessBuilder,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideRetryFailedMessage(
        messageRepository: MessageRepository,
        assetRepository: AssetRepository,
        conversationRepository: ConversationRepository,
        attachmentsRepositoryFactory: MessageDependencyFactory<MessageAttachmentDraftRepository>,
        persistMessage: PersistMessageUseCase,
        publishAttachmentsFactory: MessageDependencyFactory<PublishAttachmentsUseCase>,
        scope: CoroutineScope,
        dispatcher: KaliumDispatcher,
        messageSender: MessageSender,
        updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase,
        messageSendFailureHandler: MessageSendFailureHandler,
    ): RetryFailedMessageUseCase = RetryFailedMessageUseCase(
        messageRepository,
        assetRepository,
        conversationRepository,
        attachmentsRepositoryFactory(),
        persistMessage,
        publishAttachmentsFactory(),
        scope,
        dispatcher,
        messageSender,
        updateAssetMessageTransferStatus,
        getAssetMessageTransferStatus,
        messageSendFailureHandler,
    )

    @Provides
    fun provideGetMessageById(messageRepository: MessageRepository): GetMessageByIdUseCase =
        GetMessageByIdUseCase(messageRepository)

    @Provides
    fun provideObserveMessageById(messageRepository: MessageRepository): ObserveMessageByIdUseCase =
        ObserveMessageByIdUseCase(messageRepository)

    @Provides
    fun provideGetAssetMessage(
        assetRepository: AssetRepository,
        messageRepository: MessageRepository,
        userRepository: UserRepository,
        updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler,
        scope: CoroutineScope,
        dispatcher: KaliumDispatcher,
    ): GetMessageAssetUseCase = GetMessageAssetUseCaseImpl(
        assetRepository,
        messageRepository,
        userRepository,
        updateAssetMessageTransferStatus,
        audioNormalizedLoudnessScheduler,
        scope,
        dispatcher,
    )

    @Provides
    fun provideGetImageAssetMessagesByConversation(
        dispatcher: KaliumDispatcher,
        messageRepository: MessageRepository,
    ): GetImageAssetMessagesForConversationUseCase =
        GetImageAssetMessagesForConversationUseCaseImpl(dispatcher, messageRepository)

    @Provides
    fun provideGetRecentMessages(
        messageRepository: MessageRepository,
        slowSyncRepository: SlowSyncRepository,
    ): GetRecentMessagesUseCase = GetRecentMessagesUseCase(messageRepository, slowSyncRepository)

    @Provides
    fun provideDeleteMessage(
        messageRepository: MessageRepository,
        assetRepository: AssetRepository,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        selfConversationIdProvider: SelfConversationIdProvider,
        deleteMessageAttachmentsFactory: MessageDependencyFactory<DeleteMessageAttachmentsUseCase>,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): DeleteMessageUseCase = DeleteMessageUseCase(
        messageRepository,
        assetRepository,
        slowSyncRepository,
        messageSender,
        selfUserId,
        currentClientIdProvider,
        selfConversationIdProvider,
        deleteMessageAttachmentsFactory(),
        persistenceEventHookNotifier,
    )

    @Provides
    fun provideToggleReaction(
        currentClientIdProvider: CurrentClientIdProvider,
        selfUserId: QualifiedID,
        slowSyncRepository: SlowSyncRepository,
        reactionRepository: ReactionRepository,
        messageSender: MessageSender,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): ToggleReactionUseCase = ToggleReactionUseCase(
        currentClientIdProvider,
        selfUserId,
        slowSyncRepository,
        reactionRepository,
        messageSender,
        persistenceEventHookNotifier,
    )

    @Provides
    fun provideObserveMessageReactions(reactionRepository: ReactionRepository): ObserveMessageReactionsUseCase =
        ObserveMessageReactionsUseCaseImpl(reactionRepository)

    @Provides
    fun provideObserveMessageReceipts(receiptRepository: ReceiptRepository): ObserveMessageReceiptsUseCase =
        ObserveMessageReceiptsUseCaseImpl(receiptRepository)

    @Provides
    fun provideSendKnock(
        persistMessage: PersistMessageUseCase,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    ): SendKnockUseCase = SendKnockUseCase(
        persistMessage,
        selfUserId,
        currentClientIdProvider,
        slowSyncRepository,
        messageSender,
        messageSendFailureHandler,
        observeSelfDeletingMessages,
    )

    @Provides
    fun provideSendLocation(
        persistMessage: PersistMessageUseCase,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        kaliumConfigs: KaliumConfigs,
    ): SendLocationUseCase = SendLocationUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        selfDeleteTimer = observeSelfDeletingMessages,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    fun provideMarkMessagesAsNotified(conversationRepository: ConversationRepository): MarkMessagesAsNotifiedUseCase =
        MarkMessagesAsNotifiedUseCase(conversationRepository)

    @Provides
    fun provideGetNotifications(
        connectionRepository: ConnectionRepository,
        messageRepository: MessageRepository,
        incrementalSyncRepository: IncrementalSyncRepository,
    ): GetNotificationsUseCase = GetNotificationsUseCaseImpl(
        connectionRepository = connectionRepository,
        messageRepository = messageRepository,
        incrementalSyncRepository = incrementalSyncRepository,
        notificationEventsManager = NotificationEventsManagerImpl,
    )

    @Provides
    fun provideSendConfirmation(
        currentClientIdProvider: CurrentClientIdProvider,
        syncManager: SyncManager,
        messageSender: MessageSender,
        selfUserId: QualifiedID,
        conversationRepository: ConversationRepository,
        messageRepository: MessageRepository,
        userPropertyRepository: UserPropertyRepository,
    ): SendConfirmationUseCase = SendConfirmationUseCase(
        currentClientIdProvider,
        syncManager,
        messageSender,
        selfUserId,
        conversationRepository,
        messageRepository,
        userPropertyRepository,
    )

    @Provides
    fun provideSessionResetSender(
        slowSyncRepository: SlowSyncRepository,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        messageSender: MessageSender,
        dispatcher: KaliumDispatcher,
    ): SessionResetSender = SessionResetSenderImpl(
        slowSyncRepository,
        selfUserId,
        currentClientIdProvider,
        messageSender,
        dispatcher,
    )

    @Provides
    fun provideResetSession(
        transactionProvider: CryptoTransactionProvider,
        sessionResetSender: SessionResetSender,
        messageRepository: MessageRepository,
    ): ResetSessionUseCase = ResetSessionUseCaseImpl(transactionProvider, sessionResetSender, messageRepository)

    @Provides
    fun provideSendButtonActionConfirmationMessage(
        syncManager: SyncManager,
        messageSender: MessageSender,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
    ): SendButtonActionConfirmationMessageUseCase = SendButtonActionConfirmationMessageUseCase(
        syncManager = syncManager,
        messageSender = messageSender,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
    )

    @Provides
    fun provideSendButtonActionMessage(
        syncManager: SyncManager,
        messageSender: MessageSender,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        messageMetadataRepository: MessageMetadataRepository,
        compositeMessageRepository: CompositeMessageRepository,
    ): SendButtonActionMessageUseCase = SendButtonActionMessageUseCase(
        syncManager = syncManager,
        messageSender = messageSender,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
        messageMetadataRepository = messageMetadataRepository,
        compositeMessageRepository = compositeMessageRepository,
    )

    @Provides
    @OptIn(InternalKaliumApi::class)
    fun provideSendButtonMessage(
        persistMessage: PersistMessageUseCase,
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        slowSyncRepository: SlowSyncRepository,
        messageSender: MessageSender,
        messageSendFailureHandler: MessageSendFailureHandler,
        userPropertyRepository: UserPropertyRepository,
        scope: CoroutineScope,
    ): SendButtonMessageUseCase = SendButtonMessageUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        scope = scope,
    )

    @Provides
    fun provideGetSearchedConversationMessagePosition(
        messageRepository: MessageRepository,
    ): GetSearchedConversationMessagePositionUseCase =
        GetSearchedConversationMessagePositionUseCaseImpl(messageRepository)

    @Provides
    fun provideSearchMessagesInConversation(
        messageRepository: MessageRepository,
    ): SearchMessagesInConversationUseCase = SearchMessagesInConversationUseCaseImpl(messageRepository)

    @Provides
    fun provideSearchMessagesGlobally(messageRepository: MessageRepository): SearchMessagesGloballyUseCase =
        SearchMessagesGloballyUseCaseImpl(messageRepository)

    @Provides
    fun provideObserveAssetStatuses(messageRepository: MessageRepository): ObserveAssetStatusesUseCase =
        ObserveAssetStatusesUseCaseImpl(messageRepository)

    @Provides
    fun provideSaveMessageDraft(messageDraftRepository: MessageDraftRepository): SaveMessageDraftUseCase =
        SaveMessageDraftUseCaseImpl(messageDraftRepository)

    @Provides
    fun provideGetMessageDraft(
        messageRepository: MessageRepository,
        messageDraftRepository: MessageDraftRepository,
    ): GetMessageDraftUseCase = GetMessageDraftUseCaseImpl(messageRepository, messageDraftRepository)

    @Provides
    fun provideRemoveMessageDraft(messageDraftRepository: MessageDraftRepository): RemoveMessageDraftUseCase =
        RemoveMessageDraftUseCaseImpl(messageDraftRepository)

    @Provides
    fun provideSendInCallReaction(
        selfUserId: QualifiedID,
        currentClientIdProvider: CurrentClientIdProvider,
        messageSender: MessageSender,
        dispatcher: KaliumDispatcher,
        scope: CoroutineScope,
    ): SendInCallReactionUseCase = SendInCallReactionUseCase(
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        messageSender = messageSender,
        dispatchers = dispatcher,
        scope = scope,
    )

    @Provides
    fun provideGetSenderNameByMessageId(messageRepository: MessageRepository): GetSenderNameByMessageIdUseCase =
        GetSenderNameByMessageIdUseCase(messageRepository)

    @Provides
    fun provideGetNextAudioMessageInConversation(
        messageRepository: MessageRepository,
    ): GetNextAudioMessageInConversationUseCase = GetNextAudioMessageInConversationUseCase(messageRepository)

    @Provides
    fun provideObserveAssetUploadState(
        messageRepository: MessageRepository,
        attachmentsRepositoryFactory: MessageDependencyFactory<MessageAttachmentDraftRepository>,
    ): ObserveAssetUploadStateUseCase =
        ObserveAssetUploadStateUseCaseImpl(messageRepository, attachmentsRepositoryFactory())
}
