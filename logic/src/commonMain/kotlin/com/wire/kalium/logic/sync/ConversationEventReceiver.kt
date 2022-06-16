package com.wire.kalium.logic.sync

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.configuration.UserConfigRepository
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
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import io.ktor.utils.io.core.toByteArray

interface ConversationEventReceiver : EventReceiver<Event.Conversation>

// Suppressed as it's an old issue
@Suppress("LongParameterList")
class ConversationEventReceiverImpl(
    private val proteusClient: ProteusClient,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userRepository: UserRepository,
    private val callManagerImpl: Lazy<CallManager>,
    private val editTextHandler: MessageTextEditHandler,
    private val userConfigRepository: UserConfigRepository,
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper()
) : ConversationEventReceiver {

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

    private suspend fun handleProtoContent(
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        protoContent: ProtoContent
    ) {
        when (protoContent.messageContent) {
            is MessageContent.Regular -> {
                val visibility = when (protoContent.messageContent) {
                    is MessageContent.DeleteMessage -> Message.Visibility.HIDDEN
                    is MessageContent.TextEdited -> Message.Visibility.HIDDEN
                    is MessageContent.DeleteForMe -> Message.Visibility.HIDDEN
                    MessageContent.Empty -> Message.Visibility.HIDDEN
                    is MessageContent.Unknown ->
                        if (protoContent.messageContent.hidden) Message.Visibility.HIDDEN
                        else Message.Visibility.VISIBLE
                    is MessageContent.Text -> Message.Visibility.VISIBLE
                    is MessageContent.Calling -> Message.Visibility.VISIBLE
                    is MessageContent.Asset -> Message.Visibility.VISIBLE
                    is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
                }
                val message = Message.Regular(
                    id = protoContent.messageUid,
                    content = protoContent.messageContent,
                    conversationId = conversationId,
                    date = timestampIso,
                    senderUserId = senderUserId,
                    senderClientId = senderClientId,
                    status = Message.Status.SENT,
                    editStatus = Message.EditStatus.NotEdited,
                    visibility = visibility
                )
                processMessage(message)
            }
            is MessageContent.Signaling -> {
                processSignaling(protoContent.messageContent)
            }
        }
    }

    private suspend fun handleNewMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId =
            CryptoSessionId(idMapper.toCryptoQualifiedIDId(event.senderUserId), CryptoClientId(event.senderClientId.value))
        wrapCryptoRequest { proteusClient.decrypt(decodedContentBytes, cryptoSessionId) }.map { PlainMessageBlob(it) }
            .onFailure {
                // TODO(important): Insert a failed message into the database to notify user that encryption is kaputt
                kaliumLogger.e("$TAG - failure on proteus message: ${it.proteusException.stackTraceToString()}")
            }.onSuccess { plainMessageBlob ->
                handleProtoContent(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                )
            }
    }

    private fun updateAssetMessage(persistedMessage: Message.Regular, newMessageRemoteData: AssetContent.RemoteData): Message? =
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

    private suspend fun handleMemberJoin(event: Event.Conversation.MemberJoin) = conversationRepository
        .persistMembers(
            event.members.map { memberMapper.toDaoModel(it) },
            idMapper.toDaoModel(event.conversationId)
        )
        .onSuccess {
            val message = Message.System(
                id = event.id,
                content = MessageContent.MemberChange.Added(members = event.members),
                conversationId = event.conversationId,
                date = event.timestampIso,
                senderUserId = event.addedBy,
                status = Message.Status.SENT,
                visibility = Message.Visibility.VISIBLE
            )
            processMessage(message)
        }
        .onFailure { kaliumLogger.e("$TAG - failure on member join event: $it") }

    private suspend fun handleMemberLeave(event: Event.Conversation.MemberLeave) = conversationRepository
        .deleteMembers(
            event.members.map { idMapper.toDaoModel(it.id) },
            idMapper.toDaoModel(event.conversationId)
        )
        .onSuccess {
            val message = Message.System(
                id = event.id,
                content = MessageContent.MemberChange.Removed(members = event.members),
                conversationId = event.conversationId,
                date = event.timestampIso,
                senderUserId = event.removedBy,
                status = Message.Status.SENT,
                visibility = Message.Visibility.VISIBLE
            )
            processMessage(message)
        }
        .onFailure { kaliumLogger.e("$TAG - failure on member leave event: $it") }

    private suspend fun handleMLSWelcome(event: Event.Conversation.MLSWelcome) {
        mlsConversationRepository.establishMLSGroupFromWelcome(event)
            .onFailure { kaliumLogger.e("$TAG - failure on MLS welcome event: $it") }
    }

    private suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) =
        mlsConversationRepository.messageFromMLSMessage(event)
            .onFailure {
                // TODO(mls): Insert a failed message into the database to notify user that encryption is kaputt
                kaliumLogger.e("$TAG - failure on MLS message: $it")
            }.onSuccess { mlsMessage ->
                val plainMessageBlob = mlsMessage?.let { PlainMessageBlob(it) } ?: return@onSuccess

                handleProtoContent(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                    protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                )
            }

    private fun processSignaling(signaling: MessageContent.Signaling) {
        when (signaling) {
            MessageContent.Ignored -> {
                kaliumLogger.i(message = "Ignored Signaling Message received: $signaling")
            }
        }
    }

    // TODO(qol): split this function so it's easier to maintain
    @Suppress("ComplexMethod", "LongMethod")
    private suspend fun processMessage(message: Message) {
        kaliumLogger.i(message = "Message received: $message")

        val isMyMessage = userRepository.getSelfUserId() == message.senderUserId
        when (message) {
            is Message.Regular -> when (message.content) {
                is MessageContent.Text -> messageRepository.persistMessage(message)
                is MessageContent.Asset -> {
                    userConfigRepository.isFileSharingEnabled().onSuccess {
                        if (it) {
                            messageRepository.getMessageById(message.conversationId, message.id)
                                .onFailure {
                                    // No asset message was received previously, so just persist the preview asset message
                                    messageRepository.persistMessage(message)
                                }
                                .onSuccess { persistedMessage ->
                                    // Check the second asset message is from the same original sender
                                    if (isSenderVerified(persistedMessage.id, persistedMessage.conversationId, message.senderUserId)
                                        && persistedMessage is Message.Regular && persistedMessage.content is MessageContent.Asset
                                    ) {
                                        // The asset message received contains the asset decryption keys,
                                        // so update the preview message persisted previously
                                        updateAssetMessage(persistedMessage, message.content.value.remoteData)?.let {
                                            messageRepository.persistMessage(it)
                                        }
                                    }
                                }
                        } else {
                            val newMessage = message.copy(content = MessageContent.RestrictedAsset(message.content.value.mimeType))
                            messageRepository.persistMessage(newMessage)
                        }
                    }
                }

                is MessageContent.DeleteMessage ->
                    if (isSenderVerified(message.content.messageId, message.conversationId, message.senderUserId))
                        messageRepository.markMessageAsDeleted(
                            messageUuid = message.content.messageId,
                            conversationId = message.conversationId
                        )
                    else kaliumLogger.i(message = "Delete message sender is not verified: $message")
                is MessageContent.DeleteForMe -> {
                    /*The conversationId comes with the hidden message[message.content] only carries the conversaionId VALUE,
                    *  we need to get the DOMAIN from the self conversationId[here is the message.conversationId]*/
                    val conversationId =
                        if (message.content.qualifiedConversationId != null)
                            idMapper.fromProtoModel(message.content.qualifiedConversationId)
                        else ConversationId(
                            message.content.conversationId,
                            message.conversationId.domain
                        )
                    if (message.conversationId == conversationRepository.getSelfConversationId())
                        messageRepository.deleteMessage(
                            messageUuid = message.content.messageId,
                            conversationId = conversationId
                        )
                    else kaliumLogger.i(message = "Delete message sender is not verified: $message")
                }
                is MessageContent.Calling -> {
                    kaliumLogger.d("$TAG - MessageContent.Calling")
                    callManagerImpl.value.onCallingMessageReceived(
                        message = message,
                        content = message.content
                    )
                }
                is MessageContent.TextEdited -> editTextHandler.handle(message, message.content)
                is MessageContent.Unknown -> {
                    kaliumLogger.i(message = "Unknown Message received: $message")
                    messageRepository.persistMessage(message)
                }
                MessageContent.Empty -> TODO()
            }
            is Message.System -> when (message.content) {
                is MessageContent.MemberChange -> {
                    kaliumLogger.i(message = "System MemberChange Message received: $message")
                    messageRepository.persistMessage(message)
                }
            }
        }

        if (isMyMessage) conversationRepository.updateConversationNotificationDate(message.conversationId, message.date)
        conversationRepository.updateConversationModifiedDate(message.conversationId, message.date)
    }

    private companion object {
        const val TAG = "ConversationEventReceiver"
    }

}
