/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageUploadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageUploadStatusUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandlerImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList")
class MessageScope internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val selfUserId: QualifiedID,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    internal val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val clientRepository: ClientRepository,
    private val proteusClientProvider: ProteusClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val userRepository: UserRepository,
    private val assetRepository: AssetRepository,
    private val reactionRepository: ReactionRepository,
    private val receiptRepository: ReceiptRepository,
    private val syncManager: SyncManager,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val userPropertyRepository: UserPropertyRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val protoContentMapper: ProtoContentMapper,
    private val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val scope: CoroutineScope,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(userRepository, clientRepository, messageRepository, messageSendingScheduler)

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(proteusClientProvider, preKeyRepository)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(
            proteusClientProvider = proteusClientProvider,
            selfUserId = selfUserId,
            protoContentMapper = protoContentMapper
        )

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(
            mlsClientProvider = mlsClientProvider,
            selfUserId = selfUserId,
            protoContentMapper = protoContentMapper
        )

    private val messageContentEncoder = MessageContentEncoder()
    private val messageSendingInterceptor: MessageSendingInterceptor
        get() = MessageSendingInterceptorImpl(messageContentEncoder, messageRepository)

    internal val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler =
        EphemeralMessageDeletionHandlerImpl(
            userSessionCoroutineScope = scope,
            messageRepository = messageRepository,
            deleteEphemeralMessageForSelfUserAsReceiver = deleteEphemeralMessageForSelfUserAsReceiver,
            deleteEphemeralMessageForSelfUserAsSender = deleteEphemeralMessageForSelfUserAsSender,
            selfUserId = selfUserId
        )

    private val deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
        get() = DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(messageRepository)

    val enqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase = EnqueueMessageSelfDeletionUseCaseImpl(
        ephemeralMessageDeletionHandler = ephemeralMessageDeletionHandler
    )

    internal val messageSender: MessageSender
        get() = MessageSenderImpl(
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            syncManager,
            messageSendFailureHandler,
            sessionEstablisher,
            messageEnvelopeCreator,
            mlsMessageCreator,
            messageSendingInterceptor,
            userRepository,
            { message, expirationData -> ephemeralMessageDeletionHandler.enqueueSelfDeletion(message, expirationData) },
            scope
        )

    val persistMessage: PersistMessageUseCase
        get() = PersistMessageUseCaseImpl(messageRepository, selfUserId)

    val sendTextMessage: SendTextMessageUseCase
        get() = SendTextMessageUseCase(
            persistMessage = persistMessage,
            selfUserId = selfUserId,
            provideClientId = currentClientIdProvider,
            slowSyncRepository = slowSyncRepository,
            messageSender = messageSender,
            messageSendFailureHandler = messageSendFailureHandler,
            userPropertyRepository = userPropertyRepository,
            selfDeleteTimer = observeSelfDeletingMessages,
            scope = scope
        )

    val sendEditTextMessage: SendEditTextMessageUseCase
        get() = SendEditTextMessageUseCase(
            messageRepository,
            selfUserId,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler
        )

    val retryFailedMessage: RetryFailedMessageUseCase
        get() = RetryFailedMessageUseCase(
            messageRepository,
            assetRepository,
            persistMessage,
            scope,
            dispatcher,
            messageSender,
            updateAssetMessageUploadStatus,
            messageSendFailureHandler
        )

    val getMessageById: GetMessageByIdUseCase
        get() = GetMessageByIdUseCase(messageRepository)

    val sendAssetMessage: ScheduleNewAssetMessageUseCase
        get() = ScheduleNewAssetMessageUseCaseImpl(
            persistMessage,
            updateAssetMessageUploadStatus,
            currentClientIdProvider,
            assetRepository,
            selfUserId,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            messageRepository,
            userPropertyRepository,
            observeSelfDeletingMessages,
            scope,
            dispatcher
        )

    val getAssetMessage: GetMessageAssetUseCase
        get() = GetMessageAssetUseCaseImpl(
            assetRepository,
            messageRepository,
            userRepository,
            updateAssetMessageDownloadStatus,
            scope,
            dispatcher
        )

    val getRecentMessages: GetRecentMessagesUseCase
        get() = GetRecentMessagesUseCase(
            messageRepository,
            slowSyncRepository
        )

    val deleteMessage: DeleteMessageUseCase
        get() = DeleteMessageUseCase(
            messageRepository,
            assetRepository,
            slowSyncRepository,
            messageSender,
            selfUserId,
            currentClientIdProvider,
            selfConversationIdProvider
        )

    val toggleReaction: ToggleReactionUseCase
        get() = ToggleReactionUseCase(
            currentClientIdProvider,
            selfUserId,
            slowSyncRepository,
            reactionRepository,
            messageSender
        )

    val observeMessageReactions: ObserveMessageReactionsUseCase
        get() = ObserveMessageReactionsUseCaseImpl(
            reactionRepository = reactionRepository
        )

    val observeMessageReceipts: ObserveMessageReceiptsUseCase
        get() = ObserveMessageReceiptsUseCaseImpl(
            receiptRepository = receiptRepository
        )

    val sendKnock: SendKnockUseCase
        get() = SendKnockUseCase(
            persistMessage,
            selfUserId,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            observeSelfDeletingMessages
        )

    val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase
        get() = MarkMessagesAsNotifiedUseCase(conversationRepository)

    val updateAssetMessageUploadStatus: UpdateAssetMessageUploadStatusUseCase
        get() = UpdateAssetMessageUploadStatusUseCaseImpl(
            messageRepository
        )

    val updateAssetMessageDownloadStatus: UpdateAssetMessageDownloadStatusUseCase
        get() = UpdateAssetMessageDownloadStatusUseCaseImpl(
            messageRepository
        )

    val getNotifications: GetNotificationsUseCase
        get() = GetNotificationsUseCaseImpl(
            connectionRepository = connectionRepository,
            messageRepository = messageRepository,
            incrementalSyncRepository = incrementalSyncRepository,
            deleteConversationNotificationsManager = DeleteConversationNotificationsManagerImpl
        )

    internal val sendConfirmation: SendConfirmationUseCase
        get() = SendConfirmationUseCase(
            currentClientIdProvider,
            syncManager,
            messageSender,
            selfUserId,
            conversationRepository,
            messageRepository,
            userPropertyRepository
        )

    private val sessionResetSender: SessionResetSender
        get() = SessionResetSenderImpl(slowSyncRepository, selfUserId, currentClientIdProvider, messageSender, dispatcher)

    val resetSession: ResetSessionUseCase
        get() = ResetSessionUseCaseImpl(proteusClientProvider, sessionResetSender, messageRepository)

    private val deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
        get() = DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository,
            currentClientIdProvider = currentClientIdProvider,
            messageSender = messageSender,
            selfUserId = selfUserId,
            selfConversationIdProvider = selfConversationIdProvider
        )
}
