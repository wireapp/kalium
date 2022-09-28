package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.SendAssetMessageUseCase
import com.wire.kalium.logic.feature.asset.SendAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageDownloadStatusUseCaseImpl
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageUploadStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageUploadStatusUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList")
class MessageScope internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val userId: QualifiedID,
    private val currentClientIdProvider: CurrentClientIdProvider,
    internal val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient,
    private val mlsClientProvider: MLSClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val userRepository: UserRepository,
    private val assetRepository: AssetRepository,
    private val syncManager: SyncManager,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val timeParser: TimeParser,
    private val scope: CoroutineScope,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(userRepository, clientRepository)

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
            timeParser,
            scope
        )

    val persistMessage: PersistMessageUseCase
        get() = PersistMessageUseCaseImpl(messageRepository, userId)

    val sendTextMessage: SendTextMessageUseCase
        get() = SendTextMessageUseCase(
            persistMessage,
            userId,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender
        )

    val getMessageById: GetMessageByIdUseCase
        get() = GetMessageByIdUseCase(messageRepository)

    val sendAssetMessage: SendAssetMessageUseCase
        get() = SendAssetMessageUseCaseImpl(
            persistMessage,
            updateAssetMessageUploadStatus,
            clientRepository,
            assetRepository,
            userRepository,
            slowSyncRepository,
            messageSender
        )

    val getAssetMessage: GetMessageAssetUseCase
        get() = GetMessageAssetUseCaseImpl(
            assetRepository,
            messageRepository,
            updateAssetMessageDownloadStatus
        )

    val getRecentMessages: GetRecentMessagesUseCase
        get() = GetRecentMessagesUseCase(
            messageRepository,
            slowSyncRepository
        )

    val deleteMessage: DeleteMessageUseCase
        get() = DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            assetRepository,
            slowSyncRepository,
            messageSender
        )

    val sendKnock: SendKnockUseCase
        get() = SendKnockUseCase(
            persistMessage,
            userRepository,
            clientRepository,
            slowSyncRepository,
            messageSender
        )

    val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase get() = MarkMessagesAsNotifiedUseCaseImpl(conversationRepository)

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
            connectionRepository,
            messageRepository,
            userRepository,
            conversationRepository,
            timeParser,
            EphemeralNotificationsManager
        )
}
