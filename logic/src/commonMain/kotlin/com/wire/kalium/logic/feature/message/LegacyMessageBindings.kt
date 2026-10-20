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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewRepository
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewRepositoryImpl
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.timeline.MessageTimelineRepository
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserLifetime
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
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
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
import com.wire.kalium.logic.feature.message.linkpreview.DetectLinkPreviewTargetUseCase
import com.wire.kalium.logic.feature.message.linkpreview.DetectLinkPreviewTargetUseCaseImpl
import com.wire.kalium.logic.feature.message.linkpreview.GenerateLinkPreviewUseCase
import com.wire.kalium.logic.feature.message.linkpreview.GenerateLinkPreviewUseCaseImpl
import com.wire.kalium.logic.feature.message.poll.SendPollMessageUseCase
import com.wire.kalium.logic.feature.message.poll.SendPollVoteUseCase
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
import com.wire.kalium.messaging.sending.MLSMessageCreator
import com.wire.kalium.messaging.sending.MLSMessageCreatorImpl
import com.wire.kalium.messaging.sending.MessageEnvelopeCreator
import com.wire.kalium.messaging.sending.MessageEnvelopeCreatorImpl
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSendFailureHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageSenderImpl
import com.wire.kalium.persistence.dao.message.MessageTimelineDAO
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/** Distinguishes the original constructor captures and property bindings, including same-typed values. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyMessageBinding(val value: String)

/**
 * Adapts the existing session accessors without caching them or eagerly reading the full dependency list.
 * Each capture is memoized independently by its qualified provider in [LegacyMessageBindings].
 */
internal interface LegacyMessageDependencies {
    val notificationEventsManager: NotificationEventsManager
    val connectionRepository: ConnectionRepository
    val messageDraftRepository: MessageDraftRepository
    val selfUserId: QualifiedID
    val currentClientIdProvider: CurrentClientIdProvider
    val selfConversationIdProvider: SelfConversationIdProvider
    val messageRepository: MessageRepository
    val conversationRepository: ConversationRepository
    val attachmentsRepository: Lazy<MessageAttachmentDraftRepository>
    val mlsConversationRepository: MLSConversationRepository
    val clientRepository: ClientRepository
    val clientRemoteRepository: ClientRemoteRepository
    val preKeyRepository: PreKeyRepository
    val userRepository: UserRepository
    val assetRepository: AssetRepository
    val reactionRepository: ReactionRepository
    val receiptRepository: ReceiptRepository
    val syncManager: SyncManager
    val slowSyncRepository: SlowSyncRepository
    val messageSendingScheduler: MessageSendingScheduler
    val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler
    val userPropertyRepository: UserPropertyRepository
    val incrementalSyncRepository: IncrementalSyncRepository
    val kaliumFileSystem: KaliumFileSystem
    val protoContentMapper: ProtoContentMapper
    val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
    val messageMetadataRepository: MessageMetadataRepository
    val staleEpochVerifier: StaleEpochVerifier
    val legalHoldHandler: LegalHoldHandler
    val observeFileSharingStatusUseCase: ObserveFileSharingStatusUseCase
    val getMessageAttachmentsUseCase: Lazy<GetMessageAttachmentsUseCase>
    val publishAttachmentsUseCase: Lazy<PublishAttachmentsUseCase>
    val removeAttachmentDraftsUseCase: Lazy<RemoveAttachmentDraftsUseCase>
    val deleteMessageAttachmentsUseCase: Lazy<DeleteMessageAttachmentsUseCase>
    val fetchConversationUseCase: FetchConversationUseCase
    val transactionProvider: CryptoTransactionProvider
    val compositeMessageRepository: CompositeMessageRepository
    val pollMessageRepository: PollMessageRepository
    val joinExistingConversationUseCaseProvider: () -> JoinExistingMLSConversationUseCase
    val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
    val mlsMissingUsersMessageRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler
    val persistenceEventHookNotifier: PersistenceEventHookNotifier
    val linkPreviewEnabled: Boolean
    val scope: CoroutineScope
    val kaliumConfigs: KaliumConfigs
    val kaliumLogger: KaliumLogger
    val dispatcher: KaliumDispatcher
    val messageTimelineDAO: MessageTimelineDAO
}

/** Keeps an original lazy/function capture as a value; Metro reserves their raw types for deferred injection. */
internal class LegacyMessageCapture<out T>(val value: T)

/** Individual entry points shared by the compatibility facade and user runtime. */
internal interface LegacyMessageApi {
    @get:LegacyMessageBinding("messageRepository")
    val messageMessageRepository: MessageRepository

    @get:LegacyMessageBinding("dispatcher")
    val messageDispatcher: KaliumDispatcher

    @get:LegacyMessageBinding("messageTimelineRepository")
    val messageMessageTimelineRepository: MessageTimelineRepository

    @get:LegacyMessageBinding("openMessageTimeline")
    val messageOpenMessageTimeline: OpenMessageTimelineUseCase

    @get:LegacyMessageBinding("getPaginatedFlowOfMessageListItems")
    val messageGetPaginatedFlowOfMessageListItems: GetPaginatedFlowOfMessageListItemsUseCase

    @get:LegacyMessageBinding("messageSendFailureHandler")
    val messageMessageSendFailureHandler: MessageSendFailureHandler

    @get:LegacyMessageBinding("sessionEstablisher")
    val messageSessionEstablisher: SessionEstablisher

    @get:LegacyMessageBinding("messageEnvelopeCreator")
    val messageMessageEnvelopeCreator: MessageEnvelopeCreator

    @get:LegacyMessageBinding("mlsMessageCreator")
    val messageMlsMessageCreator: MLSMessageCreator

    @get:LegacyMessageBinding("validateAssetMimeTypeUseCase")
    val messageValidateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase

    @get:LegacyMessageBinding("messageContentEncoder")
    val messageMessageContentEncoder: MessageContentEncoder

    @get:LegacyMessageBinding("messageSendingInterceptor")
    val messageMessageSendingInterceptor: MessageSendingInterceptor

    @get:LegacyMessageBinding("ephemeralMessageDeletionHandler")
    val messageEphemeralMessageDeletionHandler: EphemeralMessageDeletionHandler

    @get:LegacyMessageBinding("sendDeliverSignalUseCase")
    val messageSendDeliverSignalUseCase: SendDeliverSignalUseCase

    @get:LegacyMessageBinding("confirmationDeliveryHandler")
    val messageConfirmationDeliveryHandler: ConfirmationDeliveryHandler

    @get:LegacyMessageBinding("enqueueMessageSelfDeletion")
    val messageEnqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase

    @get:LegacyMessageBinding("deleteEphemeralMessageEndDate")
    val messageDeleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase

    @get:LegacyMessageBinding("messageSender")
    val messageMessageSender: MessageSender

    @get:LegacyMessageBinding("persistMessage")
    val messagePersistMessage: PersistMessageUseCase

