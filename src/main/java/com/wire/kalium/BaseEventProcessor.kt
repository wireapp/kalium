package com.wire.kalium

import com.google.protobuf.InvalidProtocolBufferException
import com.waz.model.Messages.GenericMessage
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.backend.GenericMessageProcessor
import com.wire.kalium.backend.models.Conversation
import com.wire.kalium.backend.models.Member
import com.wire.kalium.backend.models.Payload
import com.wire.kalium.backend.models.SystemMessage
import com.wire.kalium.models.MessageBase
import com.wire.kalium.tools.Logger
import java.util.*

abstract class BaseEventProcessor(private val handler: MessageHandler) : EventProcessor {

    @Throws(Exception::class)
    override fun processEvent(eventId: UUID, payload: Payload, client: WireClient) {
        val data = payload.data
        val botId = client.getId()
        Logger.debug("New event of type: '${payload.type}'; Content: $payload")
        when (payload.type) {
            // TODO: Replace with enum!
            "conversation.otr-message-add" -> {
                handleMessageAddEvent(payload, client, eventId, data)
            }
            "conversation.member-join" -> {
                handleMemberJoinEvent(data, botId, eventId, payload, client)
            }
            "conversation.member-leave" -> {
                handleMemberLeaveEvent(eventId, payload, data, botId, client)
            }
            "conversation.delete" -> {
                handleConversationDeleteEvent(eventId, payload, botId)
            }
            "conversation.create" -> {
                handleConversationCreatedEvent(eventId, payload, botId, client)
            }
            "conversation.rename" -> {
                handleConversationRenameEvent(eventId, payload, client)
            }
            "user.connection" -> {
                handleConnectionUpdateEvent(client, eventId, payload)
            }
            else -> Logger.debug("Unknown event: %s", payload.type)
        }
    }

    private fun handleConnectionUpdateEvent(client: WireClient, eventId: UUID, payload: Payload) {
        val connection = payload.connection
        val accepted = handler.onConnectRequest(client, connection.from, connection.to, connection.status)
        if (accepted) {
            val conversation = Conversation()
            conversation.id = connection.conversation
            val systemMessage = SystemMessage()
            systemMessage.id = eventId
            systemMessage.from = connection.from
            systemMessage.type = payload.type
            systemMessage.conversation = conversation
            handler.onNewConversation(client, systemMessage)
        }
    }

    private fun handleConversationRenameEvent(eventId: UUID, payload: Payload, client: WireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        handler.onConversationRename(client, systemMessage)
    }

    private fun handleConversationCreatedEvent(eventId: UUID, payload: Payload, botId: UUID, client: WireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        val self = Member()
        self.id = botId
        systemMessage.conversation.members.add(self)
        handler.onNewConversation(client, systemMessage)
    }

    private fun handleConversationDeleteEvent(eventId: UUID, payload: Payload, botId: UUID) {
        val systemMessage = getSystemMessage(eventId, payload)

        // Cleanup
        handler.onBotRemoved(botId, systemMessage)
    }

    private fun handleMemberLeaveEvent(eventId: UUID, payload: Payload, data: Payload.Data, botId: UUID, client: WireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        systemMessage.users = data.userIds

        // Check if this bot got removed from the conversation
        val participants = data.userIds
        if (participants.remove(botId)) {
            handler.onBotRemoved(botId, systemMessage)
            return
        }
        if (participants.isNotEmpty()) {
            handler.onMemberLeave(client, systemMessage)
        }
    }

    private fun handleMemberJoinEvent(data: Payload.Data, botId: UUID?, eventId: UUID, payload: Payload, client: WireClient) {
        // Check if this bot got added to the conversation
        val participants = data.userIds
        if (participants.remove(botId)) {
            val systemMessage = getSystemMessage(eventId, payload)
            systemMessage.conversation = client.getConversation()
            systemMessage.type = "conversation.create" //hack the type
            handler.onNewConversation(client, systemMessage)
            return
        }

        // Check if we still have some prekeys available. Upload new prekeys if needed
        handler.validatePreKeys(client, participants.size)
        val systemMessage = getSystemMessage(eventId, payload)
        systemMessage.users = data.userIds
        handler.onMemberJoin(client, systemMessage)
    }

    private fun handleMessageAddEvent(payload: Payload, client: WireClient, eventId: UUID, data: Payload.Data) {
        val from = payload.from
        val processor = GenericMessageProcessor(client, handler)
        val genericMessage = decrypt(client, payload)
        val messageId = UUID.fromString(genericMessage.messageId)
        val msgBase = MessageBase(eventId, messageId, payload.convId, data.sender, from, payload.time)
        processor.process(msgBase, genericMessage)
        handler.onEvent(client, from, genericMessage)
    }

    private fun getSystemMessage(eventId: UUID, payload: Payload): SystemMessage = SystemMessage().apply {
        id = eventId
        from = payload.from
        time = payload.time
        type = payload.type
        convId = payload.convId
        conversation = Conversation()
        conversation.id = payload.convId
        conversation.creator = payload.data.creator
        conversation.name = payload.data.name
        payload.data.members?.let {
            conversation.members = it.others
        }
    }

    @Throws(CryptoException::class, InvalidProtocolBufferException::class)
    private fun decrypt(client: WireClient, payload: Payload): GenericMessage {
        val from = payload.from
        val sender = payload.data.sender
        val cipher = payload.data.text
        val encoded = client.decrypt(from, sender, cipher)
        val decoded = Base64.getDecoder().decode(encoded)
        return GenericMessage.parseFrom(decoded)
    }
}
