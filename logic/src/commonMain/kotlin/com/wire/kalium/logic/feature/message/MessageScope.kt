package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.asset.GetPrivateAssetUseCase
import com.wire.kalium.logic.feature.asset.GetPrivateAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.SendImageMessageUseCase
import com.wire.kalium.logic.feature.asset.SendImageMessageUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

class MessageScope(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient,
    private val preKeyRepository: PreKeyRepository,
    private val userRepository: UserRepository,
    private val assetRepository: AssetRepository,
    private val syncManager: SyncManager
) {

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandler(userRepository, clientRepository)

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(proteusClient, preKeyRepository)

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl()

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(proteusClient, protoContentMapper)

    private val messageSender: MessageSender
        get() = MessageSenderImpl(
            messageRepository, conversationRepository, syncManager, messageSendFailureHandler, sessionEstablisher, messageEnvelopeCreator
        )

    val sendTextMessage: SendTextMessageUseCase
        get() = SendTextMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            syncManager,
            messageSender
        )

    val sendImageMessage: SendImageMessageUseCase
        get() = SendImageMessageUseCaseImpl(
            messageRepository,
            clientRepository,
            assetRepository,
            userRepository,
            messageSender
        )

    val getImageAssetMessage: GetPrivateAssetUseCase
        get() = GetPrivateAssetUseCaseImpl(
            assetRepository,
            messageRepository
        )

    val getRecentMessages: GetRecentMessagesUseCase get() = GetRecentMessagesUseCase(messageRepository)

    val deleteMessage: DeleteMessageUseCase
        get() = DeleteMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            syncManager,
            messageSender,
            conversationRepository
        )

}
