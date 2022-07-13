package com.wire.kalium.logic.sync

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.handler.MessageTextEditHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock

interface ConversationEventReceiver : EventReceiver<Event.Conversation>

// Suppressed as it's an old issue
//TODO(refactor): Create a `MessageEventReceiver` to offload some logic from here
@Suppress("LongParameterList", "TooManyFunctions")
class ConversationEventReceiverImpl(
    private val proteusClient: ProteusClient,
    private val persistMessage: PersistMessageUseCase,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userRepository: UserRepository,
    private val callManagerImpl: Lazy<CallManager>,
    private val editTextHandler: MessageTextEditHandler,
    private val userConfigRepository: UserConfigRepository,
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

    private suspend fun handleContent(
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable
    ) {
        when (content.messageContent) {
            is MessageContent.Regular -> {
                val visibility = when (content.messageContent) {
                    is MessageContent.DeleteMessage -> Message.Visibility.HIDDEN
                    is MessageContent.TextEdited -> Message.Visibility.HIDDEN
                    is MessageContent.DeleteForMe -> Message.Visibility.HIDDEN
                    MessageContent.Empty -> Message.Visibility.HIDDEN
                    is MessageContent.Unknown ->
                        if (content.messageContent.hidden) Message.Visibility.HIDDEN
                        else Message.Visibility.VISIBLE

                    is MessageContent.Text -> Message.Visibility.VISIBLE
                    is MessageContent.Calling -> Message.Visibility.VISIBLE
                    is MessageContent.Asset -> Message.Visibility.VISIBLE
                    is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
                }
                val message = Message.Regular(
                    id = content.messageUid,
                    content = content.messageContent,
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
                processSignaling(content.messageContent)
            }
        }
    }

    private suspend fun handleNewMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())
        val cryptoSessionId =
            CryptoSessionId(idMapper.toCryptoQualifiedIDId(event.senderUserId), CryptoClientId(event.senderClientId.value))
        wrapCryptoRequest { proteusClient.decrypt(decodedContentBytes, cryptoSessionId) }.map { PlainMessageBlob(it) }
            .flatMap { plainMessageBlob -> getReadableMessageContent(plainMessageBlob, event) }
            .onFailure {
                // TODO(important): Insert a failed message into the database to notify user that encryption is kaputt
                when (it) {
                    is CoreFailure.Unknown -> kaliumLogger.e("$TAG - UnknownFailure when processing message: $it", it.rootCause)
                    is ProteusFailure -> kaliumLogger.e("$TAG - ProteusFailure when processing message: $it", it.proteusException)
                    else -> kaliumLogger.e("Failure when processing message: $it")
                }
            }.onSuccess { readableContent ->
                handleContent(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    content = readableContent
                )
            }
    }

    private fun getReadableMessageContent(
        plainMessageBlob: PlainMessageBlob,
        event: Event.Conversation.NewMessage
    ) = when (val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)) {
        is ProtoContent.Readable -> Either.Right(protoContent)
        is ProtoContent.ExternalMessageInstructions -> event.encryptedExternalContent?.let {
            kaliumLogger.d("Solving external content '$protoContent', EncryptedData='$it'")
            solveExternalContentForProteusMessage(protoContent, event.encryptedExternalContent)
        } ?: run {
            val rootCause = IllegalArgumentException("Null external content when processing external message instructions.")
            Either.Left(CoreFailure.Unknown(rootCause))
        }
    }

    private fun solveExternalContentForProteusMessage(
        externalInstructions: ProtoContent.ExternalMessageInstructions,
        externalData: EncryptedData
    ): Either<CoreFailure, ProtoContent.Readable> = wrapCryptoRequest {
        val decryptedExternalMessage = decryptDataWithAES256(externalData, AES256Key(externalInstructions.otrKey)).data
        kaliumLogger.d("ExternalMessage - Decrypted external message content: '$decryptedExternalMessage'")
        PlainMessageBlob(decryptedExternalMessage)
    }.map(protoContentMapper::decodeFromProtobuf).flatMap { decodedProtobuf ->
        if (decodedProtobuf !is ProtoContent.Readable) {
            val rootCause = IllegalArgumentException("матрёшка! External message can't contain another external message inside!")
            Either.Left(CoreFailure.Unknown(rootCause))
        } else {
            Either.Right(decodedProtobuf)
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
        conversationRepository.insertConversationFromEvent(event).flatMap {
            conversationRepository.updateConversationModifiedDate(event.conversationId, Clock.System.now().toString())
        }.onFailure { kaliumLogger.e("$TAG - failure on new conversation event: $it") }

    private suspend fun handleMemberJoin(event: Event.Conversation.MemberJoin) =
        // Attempt to fetch conversation details if needed, as this might be an unknown conversation
        conversationRepository.fetchConversationIfUnknown(event.conversationId)
            .run {
                onSuccess { kaliumLogger.v("Succeeded fetching conversation details on MemberJoin Event: $event") }
                onFailure { kaliumLogger.w("Failure fetching conversation details on MemberJoin Event: $event") }
                // Even if unable to fetch conversation details, at least attempt adding the member
                conversationRepository.persistMembers(event.members, event.conversationId)
            }.onSuccess {
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.MemberChange.Added(members = event.members.map { it.id }),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.addedBy,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                processMessage(message) //TODO(exception-handling): processMessage exceptions are not caught
            }.onFailure { kaliumLogger.e("$TAG - failure on member join event: $it") }

    private suspend fun handleMemberLeave(event: Event.Conversation.MemberLeave) = conversationRepository
        .deleteMembers(
            event.removedList.map { idMapper.toDaoModel(it) },
            idMapper.toDaoModel(event.conversationId)
        )
        .flatMap {
            // fetch required unknown users that haven't been persisted during slow sync, e.g. from another team
            // and keep them to properly show this member-leave message
            userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }
        .onSuccess {
            val message = Message.System(
                id = event.id,
                content = MessageContent.MemberChange.Removed(members = event.removedList),
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
                val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                if (protoContent !is ProtoContent.Readable) {
                    throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
                }
                handleContent(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                    content = protoContent
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

        when (message) {
            is Message.Regular -> when (val content = message.content) {
                is MessageContent.Text -> persistMessage(message)
                is MessageContent.Asset -> {

                    userConfigRepository.isFileSharingEnabled().onSuccess {
                        if (it.isFileSharingEnabled != null && it.isFileSharingEnabled) {
                            messageRepository.getMessageById(message.conversationId, message.id)
                                .onFailure {
                                    // No asset message was received previously, so just persist the preview asset message
                                    persistMessage(message)
                                }
                                .onSuccess { persistedMessage ->
                                    // Check the second asset message is from the same original sender
                                    if (
                                        isSenderVerified(persistedMessage.id, persistedMessage.conversationId, message.senderUserId)
                                        && persistedMessage is Message.Regular && persistedMessage.content is MessageContent.Asset
                                    ) {
                                        // The asset message received contains the asset decryption keys,
                                        // so update the preview message persisted previously
                                        updateAssetMessage(persistedMessage, content.value.remoteData)?.let {
                                            persistMessage(it)
                                        }
                                    }
                                }

                        } else {
                            val newMessage = message.copy(content = MessageContent.RestrictedAsset(content.value.mimeType))
                            persistMessage(newMessage)

                        }
                    }
                }

                is MessageContent.DeleteMessage ->
                    if (isSenderVerified(content.messageId, message.conversationId, message.senderUserId))
                        messageRepository.markMessageAsDeleted(
                            messageUuid = content.messageId,
                            conversationId = message.conversationId
                        )
                    else kaliumLogger.i(message = "Delete message sender is not verified: $message")

                is MessageContent.DeleteForMe -> {
                    /*The conversationId comes with the hidden message[content] only carries the conversationId VALUE,
                    *  we need to get the DOMAIN from the self conversationId[here is the message.conversationId]*/
                    val conversationId =
                        if (content.qualifiedConversationId != null)
                            idMapper.fromProtoModel(content.qualifiedConversationId)
                        else ConversationId(
                            content.conversationId,
                            message.conversationId.domain
                        )
                    if (message.conversationId == conversationRepository.getSelfConversationId())
                        messageRepository.deleteMessage(
                            messageUuid = content.messageId,
                            conversationId = conversationId
                        )
                    else kaliumLogger.i(message = "Delete message sender is not verified: $message")
                }

                is MessageContent.Calling -> {
                    kaliumLogger.d("$TAG - MessageContent.Calling")
                    callManagerImpl.value.onCallingMessageReceived(
                        message = message,
                        content = content
                    )
                }

                is MessageContent.TextEdited -> editTextHandler.handle(message, content)
                is MessageContent.Unknown -> {
                    kaliumLogger.i(message = "Unknown Message received: $message")
                    persistMessage(message)
                }

                MessageContent.Empty -> TODO()
            }

            is Message.System -> when (message.content) {
                is MessageContent.MemberChange -> {
                    kaliumLogger.i(message = "System MemberChange Message received: $message")
                    persistMessage(message)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ConversationEventReceiver"
    }

}
