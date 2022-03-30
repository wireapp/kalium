package com.wire.kalium.logic.sync

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.PlainUserId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.wrapCryptoRequest
import io.ktor.utils.io.core.toByteArray

class ConversationEventReceiver(
    private val proteusClient: ProteusClient,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val protoContentMapper: ProtoContentMapper,
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : EventReceiver<Event.Conversation> {
    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.NewMessage -> handleNewMessage(event)
            is Event.Conversation.MemberJoin -> handleMemberJoin(event)
            is Event.Conversation.MemberLeave -> handleMemberLeave(event)
        }
    }

    private suspend fun handleNewMessage(event: Event.Conversation.NewMessage) {
        val decodedContentBytes = Base64.decodeFromBase64(event.content.toByteArray())

        //TODO Use domain when creating CryptoSession too
        val cryptoSessionId = CryptoSessionId(PlainUserId(event.senderUserId.value), CryptoClientId(event.senderClientId.value))
        suspending {
            wrapCryptoRequest { proteusClient.decrypt(decodedContentBytes, cryptoSessionId) }.map { PlainMessageBlob(it) }

                .onFailure {
                    //TODO: Insert a failed message into the database to notify user that encryption is kaputt
                    it.proteusException.printStackTrace()
                }.onSuccess { plainMessageBlob ->
                    val protoContent = protoContentMapper.decodeFromProtobuf(plainMessageBlob)
                    val message = Message(
                        protoContent.messageUid,
                        protoContent.messageContent,
                        event.conversationId,
                        event.time,
                        event.senderUserId,
                        event.senderClientId,
                        Message.Status.SENT
                    )
                    //TODO Multiplatform logging
                    kaliumLogger.i(message = "Message received: $message")
                    when (message.content) {
                        is MessageContent.Text -> messageRepository.persistMessage(message)
                        is MessageContent.DeleteMessage -> messageRepository.softDeleteMessage(messageUuid = message.content.messageId, message.conversationId)
                        is MessageContent.DeleteForMe -> messageRepository.hideMessage(messageUuid = message.content.messageId, message.content.conversationId)
                        is MessageContent.Unknown -> kaliumLogger.i(message = "Unknown Message received: $message")
                    }
                }
        }
    }

    //TODO: insert a message to show a user added to the conversation
    private suspend fun handleMemberJoin(event: Event.Conversation.MemberJoin) = conversationRepository
        .persistMembers(
            memberMapper.fromEventToDaoModel(event.members.users),
            idMapper.toDaoModel(event.conversationId)
        )

    //TODO: insert a message to show a user deleted to the conversation
    private suspend fun handleMemberLeave(event: Event.Conversation.MemberLeave) =
        event.members.qualifiedUserIds.forEach { userId ->
            conversationRepository.deleteMember(
                idMapper.toDaoModel(event.conversationId), idMapper.fromApiToDao(userId)
            )
        }

}
