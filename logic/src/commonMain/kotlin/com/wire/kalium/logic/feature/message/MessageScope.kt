package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.SendAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.SendAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.SendImageMessageUseCase
import com.wire.kalium.logic.feature.asset.SendImageMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.TimeParser

class MessageScope(
    internal val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient,
    private val mlsClientProvider: MLSClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val selfUserRepository: SelfUserRepository,
    private val assetRepository: AssetRepository,
    private val syncManager: SyncManager,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val timeParser: TimeParser,
) {

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(selfUserRepository, clientRepository)

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(proteusClient, preKeyRepository)

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl()

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(proteusClient, protoContentMapper)

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(mlsClientProvider, protoContentMapper)

    private val idMapper: IdMapper
        get() = IdMapperImpl()

    internal val messageSender: MessageSender
        get() = MessageSenderImpl(
            messageRepository,
            conversationRepository,
            syncManager,
            messageSendFailureHandler,
            sessionEstablisher,
            messageEnvelopeCreator,
            mlsMessageCreator,
            messageSendingScheduler,
            timeParser
        )

    val sendTextMessage: SendTextMessageUseCase
        get() = SendTextMessageUseCase(
            messageRepository,
            selfUserRepository,
            clientRepository,
            syncManager,
            messageSender
        )

    val sendImageMessage: SendImageMessageUseCase
        get() = SendImageMessageUseCaseImpl(
            messageRepository,
            clientRepository,
            assetRepository,
            selfUserRepository,
            messageSender
        )

    val sendAssetMessage: SendAssetMessageUseCase
        get() = SendAssetMessageUseCaseImpl(
            messageRepository,
            clientRepository,
            assetRepository,
            selfUserRepository,
            messageSender
        )

    val getAssetMessage: GetMessageAssetUseCase
        get() = GetMessageAssetUseCaseImpl(
            assetRepository,
            messageRepository
        )

    val getRecentMessages: GetRecentMessagesUseCase get() = GetRecentMessagesUseCase(messageRepository)

    val deleteMessage: DeleteMessageUseCase
        get() = DeleteMessageUseCase(
            messageRepository,
            selfUserRepository,
            clientRepository,
            syncManager,
            messageSender,
            idMapper
        )

    val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase get() = MarkMessagesAsNotifiedUseCaseImpl(conversationRepository)
    val updateAssetMessageDownloadStatus: UpdateAssetMessageDownloadStatusUseCase
        get() = UpdateAssetMessageDownloadStatusUseCaseImpl(
            messageRepository
        )

    val getNotifications: GetNotificationsUseCase
        get() = GetNotificationsUseCaseImpl(
            messageRepository,
            selfUserRepository,
            conversationRepository
        )
}