    @get:LegacyMessageBinding("sendTextMessage")
    val messageSendTextMessage: SendTextMessageUseCase

    @get:LegacyMessageBinding("sendMultipartMessage")
    val messageSendMultipartMessage: SendMultipartMessageUseCase

    @get:LegacyMessageBinding("sendEditTextMessage")
    val messageSendEditTextMessage: SendEditTextMessageUseCase

    @get:LegacyMessageBinding("sendEditMultipartMessage")
    val messageSendEditMultipartMessage: SendEditMultipartMessageUseCase

    @get:LegacyMessageBinding("getAssetMessageTransferStatus")
    val messageGetAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase

    @get:LegacyMessageBinding("sendPendingAssetMessage")
    val messageSendPendingAssetMessage: SendPendingAssetMessageUseCase

    @get:LegacyMessageBinding("retryFailedMessage")
    val messageRetryFailedMessage: RetryFailedMessageUseCase

    @get:LegacyMessageBinding("getMessageById")
    val messageGetMessageById: GetMessageByIdUseCase

    @get:LegacyMessageBinding("observeMessageById")
    val messageObserveMessageById: ObserveMessageByIdUseCase

    @get:LegacyMessageBinding("persistNewAssetMessageUseCase")
    val messagePersistNewAssetMessageUseCase: PersistNewAssetMessageUseCase

    @get:LegacyMessageBinding("uploadAssetUseCase")
    val messageUploadAssetUseCase: UploadAssetUseCase

    @get:LegacyMessageBinding("sendAssetMessage")
    val messageSendAssetMessage: ScheduleNewAssetMessageUseCase

    @get:LegacyMessageBinding("scheduleAssetMessage")
    val messageScheduleAssetMessage: ScheduleNewAssetMessageUseCaseImpl

    @get:LegacyMessageBinding("getAssetMessage")
    val messageGetAssetMessage: GetMessageAssetUseCase

    @get:LegacyMessageBinding("getImageAssetMessagesByConversation")
    val messageGetImageAssetMessagesByConversation: GetImageAssetMessagesForConversationUseCase

    @get:LegacyMessageBinding("getRecentMessages")
    val messageGetRecentMessages: GetRecentMessagesUseCase

    @get:LegacyMessageBinding("deleteMessage")
    val messageDeleteMessage: DeleteMessageUseCase

    @get:LegacyMessageBinding("toggleReaction")
    val messageToggleReaction: ToggleReactionUseCase

    @get:LegacyMessageBinding("observeMessageReactions")
    val messageObserveMessageReactions: ObserveMessageReactionsUseCase

    @get:LegacyMessageBinding("observeMessageReceipts")
    val messageObserveMessageReceipts: ObserveMessageReceiptsUseCase

    @get:LegacyMessageBinding("sendKnock")
    val messageSendKnock: SendKnockUseCase

    @get:LegacyMessageBinding("sendLocation")
    val messageSendLocation: SendLocationUseCase

    @get:LegacyMessageBinding("markMessagesAsNotified")
    val messageMarkMessagesAsNotified: MarkMessagesAsNotifiedUseCase

    @get:LegacyMessageBinding("updateAssetMessageTransferStatus")
    val messageUpdateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase

    @get:LegacyMessageBinding("getNotifications")
    val messageGetNotifications: GetNotificationsUseCase

    @get:LegacyMessageBinding("sendConfirmation")
    val messageSendConfirmation: SendConfirmationUseCase

    @get:LegacyMessageBinding("sessionResetSender")
    val messageSessionResetSender: SessionResetSender

    @get:LegacyMessageBinding("resetSession")
    val messageResetSession: ResetSessionUseCase

    @get:LegacyMessageBinding("sendButtonActionConfirmationMessage")
    val messageSendButtonActionConfirmationMessage: SendButtonActionConfirmationMessageUseCase

    @get:LegacyMessageBinding("sendButtonActionMessage")
    val messageSendButtonActionMessage: SendButtonActionMessageUseCase

    @OptIn(InternalKaliumApi::class)
    @get:LegacyMessageBinding("sendButtonMessage")
    val messageSendButtonMessage: SendButtonMessageUseCase

    @get:LegacyMessageBinding("sendPollMessage")
    val messageSendPollMessage: SendPollMessageUseCase

    @get:LegacyMessageBinding("sendPollVote")
    val messageSendPollVote: SendPollVoteUseCase

    @get:LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsSender")
    val messageDeleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl

    @get:LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsReceiver")
    val messageDeleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl

    @get:LegacyMessageBinding("getSearchedConversationMessagePosition")
    val messageGetSearchedConversationMessagePosition: GetSearchedConversationMessagePositionUseCase

    @get:LegacyMessageBinding("searchMessagesInConversation")
    val messageSearchMessagesInConversation: SearchMessagesInConversationUseCase

    @get:LegacyMessageBinding("searchMessagesGlobally")
    val messageSearchMessagesGlobally: SearchMessagesGloballyUseCase

    @get:LegacyMessageBinding("observeAssetStatuses")
    val messageObserveAssetStatuses: ObserveAssetStatusesUseCase

    @get:LegacyMessageBinding("saveMessageDraftUseCase")
    val messageSaveMessageDraftUseCase: SaveMessageDraftUseCase

    @get:LegacyMessageBinding("getMessageDraftUseCase")
    val messageGetMessageDraftUseCase: GetMessageDraftUseCase

    @get:LegacyMessageBinding("removeMessageDraftUseCase")
    val messageRemoveMessageDraftUseCase: RemoveMessageDraftUseCase

    @get:LegacyMessageBinding("sendInCallReactionUseCase")
    val messageSendInCallReactionUseCase: SendInCallReactionUseCase

    @get:LegacyMessageBinding("getSenderNameByMessageId")
    val messageGetSenderNameByMessageId: GetSenderNameByMessageIdUseCase

    @get:LegacyMessageBinding("getNextAudioMessageInConversation")
    val messageGetNextAudioMessageInConversation: GetNextAudioMessageInConversationUseCase

    @get:LegacyMessageBinding("observeAssetUploadState")
    val messageObserveAssetUploadState: ObserveAssetUploadStateUseCase

    @get:LegacyMessageBinding("updateAudioMessageNormalizedLoudnessUseCase")
    val messageUpdateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase

    @get:LegacyMessageBinding("linkPreviewRepository")
    val messageLinkPreviewRepository: LinkPreviewRepository

    @get:LegacyMessageBinding("generateLinkPreview")
    val messageGenerateLinkPreview: GenerateLinkPreviewUseCase

    @get:LegacyMessageBinding("detectLinkPreviewTarget")
    val messageDetectLinkPreviewTarget: DetectLinkPreviewTargetUseCase
}

