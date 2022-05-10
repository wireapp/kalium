package com.wire.kalium.logic.sync

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import io.ktor.utils.io.core.toByteArray

class ConversationEventReceiver(
    private val proteusClient: ProteusClient,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userRepository: UserRepository,
    private val protoContentMapper: ProtoContentMapper,
    private val callManagerImpl: CallManager,
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : EventReceiver<Event.Conversation> {

    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.NewMessage -> handleNewMessage(event)
            is Event.Conversation.NewConversation -> handleNewConversation(event)
            is Event.Conversation.MemberJoin -> handleMemberJoin(event)
            is Event.Conversation.MemberLeave -> handleMemberLeave(event)
            is Event.Conversation.MLSWelcome -> handleMLSWelcome(event)
            is Event.Conversation.NewMLSMessage -> handleNewMLSMessage(event)
        }
    }

    private suspend fun handleNewMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId =
            CryptoSessionId(idMapper.toCryptoQualifiedIDId(event.senderUserId), CryptoClientId(event.senderClientId.value))
        suspending {
            wrapCryptoRequest { proteusClient.decrypt(decodedContentBytes, cryptoSessionId) }.map { PlainMessageBlob(it) }
                .onFailure {
                    // TODO: Insert a failed message into the database to notify user that encryption is kaputt
                    kaliumLogger.e("$TAG - failure on proteus message: ${it.proteusException.stackTraceToString()}")
                }.onSuccess { plainMessageBlob ->
                    val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                    val message = Message(
                        id = protoContent.messageUid,
                        content = protoContent.messageContent,
                        conversationId = event.conversationId,
                        date = event.time,
                        senderUserId = event.senderUserId,
                        senderClientId = event.senderClientId,
                        status = Message.Status.SENT
                    )

                    processMessage(message)
                }
        }
    }

    private fun updateAssetMessage(persistedMessage: Message, newMessageRemoteData: AssetContent.RemoteData): Message? =
        // The message was previously received with just metadata info, so let's update it with the raw data info
        if (persistedMessage.content is MessageContent.Asset) {
            persistedMessage.copy(
                content = persistedMessage.content.copy(
                    value = persistedMessage.content.value.copy(
                        remoteData = newMessageRemoteData
                    )
                )
            )
        } else null

    private suspend fun isSenderVerified(messageId: String, conversationId: ConversationId, senderUserId: UserId): Boolean {
        var verified = false
        messageRepository.getMessageById(
            messageUuid = messageId,
            conversationId = conversationId
        ).onSuccess {
            verified = senderUserId == it.senderUserId
        }
        return verified
    }

    private suspend fun handleNewConversation(event: Event.Conversation.NewConversation) =
        conversationRepository.insertConversationFromEvent(event)
            .onFailure { kaliumLogger.e("$TAG - failure on new conversation event: $it") }

    //TODO: insert a message to show a user added to the conversation
    private suspend fun handleMemberJoin(event: Event.Conversation.MemberJoin) = conversationRepository
        .persistMembers(
            memberMapper.fromEventToDaoModel(event.members.users),
            idMapper.toDaoModel(event.conversationId)
        ).onFailure { kaliumLogger.e("$TAG - failure on member join event: $it") }

    //TODO: insert a message to show a user deleted to the conversation
    private suspend fun handleMemberLeave(event: Event.Conversation.MemberLeave) =
        event.members.qualifiedUserIds.forEach { userId ->
            conversationRepository.deleteMember(
                idMapper.toDaoModel(event.conversationId), idMapper.fromApiToDao(userId)
            ).onFailure { kaliumLogger.e("$TAG - failure on member leave event: $it") }
        }

    private suspend fun handleMLSWelcome(event: Event.Conversation.MLSWelcome) {
        mlsConversationRepository.establishMLSGroupFromWelcome(event)
            .onFailure { kaliumLogger.e("$TAG - failure on MLS welcome event: $it") }
    }

    private suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) = suspending {
        mlsConversationRepository.messageFromMLSMessage(event)
            .onFailure {
                // TODO: Insert a failed message into the database to notify user that encryption is kaputt
                kaliumLogger.e("$TAG - failure on MLS message: $it")
            }
            .onSuccess { message ->
                val plainMessageBlob = message?.let { PlainMessageBlob(it) } ?: return@onSuccess
                val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                val message = Message(
                    id = protoContent.messageUid,
                    content = protoContent.messageContent,
                    conversationId = event.conversationId,
                    date = event.time,
                    senderUserId = event.senderUserId,
                    senderClientId = ClientId(""), // TODO client ID not available for MLS messages
                    status = Message.Status.SENT
                )

                processMessage(message)
            }
    }

    private suspend fun processMessage(message: Message) = suspending {
        kaliumLogger.i(message = "Message received: $message")

        val isMyMessage = userRepository.getSelfUserId() == message.senderUserId
        when (message.content) {
            is MessageContent.Text -> messageRepository.persistMessage(message)
            is MessageContent.Asset -> {
                messageRepository.getMessageById(message.conversationId, message.id)
                    .onFailure {
                        // No asset message was received previously, so just persist the preview asset message
                        messageRepository.persistMessage(message)
                    }
                    .onSuccess { persistedMessage ->
                        // Check the second asset message is from the same original sender
                        if (isSenderVerified(persistedMessage.id, persistedMessage.conversationId, message.senderUserId)) {
                            // The asset message received contains the asset decryption keys, so update the preview message persisted previously
                            updateAssetMessage(persistedMessage, message.content.value.remoteData)?.let {
                                messageRepository.persistMessage(it)
                            }
                        }
                    }
            }
            is MessageContent.DeleteMessage ->
                if (isSenderVerified(message.content.messageId, message.conversationId, message.senderUserId))
                    messageRepository.markMessageAsDeleted(messageUuid = message.content.messageId, conversationId = message.conversationId)
                else kaliumLogger.i(message = "Delete message sender is not verified: $message")
            is MessageContent.DeleteForMe ->
                if (message.conversationId == conversationRepository.getSelfConversationId())
                //todo: consider to check with conversation id
                    messageRepository.deleteMessage(messageUuid = message.content.messageId)
                else kaliumLogger.i(message = "Delete message sender is not verified: $message")
            is MessageContent.Calling -> {
                kaliumLogger.d("$TAG - MessageContent.Calling")
                callManagerImpl.onCallingMessageReceived(
                    message = message,
                    content = message.content
                )
            }
            is MessageContent.Unknown -> kaliumLogger.i(message = "Unknown Message received: $message")
        }

        if (isMyMessage) conversationRepository.updateConversationNotificationDate(message.conversationId, message.date)
        conversationRepository.updateConversationModifiedDate(message.conversationId, message.date)
    }

    private companion object {
        const val TAG = "ConversationEventReceiver"
    }

}