/**
 * Mixed into the single user graph; this interface does not create a graph or a feature container.
 * Constructor captures and formerly cached properties retain their original user lifetime. Factory
 * getters stay unscoped, including senders retained by the scoped delivery/self-deletion handlers.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal interface LegacyMessageBindings : LegacyMessageApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("notificationEventsManager")
    fun provideMessageNotificationEventsManager(dependencies: LegacyMessageDependencies): NotificationEventsManager =
        dependencies.notificationEventsManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("connectionRepository")
    fun provideMessageConnectionRepository(dependencies: LegacyMessageDependencies): ConnectionRepository =
        dependencies.connectionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageDraftRepository")
    fun provideMessageMessageDraftRepository(dependencies: LegacyMessageDependencies): MessageDraftRepository =
        dependencies.messageDraftRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("selfUserId")
    fun provideMessageSelfUserId(dependencies: LegacyMessageDependencies): QualifiedID =
        dependencies.selfUserId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("currentClientIdProvider")
    fun provideMessageCurrentClientIdProvider(dependencies: LegacyMessageDependencies): CurrentClientIdProvider =
        dependencies.currentClientIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("selfConversationIdProvider")
    fun provideMessageSelfConversationIdProvider(dependencies: LegacyMessageDependencies): SelfConversationIdProvider =
        dependencies.selfConversationIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageRepository")
    fun provideMessageMessageRepository(dependencies: LegacyMessageDependencies): MessageRepository =
        dependencies.messageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("conversationRepository")
    fun provideMessageConversationRepository(dependencies: LegacyMessageDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("attachmentsRepository")
    fun provideMessageAttachmentsRepository(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<Lazy<MessageAttachmentDraftRepository>> =
        LegacyMessageCapture(dependencies.attachmentsRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("mlsConversationRepository")
    fun provideMessageMlsConversationRepository(dependencies: LegacyMessageDependencies): MLSConversationRepository =
        dependencies.mlsConversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("clientRepository")
    fun provideMessageClientRepository(dependencies: LegacyMessageDependencies): ClientRepository =
        dependencies.clientRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("clientRemoteRepository")
    fun provideMessageClientRemoteRepository(dependencies: LegacyMessageDependencies): ClientRemoteRepository =
        dependencies.clientRemoteRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("preKeyRepository")
    fun provideMessagePreKeyRepository(dependencies: LegacyMessageDependencies): PreKeyRepository =
        dependencies.preKeyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("userRepository")
    fun provideMessageUserRepository(dependencies: LegacyMessageDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("assetRepository")
    fun provideMessageAssetRepository(dependencies: LegacyMessageDependencies): AssetRepository =
        dependencies.assetRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("reactionRepository")
    fun provideMessageReactionRepository(dependencies: LegacyMessageDependencies): ReactionRepository =
        dependencies.reactionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("receiptRepository")
    fun provideMessageReceiptRepository(dependencies: LegacyMessageDependencies): ReceiptRepository =
        dependencies.receiptRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("syncManager")
    fun provideMessageSyncManager(dependencies: LegacyMessageDependencies): SyncManager =
        dependencies.syncManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("slowSyncRepository")
    fun provideMessageSlowSyncRepository(dependencies: LegacyMessageDependencies): SlowSyncRepository =
        dependencies.slowSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageSendingScheduler")
    fun provideMessageMessageSendingScheduler(dependencies: LegacyMessageDependencies): MessageSendingScheduler =
        dependencies.messageSendingScheduler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("audioNormalizedLoudnessScheduler")
    fun provideMessageAudioNormalizedLoudnessScheduler(dependencies: LegacyMessageDependencies): AudioNormalizedLoudnessScheduler =
        dependencies.audioNormalizedLoudnessScheduler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("userPropertyRepository")
    fun provideMessageUserPropertyRepository(dependencies: LegacyMessageDependencies): UserPropertyRepository =
        dependencies.userPropertyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("incrementalSyncRepository")
    fun provideMessageIncrementalSyncRepository(dependencies: LegacyMessageDependencies): IncrementalSyncRepository =
        dependencies.incrementalSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("kaliumFileSystem")
    fun provideMessageKaliumFileSystem(dependencies: LegacyMessageDependencies): KaliumFileSystem =
        dependencies.kaliumFileSystem

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("protoContentMapper")
    fun provideMessageProtoContentMapper(dependencies: LegacyMessageDependencies): ProtoContentMapper =
        dependencies.protoContentMapper

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("observeSelfDeletingMessages")
    fun provideMessageObserveSelfDeletingMessages(
        dependencies: LegacyMessageDependencies,
    ): ObserveSelfDeletionTimerSettingsForConversationUseCase =
        dependencies.observeSelfDeletingMessages

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageMetadataRepository")
    fun provideMessageMessageMetadataRepository(dependencies: LegacyMessageDependencies): MessageMetadataRepository =
        dependencies.messageMetadataRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("staleEpochVerifier")
    fun provideMessageStaleEpochVerifier(dependencies: LegacyMessageDependencies): StaleEpochVerifier =
        dependencies.staleEpochVerifier

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("legalHoldHandler")
    fun provideMessageLegalHoldHandler(dependencies: LegacyMessageDependencies): LegalHoldHandler =
        dependencies.legalHoldHandler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("observeFileSharingStatusUseCase")
    fun provideMessageObserveFileSharingStatusUseCase(dependencies: LegacyMessageDependencies): ObserveFileSharingStatusUseCase =
        dependencies.observeFileSharingStatusUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("getMessageAttachmentsUseCase")
    fun provideMessageGetMessageAttachmentsUseCase(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<Lazy<GetMessageAttachmentsUseCase>> =
        LegacyMessageCapture(dependencies.getMessageAttachmentsUseCase)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("publishAttachmentsUseCase")
    fun provideMessagePublishAttachmentsUseCase(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<Lazy<PublishAttachmentsUseCase>> =
        LegacyMessageCapture(dependencies.publishAttachmentsUseCase)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("removeAttachmentDraftsUseCase")
    fun provideMessageRemoveAttachmentDraftsUseCase(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<Lazy<RemoveAttachmentDraftsUseCase>> =
        LegacyMessageCapture(dependencies.removeAttachmentDraftsUseCase)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("deleteMessageAttachmentsUseCase")
    fun provideMessageDeleteMessageAttachmentsUseCase(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<Lazy<DeleteMessageAttachmentsUseCase>> =
        LegacyMessageCapture(dependencies.deleteMessageAttachmentsUseCase)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("fetchConversationUseCase")
    fun provideMessageFetchConversationUseCase(dependencies: LegacyMessageDependencies): FetchConversationUseCase =
        dependencies.fetchConversationUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("transactionProvider")
    fun provideMessageTransactionProvider(dependencies: LegacyMessageDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("compositeMessageRepository")
    fun provideMessageCompositeMessageRepository(dependencies: LegacyMessageDependencies): CompositeMessageRepository =
        dependencies.compositeMessageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("pollMessageRepository")
    fun provideMessagePollMessageRepository(dependencies: LegacyMessageDependencies): PollMessageRepository =
        dependencies.pollMessageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("joinExistingConversationUseCaseProvider")
    fun provideMessageJoinExistingConversationUseCaseProvider(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<() -> JoinExistingMLSConversationUseCase> =
        LegacyMessageCapture(dependencies.joinExistingConversationUseCaseProvider)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("audioNormalizedLoudnessBuilder")
    fun provideMessageAudioNormalizedLoudnessBuilder(dependencies: LegacyMessageDependencies): AudioNormalizedLoudnessBuilder =
        dependencies.audioNormalizedLoudnessBuilder

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("mlsMissingUsersMessageRejectionHandlerProvider")
    fun provideMessageMlsMissingUsersMessageRejectionHandlerProvider(
        dependencies: LegacyMessageDependencies,
    ): LegacyMessageCapture<() -> MLSMissingUsersMessageRejectionHandler> =
        LegacyMessageCapture(dependencies.mlsMissingUsersMessageRejectionHandlerProvider)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("persistenceEventHookNotifier")
    fun provideMessagePersistenceEventHookNotifier(dependencies: LegacyMessageDependencies): PersistenceEventHookNotifier =
        dependencies.persistenceEventHookNotifier

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("linkPreviewEnabled")
    fun provideMessageLinkPreviewEnabled(dependencies: LegacyMessageDependencies): Boolean =
        dependencies.linkPreviewEnabled

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("scope")
    fun provideMessageScope(dependencies: LegacyMessageDependencies): CoroutineScope =
        dependencies.scope

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("kaliumConfigs")
    fun provideMessageKaliumConfigs(dependencies: LegacyMessageDependencies): KaliumConfigs =
        dependencies.kaliumConfigs

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("kaliumLogger")
    fun provideMessageKaliumLogger(dependencies: LegacyMessageDependencies): KaliumLogger =
        dependencies.kaliumLogger

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("dispatcher")
    fun provideMessageDispatcher(dependencies: LegacyMessageDependencies): KaliumDispatcher =
        dependencies.dispatcher

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageTimelineDAO")
    fun provideMessageMessageTimelineDAO(dependencies: LegacyMessageDependencies): MessageTimelineDAO =
        dependencies.messageTimelineDAO

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageTimelineRepository")
    fun provideMessageMessageTimelineRepository(
        @LegacyMessageBinding("messageTimelineDAO") messageTimelineDAO: MessageTimelineDAO,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
    ): MessageTimelineRepository = MessageTimelineRepository(messageTimelineDAO, selfUserId)

    @Provides
    @LegacyMessageBinding("openMessageTimeline")
    fun provideMessageOpenMessageTimeline(
        @LegacyMessageBinding("messageTimelineRepository") messageTimelineRepository: MessageTimelineRepository,
    ): OpenMessageTimelineUseCase = OpenMessageTimelineUseCase(messageTimelineRepository)

    @Provides
    @LegacyMessageBinding("getPaginatedFlowOfMessageListItems")
    fun provideMessageGetPaginatedFlowOfMessageListItems(
        @LegacyMessageBinding("messageTimelineRepository") messageTimelineRepository: MessageTimelineRepository,
    ): GetPaginatedFlowOfMessageListItemsUseCase = GetPaginatedFlowOfMessageListItemsUseCase(messageTimelineRepository)

    @Provides
    @LegacyMessageBinding("messageSendFailureHandler")
    fun provideMessageMessageSendFailureHandler(
        @LegacyMessageBinding("proteusRecipientsDataSource") proteusRecipientsDataSource: ProteusRecipientsDataSource,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("messageSendingScheduler") messageSendingScheduler: MessageSendingScheduler,
    ): MessageSendFailureHandler = MessageSendFailureHandlerImpl(
        recipients = proteusRecipientsDataSource,
        updateMessageStatus = messageRepository::updateMessageStatus,
        scheduleResend = messageSendingScheduler::scheduleSendingOfPendingMessages
    )

    @Provides
    @LegacyMessageBinding("sessionEstablisher")
    fun provideMessageSessionEstablisher(
        @LegacyMessageBinding("preKeyRepository") preKeyRepository: PreKeyRepository,
    ): SessionEstablisher = SessionEstablisherImpl(preKeyRepository::establishSessions)

    @Provides
    @LegacyMessageBinding("messageEnvelopeCreator")
    fun provideMessageMessageEnvelopeCreator(
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("protoContentMapper") protoContentMapper: ProtoContentMapper,
    ): MessageEnvelopeCreator = MessageEnvelopeCreatorImpl(
        legalHoldStatus = { conversationRepository.observeLegalHoldStatus(it).first() },
        selfUserId = selfUserId,
        protoContentMapper = protoContentMapper
    )

    @Provides
    @LegacyMessageBinding("mlsMessageCreator")
    fun provideMessageMlsMessageCreator(
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyMessageBinding("joinExistingConversationUseCaseProvider")
        joinExistingConversationUseCaseProvider: LegacyMessageCapture<() -> JoinExistingMLSConversationUseCase>,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("protoContentMapper") protoContentMapper: ProtoContentMapper,
    ): MLSMessageCreator = MLSMessageCreatorImpl(
        legalHoldStatus = { conversationRepository.observeLegalHoldStatus(it).first() },
        commitPendingProposals = mlsConversationRepository::commitPendingProposals,
        joinConversation = { context, conversationId -> joinExistingConversationUseCaseProvider.value()(context, conversationId) },
        encrypt = mlsConversationRepository::encryptMessage,
        selfUserId = selfUserId,
        protoContentMapper = protoContentMapper
    )

    @Provides
    @LegacyMessageBinding("validateAssetMimeTypeUseCase")
    fun provideMessageValidateAssetMimeTypeUseCase(): ValidateAssetFileTypeUseCase = ValidateAssetFileTypeUseCaseImpl()

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("messageContentEncoder")
    fun provideMessageMessageContentEncoder(): MessageContentEncoder = MessageContentEncoder()

    @Provides
    @LegacyMessageBinding("messageSendingInterceptor")
    fun provideMessageMessageSendingInterceptor(
        @LegacyMessageBinding("messageContentEncoder") messageContentEncoder: MessageContentEncoder,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): MessageSendingInterceptor = MessageSendingInterceptorImpl(messageContentEncoder, messageRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("ephemeralMessageDeletionHandler")
    fun provideMessageEphemeralMessageDeletionHandler(
        @LegacyMessageBinding("scope") scope: CoroutineScope,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsReceiver")
        deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl,
        @LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsSender")
        deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("kaliumLogger") kaliumLogger: KaliumLogger,
    ): EphemeralMessageDeletionHandler = EphemeralMessageDeletionHandlerImpl(
        userSessionCoroutineScope = scope,
        messageRepository = messageRepository,
        deleteEphemeralMessageForSelfUserAsReceiver = deleteEphemeralMessageForSelfUserAsReceiver,
        deleteEphemeralMessageForSelfUserAsSender = deleteEphemeralMessageForSelfUserAsSender,
        selfUserId = selfUserId,
        kaliumLogger = kaliumLogger
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("sendDeliverSignalUseCase")
    fun provideMessageSendDeliverSignalUseCase(
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("kaliumLogger") kaliumLogger: KaliumLogger,
    ): SendDeliverSignalUseCase = SendDeliverSignalUseCaseImpl(
        selfUserId = selfUserId,
        messageSender = messageSender,
        currentClientIdProvider = currentClientIdProvider,
        kaliumLogger = kaliumLogger
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("confirmationDeliveryHandler")
    fun provideMessageConfirmationDeliveryHandler(
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("sendDeliverSignalUseCase") sendDeliverSignalUseCase: SendDeliverSignalUseCase,
        @LegacyMessageBinding("kaliumLogger") kaliumLogger: KaliumLogger,
    ): ConfirmationDeliveryHandler = ConfirmationDeliveryHandlerImpl(
        syncManager = syncManager,
        conversationRepository = conversationRepository,
        sendDeliverSignalUseCase = sendDeliverSignalUseCase,
        kaliumLogger = kaliumLogger,
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("enqueueMessageSelfDeletion")
    fun provideMessageEnqueueMessageSelfDeletion(
        @LegacyMessageBinding("ephemeralMessageDeletionHandler") ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler,
    ): EnqueueMessageSelfDeletionUseCase = EnqueueMessageSelfDeletionUseCaseImpl(
        ephemeralMessageDeletionHandler = ephemeralMessageDeletionHandler
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("deleteEphemeralMessageEndDate")
    fun provideMessageDeleteEphemeralMessageEndDate(
        @LegacyMessageBinding("ephemeralMessageDeletionHandler") ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler,
    ): DeleteEphemeralMessagesAfterEndDateUseCase = DeleteEphemeralMessagesAfterEndDateUseCaseImpl(
        ephemeralMessageDeletionHandler = ephemeralMessageDeletionHandler
    )

    // The original sender looked up this handler only when sending. Defer that edge to preserve
    // the handler -> receiver -> sender capture without constructing the handler recursively.
    @Provides
    @LegacyMessageBinding("messageSender")
    fun provideMessageMessageSender(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("syncedSendingSession") syncedSendingSession: SyncedSendingSession,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("sessionEstablisher") sessionEstablisher: SessionEstablisher,
        @LegacyMessageBinding("messageEnvelopeCreator") messageEnvelopeCreator: MessageEnvelopeCreator,
        @LegacyMessageBinding("mlsMessageCreator") mlsMessageCreator: MLSMessageCreator,
        @LegacyMessageBinding("messageSendingInterceptor") messageSendingInterceptor: MessageSendingInterceptor,
        @LegacyMessageBinding("legalHoldHandler") legalHoldHandler: LegalHoldHandler,
        @LegacyMessageBinding("staleEpochVerifier") staleEpochVerifier: StaleEpochVerifier,
        @LegacyMessageBinding("mlsMissingUsersMessageRejectionHandlerProvider")
        mlsMissingUsersMessageRejectionHandlerProvider: LegacyMessageCapture<() -> MLSMissingUsersMessageRejectionHandler>,
        @LegacyMessageBinding("userRepository") userRepository: UserRepository,
        @LegacyMessageBinding("ephemeralMessageDeletionHandler") ephemeralMessageDeletionHandler: Provider<EphemeralMessageDeletionHandler>,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
    ): MessageSender = MessageSenderImpl(
        messages = messageRepository,
        transport = messageRepository,
        conversations = conversationRepository,
        session = syncedSendingSession,
        messageSendFailureHandler = messageSendFailureHandler,
        sessionEstablisher = sessionEstablisher,
        messageEnvelopeCreator = messageEnvelopeCreator,
        mlsMessageCreator = mlsMessageCreator,
        prepareMessage = messageSendingInterceptor::prepareMessage,
        legalHoldEnabledAfter = legalHoldHandler::handleMessageSendFailure,
        verifyEpoch = { context, conversationId -> staleEpochVerifier.verifyEpoch(context, conversationId) },
        handleGroupOutOfSync = mlsMissingUsersMessageRejectionHandlerProvider.value()::handle,
        broadcastRecipients = userRepository::getAllRecipients,
        enqueueSelfDeletion = { message, expirationData ->
            ephemeralMessageDeletionHandler().enqueueSelfDeletion(message, expirationData)
        },
        scope = scope
    )

    @Provides
    @LegacyMessageBinding("persistMessage")
    fun provideMessagePersistMessage(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("notificationEventsManager") notificationEventsManager: NotificationEventsManager,
        @LegacyMessageBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): PersistMessageUseCase = PersistMessageUseCaseImpl(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        notificationEventsManager = notificationEventsManager,
        persistMessageHookNotifier = persistenceEventHookNotifier
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("sendTextMessage")
    fun provideMessageSendTextMessage(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("messageDraftRepository") messageDraftRepository: MessageDraftRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("observeSelfDeletingMessages")
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): SendTextMessageUseCase = SendTextMessageUseCase(
        persistMessage = persistMessage,
        messageDraftRepository = messageDraftRepository,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        assetDataSource = assetRepository,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        selfDeleteTimer = observeSelfDeletingMessages,
        scope = scope,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages
    )

    @Provides
    @LegacyMessageBinding("sendMultipartMessage")
    fun provideMessageSendMultipartMessage(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("attachmentsRepository") attachmentsRepository: LegacyMessageCapture<Lazy<MessageAttachmentDraftRepository>>,
        @LegacyMessageBinding("observeSelfDeletingMessages")
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        @LegacyMessageBinding("publishAttachmentsUseCase") publishAttachmentsUseCase: LegacyMessageCapture<Lazy<PublishAttachmentsUseCase>>,
        @LegacyMessageBinding("removeAttachmentDraftsUseCase")
        removeAttachmentDraftsUseCase: LegacyMessageCapture<Lazy<RemoveAttachmentDraftsUseCase>>,
        @LegacyMessageBinding("sendAssetMessage") sendAssetMessage: ScheduleNewAssetMessageUseCase,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
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
        attachmentsRepository = attachmentsRepository.value.value,
        selfDeleteTimer = observeSelfDeletingMessages,
        publishAttachments = publishAttachmentsUseCase.value.value,
        removeAttachmentDrafts = removeAttachmentDraftsUseCase.value.value,
        sendAssetMessage = sendAssetMessage,
        scope = scope
    )

    @Provides
    @LegacyMessageBinding("sendEditTextMessage")
    fun provideMessageSendEditTextMessage(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): SendEditTextMessageUseCase = SendEditTextMessageUseCase(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages
    )

    @Provides
    @LegacyMessageBinding("sendEditMultipartMessage")
    fun provideMessageSendEditMultipartMessage(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("getMessageAttachmentsUseCase")
        getMessageAttachmentsUseCase: LegacyMessageCapture<Lazy<GetMessageAttachmentsUseCase>>,
    ): SendEditMultipartMessageUseCase = SendEditMultipartMessageUseCase(
        messageRepository = messageRepository,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        getMessageAttachments = getMessageAttachmentsUseCase.value.value,
    )

    @Provides
    @LegacyMessageBinding("getAssetMessageTransferStatus")
    fun provideMessageGetAssetMessageTransferStatus(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
    ): GetAssetMessageTransferStatusUseCase = GetAssetMessageTransferStatusUseCaseImpl(
        messageRepository,
        dispatcher
    )

    @Provides
    @LegacyMessageBinding("sendPendingAssetMessage")
    fun provideMessageSendPendingAssetMessage(
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("updateAssetMessageTransferStatus") updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("getAssetMessageTransferStatus") getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("audioNormalizedLoudnessBuilder") audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
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
    @LegacyMessageBinding("retryFailedMessage")
    fun provideMessageRetryFailedMessage(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("attachmentsRepository") attachmentsRepository: LegacyMessageCapture<Lazy<MessageAttachmentDraftRepository>>,
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("publishAttachmentsUseCase") publishAttachmentsUseCase: LegacyMessageCapture<Lazy<PublishAttachmentsUseCase>>,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("updateAssetMessageTransferStatus") updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("getAssetMessageTransferStatus") getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
    ): RetryFailedMessageUseCase = RetryFailedMessageUseCase(
        messageRepository,
        assetRepository,
        conversationRepository,
        attachmentsRepository.value.value,
        persistMessage,
        publishAttachmentsUseCase.value.value,
        scope,
        dispatcher,
        messageSender,
        updateAssetMessageTransferStatus,
        getAssetMessageTransferStatus,
        messageSendFailureHandler
    )

    @Provides
    @LegacyMessageBinding("getMessageById")
    fun provideMessageGetMessageById(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): GetMessageByIdUseCase = GetMessageByIdUseCase(messageRepository)

    @Provides
    @LegacyMessageBinding("observeMessageById")
    fun provideMessageObserveMessageById(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): ObserveMessageByIdUseCase = ObserveMessageByIdUseCase(messageRepository)

    @Provides
    @LegacyMessageBinding("persistNewAssetMessageUseCase")
    fun provideMessagePersistNewAssetMessageUseCase(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("observeSelfDeletingMessages")
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
    ): PersistNewAssetMessageUseCase = PersistNewAssetMessageUseCaseImpl(
        persistMessage,
        currentClientIdProvider,
        userPropertyRepository,
        observeSelfDeletingMessages,
        assetRepository,
        dispatcher
    )

    @Provides
    @LegacyMessageBinding("uploadAssetUseCase")
    fun provideMessageUploadAssetUseCase(
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("updateAssetMessageTransferStatus") updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("updateAudioMessageNormalizedLoudnessUseCase")
        updateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase,
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("audioNormalizedLoudnessBuilder") audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): UploadAssetUseCase = UploadAssetUseCaseImpl(
        assetDataSource = assetRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
        updateAudioNormalizedLoudness = updateAudioMessageNormalizedLoudnessUseCase,
        persistMessage = persistMessage,
        audioNormalizedLoudnessBuilder = audioNormalizedLoudnessBuilder,
        dispatcher = dispatcher,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    @LegacyMessageBinding("sendAssetMessage")
    fun provideMessageSendAssetMessage(
        @LegacyMessageBinding("scheduleAssetMessage") scheduleAssetMessage: ScheduleNewAssetMessageUseCaseImpl,
    ): ScheduleNewAssetMessageUseCase = scheduleAssetMessage

    @Provides
    @LegacyMessageBinding("scheduleAssetMessage")
    fun provideMessageScheduleAssetMessage(
        @LegacyMessageBinding("persistNewAssetMessageUseCase") persistNewAssetMessageUseCase: PersistNewAssetMessageUseCase,
        @LegacyMessageBinding("uploadAssetUseCase") uploadAssetUseCase: UploadAssetUseCase,
        @LegacyMessageBinding("updateAssetMessageTransferStatus") updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("observeFileSharingStatusUseCase") observeFileSharingStatusUseCase: ObserveFileSharingStatusUseCase,
        @LegacyMessageBinding("validateAssetMimeTypeUseCase") validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): ScheduleNewAssetMessageUseCaseImpl = ScheduleNewAssetMessageUseCaseImpl(
        persistNewAssetMessage = persistNewAssetMessageUseCase,
        uploadAsset = uploadAssetUseCase,
        updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
        userId = selfUserId,
        slowSyncRepository = slowSyncRepository,
        messageRepository = messageRepository,
        observeFileSharingStatus = observeFileSharingStatusUseCase,
        validateAssetFileUseCase = validateAssetMimeTypeUseCase,
        messageSendFailureHandler = messageSendFailureHandler,
        scope = scope,
        dispatcher = dispatcher,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
    )

    @Provides
    @LegacyMessageBinding("getAssetMessage")
    fun provideMessageGetAssetMessage(
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("userRepository") userRepository: UserRepository,
        @LegacyMessageBinding("updateAssetMessageTransferStatus") updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase,
        @LegacyMessageBinding("audioNormalizedLoudnessScheduler") audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
    ): GetMessageAssetUseCase = GetMessageAssetUseCaseImpl(
        assetRepository,
        messageRepository,
        userRepository,
        updateAssetMessageTransferStatus,
        audioNormalizedLoudnessScheduler,
        scope,
        dispatcher
    )

    @Provides
    @LegacyMessageBinding("getImageAssetMessagesByConversation")
    fun provideMessageGetImageAssetMessagesByConversation(
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): GetImageAssetMessagesForConversationUseCase = GetImageAssetMessagesForConversationUseCaseImpl(
        dispatcher,
        messageRepository
    )

    @Provides
    @LegacyMessageBinding("getRecentMessages")
    fun provideMessageGetRecentMessages(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
    ): GetRecentMessagesUseCase = GetRecentMessagesUseCase(
        messageRepository,
        slowSyncRepository
    )

    @Provides
    @LegacyMessageBinding("deleteMessage")
    fun provideMessageDeleteMessage(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("selfConversationIdProvider") selfConversationIdProvider: SelfConversationIdProvider,
        @LegacyMessageBinding("deleteMessageAttachmentsUseCase")
        deleteMessageAttachmentsUseCase: LegacyMessageCapture<Lazy<DeleteMessageAttachmentsUseCase>>,
        @LegacyMessageBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): DeleteMessageUseCase = DeleteMessageUseCase(
        messageRepository,
        assetRepository,
        slowSyncRepository,
        messageSender,
        selfUserId,
        currentClientIdProvider,
        selfConversationIdProvider,
        deleteMessageAttachmentsUseCase.value.value,
        persistenceEventHookNotifier,
    )

    @Provides
    @LegacyMessageBinding("toggleReaction")
    fun provideMessageToggleReaction(
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("reactionRepository") reactionRepository: ReactionRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): ToggleReactionUseCase = ToggleReactionUseCase(
        currentClientIdProvider,
        selfUserId,
        slowSyncRepository,
        reactionRepository,
        messageSender,
        persistenceEventHookNotifier
    )

    @Provides
    @LegacyMessageBinding("observeMessageReactions")
    fun provideMessageObserveMessageReactions(
        @LegacyMessageBinding("reactionRepository") reactionRepository: ReactionRepository,
    ): ObserveMessageReactionsUseCase = ObserveMessageReactionsUseCaseImpl(
        reactionRepository = reactionRepository
    )

    @Provides
    @LegacyMessageBinding("observeMessageReceipts")
    fun provideMessageObserveMessageReceipts(
        @LegacyMessageBinding("receiptRepository") receiptRepository: ReceiptRepository,
    ): ObserveMessageReceiptsUseCase = ObserveMessageReceiptsUseCaseImpl(
        receiptRepository = receiptRepository
    )

    @Provides
    @LegacyMessageBinding("sendKnock")
    fun provideMessageSendKnock(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("observeSelfDeletingMessages")
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    ): SendKnockUseCase = SendKnockUseCase(
        persistMessage,
        selfUserId,
        currentClientIdProvider,
        slowSyncRepository,
        messageSender,
        messageSendFailureHandler,
        observeSelfDeletingMessages
    )

    @Provides
    @LegacyMessageBinding("sendLocation")
    fun provideMessageSendLocation(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("observeSelfDeletingMessages")
        observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
    ): SendLocationUseCase = SendLocationUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        selfDeleteTimer = observeSelfDeletingMessages,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages
    )

    @Provides
    @LegacyMessageBinding("markMessagesAsNotified")
    fun provideMessageMarkMessagesAsNotified(
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): MarkMessagesAsNotifiedUseCase = MarkMessagesAsNotifiedUseCase(conversationRepository)

    @Provides
    @LegacyMessageBinding("updateAssetMessageTransferStatus")
    fun provideMessageUpdateAssetMessageTransferStatus(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): UpdateAssetMessageTransferStatusUseCase = UpdateAssetMessageTransferStatusUseCaseImpl(
        messageRepository
    )

    @Provides
    @LegacyMessageBinding("getNotifications")
    fun provideMessageGetNotifications(
        @LegacyMessageBinding("connectionRepository") connectionRepository: ConnectionRepository,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("incrementalSyncRepository") incrementalSyncRepository: IncrementalSyncRepository,
        @LegacyMessageBinding("notificationEventsManager") notificationEventsManager: NotificationEventsManager,
    ): GetNotificationsUseCase = GetNotificationsUseCaseImpl(
        connectionRepository = connectionRepository,
        messageRepository = messageRepository,
        incrementalSyncRepository = incrementalSyncRepository,
        notificationEventsManager = notificationEventsManager
    )

    @Provides
    @LegacyMessageBinding("sendConfirmation")
    fun provideMessageSendConfirmation(
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): SendConfirmationUseCase = SendConfirmationUseCase(
        currentClientIdProvider,
        syncManager,
        messageSender,
        selfUserId,
        conversationRepository,
        messageRepository,
        userPropertyRepository
    )

    @Provides
    @LegacyMessageBinding("sessionResetSender")
    fun provideMessageSessionResetSender(
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
    ): SessionResetSender = SessionResetSenderImpl(slowSyncRepository, selfUserId, currentClientIdProvider, messageSender, dispatcher)

    @Provides
    @LegacyMessageBinding("resetSession")
    fun provideMessageResetSession(
        @LegacyMessageBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyMessageBinding("sessionResetSender") sessionResetSender: SessionResetSender,
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): ResetSessionUseCase = ResetSessionUseCaseImpl(transactionProvider, sessionResetSender, messageRepository)

    @Provides
    @LegacyMessageBinding("sendButtonActionConfirmationMessage")
    fun provideMessageSendButtonActionConfirmationMessage(
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
    ): SendButtonActionConfirmationMessageUseCase = SendButtonActionConfirmationMessageUseCase(
        syncManager = syncManager,
        messageSender = messageSender,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider
    )

    @Provides
    @LegacyMessageBinding("sendButtonActionMessage")
    fun provideMessageSendButtonActionMessage(
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("messageMetadataRepository") messageMetadataRepository: MessageMetadataRepository,
        @LegacyMessageBinding("compositeMessageRepository") compositeMessageRepository: CompositeMessageRepository,
    ): SendButtonActionMessageUseCase = SendButtonActionMessageUseCase(
        syncManager = syncManager,
        messageSender = messageSender,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
        messageMetadataRepository = messageMetadataRepository,
        compositeMessageRepository = compositeMessageRepository
    )

    @OptIn(InternalKaliumApi::class)
    @Provides
    @LegacyMessageBinding("sendButtonMessage")
    fun provideMessageSendButtonMessage(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
    ): SendButtonMessageUseCase = SendButtonMessageUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        scope = scope
    )

    @Provides
    @LegacyMessageBinding("sendPollMessage")
    fun provideMessageSendPollMessage(
        @LegacyMessageBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("kaliumConfigs") kaliumConfigs: KaliumConfigs,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
    ): SendPollMessageUseCase = SendPollMessageUseCase(
        persistMessage = persistMessage,
        selfUserId = selfUserId,
        currentClientIdProvider = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        messageSendFailureHandler = messageSendFailureHandler,
        userPropertyRepository = userPropertyRepository,
        pendingMessagesEnabled = kaliumConfigs.pendingMessages,
        scope = scope
    )

    @Provides
    @LegacyMessageBinding("sendPollVote")
    fun provideMessageSendPollVote(
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("pollMessageRepository") pollMessageRepository: PollMessageRepository,
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
    ): SendPollVoteUseCase = SendPollVoteUseCase(
        messageSender = messageSender,
        pollMessageRepository = pollMessageRepository,
        syncManager = syncManager,
        currentClientIdProvider = currentClientIdProvider,
        selfUserId = selfUserId
    )

    @Provides
    @LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsSender")
    fun provideMessageDeleteEphemeralMessageForSelfUserAsSender(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
    ): DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl = DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(
        messageRepository = messageRepository,
        assetRepository = assetRepository,
    )

    @Provides
    @LegacyMessageBinding("deleteEphemeralMessageForSelfUserAsReceiver")
    fun provideMessageDeleteEphemeralMessageForSelfUserAsReceiver(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("selfConversationIdProvider") selfConversationIdProvider: SelfConversationIdProvider,
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
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
    @LegacyMessageBinding("getSearchedConversationMessagePosition")
    fun provideMessageGetSearchedConversationMessagePosition(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): GetSearchedConversationMessagePositionUseCase = GetSearchedConversationMessagePositionUseCaseImpl(
        messageRepository = messageRepository
    )

    @Provides
    @LegacyMessageBinding("searchMessagesInConversation")
    fun provideMessageSearchMessagesInConversation(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): SearchMessagesInConversationUseCase = SearchMessagesInConversationUseCaseImpl(
        messageRepository = messageRepository
    )

    @Provides
    @LegacyMessageBinding("searchMessagesGlobally")
    fun provideMessageSearchMessagesGlobally(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): SearchMessagesGloballyUseCase = SearchMessagesGloballyUseCaseImpl(
        messageRepository = messageRepository
    )

    @Provides
    @LegacyMessageBinding("observeAssetStatuses")
    fun provideMessageObserveAssetStatuses(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): ObserveAssetStatusesUseCase = ObserveAssetStatusesUseCaseImpl(messageRepository)

    @Provides
    @LegacyMessageBinding("saveMessageDraftUseCase")
    fun provideMessageSaveMessageDraftUseCase(
        @LegacyMessageBinding("messageDraftRepository") messageDraftRepository: MessageDraftRepository,
    ): SaveMessageDraftUseCase = SaveMessageDraftUseCaseImpl(messageDraftRepository)

    @Provides
    @LegacyMessageBinding("getMessageDraftUseCase")
    fun provideMessageGetMessageDraftUseCase(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("messageDraftRepository") messageDraftRepository: MessageDraftRepository,
    ): GetMessageDraftUseCase = GetMessageDraftUseCaseImpl(messageRepository, messageDraftRepository)

    @Provides
    @LegacyMessageBinding("removeMessageDraftUseCase")
    fun provideMessageRemoveMessageDraftUseCase(
        @LegacyMessageBinding("messageDraftRepository") messageDraftRepository: MessageDraftRepository,
    ): RemoveMessageDraftUseCase = RemoveMessageDraftUseCaseImpl(messageDraftRepository)

    @Provides
    @LegacyMessageBinding("sendInCallReactionUseCase")
    fun provideMessageSendInCallReactionUseCase(
        @LegacyMessageBinding("selfUserId") selfUserId: QualifiedID,
        @LegacyMessageBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyMessageBinding("messageSender") messageSender: MessageSender,
        @LegacyMessageBinding("dispatcher") dispatcher: KaliumDispatcher,
        @LegacyMessageBinding("scope") scope: CoroutineScope,
    ): SendInCallReactionUseCase = SendInCallReactionUseCase(
        selfUserId = selfUserId,
        provideClientId = currentClientIdProvider,
        messageSender = messageSender,
        dispatchers = dispatcher,
        scope = scope,
    )

    @Provides
    @LegacyMessageBinding("getSenderNameByMessageId")
    fun provideMessageGetSenderNameByMessageId(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): GetSenderNameByMessageIdUseCase = GetSenderNameByMessageIdUseCase(messageRepository)

    @Provides
    @LegacyMessageBinding("getNextAudioMessageInConversation")
    fun provideMessageGetNextAudioMessageInConversation(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): GetNextAudioMessageInConversationUseCase = GetNextAudioMessageInConversationUseCase(messageRepository)

    @Provides
    @LegacyMessageBinding("observeAssetUploadState")
    fun provideMessageObserveAssetUploadState(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyMessageBinding("attachmentsRepository") attachmentsRepository: LegacyMessageCapture<Lazy<MessageAttachmentDraftRepository>>,
    ): ObserveAssetUploadStateUseCase = ObserveAssetUploadStateUseCaseImpl(messageRepository, attachmentsRepository.value.value)

    @Provides
    @LegacyMessageBinding("updateAudioMessageNormalizedLoudnessUseCase")
    fun provideMessageUpdateAudioMessageNormalizedLoudnessUseCase(
        @LegacyMessageBinding("messageRepository") messageRepository: MessageRepository,
    ): UpdateAudioMessageNormalizedLoudnessUseCase = UpdateAudioMessageNormalizedLoudnessUseCaseImpl(messageRepository = messageRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyMessageBinding("linkPreviewRepository")
    fun provideMessageLinkPreviewRepository(
        @LegacyMessageBinding("linkPreviewHttpClient") linkPreviewHttpClient: io.ktor.client.HttpClient,
        @LegacyMessageBinding("kaliumFileSystem") kaliumFileSystem: KaliumFileSystem,
    ): LinkPreviewRepository = LinkPreviewRepositoryImpl(
        httpClient = linkPreviewHttpClient,
        kaliumFileSystem = kaliumFileSystem
    )

    @Provides
    @LegacyMessageBinding("generateLinkPreview")
    fun provideMessageGenerateLinkPreview(
        @LegacyMessageBinding("linkPreviewRepository") linkPreviewRepository: LinkPreviewRepository,
        @LegacyMessageBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyMessageBinding("linkPreviewEnabled") linkPreviewEnabled: Boolean,
    ): GenerateLinkPreviewUseCase = GenerateLinkPreviewUseCaseImpl(
        repository = linkPreviewRepository,
        userPropertyRepository = userPropertyRepository,
        linkPreviewEnabled = linkPreviewEnabled
    )

    @Provides
    @LegacyMessageBinding("detectLinkPreviewTarget")
    fun provideMessageDetectLinkPreviewTarget(): DetectLinkPreviewTargetUseCase = DetectLinkPreviewTargetUseCaseImpl()

    @Provides
    @LegacyMessageBinding("proteusRecipientsDataSource")
    fun provideMessageProteusRecipientsDataSource(
        @LegacyMessageBinding("userRepository") userRepository: UserRepository,
        @LegacyMessageBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyMessageBinding("clientRemoteRepository") clientRemoteRepository: ClientRemoteRepository,
        @LegacyMessageBinding("fetchConversationUseCase") fetchConversationUseCase: FetchConversationUseCase,
    ): ProteusRecipientsDataSource = ProteusRecipientsDataSource(
        userRepository,
        clientRepository,
        clientRemoteRepository,
        fetchConversationUseCase
    )

    @Provides
    @LegacyMessageBinding("syncedSendingSession")
    fun provideMessageSyncedSendingSession(
        @LegacyMessageBinding("syncManager") syncManager: SyncManager,
        @LegacyMessageBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): SyncedSendingSession = SyncedSendingSession(syncManager, transactionProvider)

    @Provides
    @LegacyMessageBinding("linkPreviewHttpClient")
    fun provideMessageLinkPreviewHttpClient(): io.ktor.client.HttpClient = io.ktor.client.HttpClient {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 5.seconds.inWholeMilliseconds
            connectTimeoutMillis = 3.seconds.inWholeMilliseconds
            socketTimeoutMillis = 5.seconds.inWholeMilliseconds
        }
        install(io.ktor.client.plugins.UserAgent) {
            agent = "Wire LinkPreviewFetcher"
        }
        followRedirects = false
        expectSuccess = false
    }
}
