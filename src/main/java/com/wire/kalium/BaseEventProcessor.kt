package com.wire.kalium

import com.google.protobuf.InvalidProtocolBufferException
import com.waz.model.Messages.GenericMessage
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.backend.GenericMessageProcessor
import com.wire.kalium.backend.models.Conversation
import com.wire.kalium.backend.models.Data
import com.wire.kalium.backend.models.Payload
import com.wire.kalium.backend.models.SystemMessage
import com.wire.kalium.models.MessageBase
import com.wire.kalium.tools.Logger
import java.util.Base64
import java.util.UUID

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
            val systemMessage = getSystemMessage(eventId, payload)
            handler.onNewConversation(client, systemMessage)
        }
    }

    private fun handleConversationRenameEvent(eventId: UUID, payload: Payload, client: WireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        handler.onConversationRename(client, systemMessage)
    }

    private fun handleConversationCreatedEvent(eventId: UUID, payload: Payload, botId: UUID, client: WireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        handler.onNewConversation(client, systemMessage)
    }

    private fun handleConversationDeleteEvent(eventId: UUID, payload: Payload, botId: UUID) {
        val systemMessage = getSystemMessage(eventId, payload)

        // Cleanup
        handler.onBotRemoved(botId, systemMessage)
    }

    private fun handleMemberLeaveEvent(eventId: UUID, payload: Payload, data: Data, botId: UUID, client: WireClient) {
        val participants = data.user_ids
        val systemMessage = getSystemMessage(eventId, payload)
            .copy(userIds = participants)

        // Check if this bot got removed from the conversation
        if (participants.any { it == botId }) {
            handler.onBotRemoved(botId, systemMessage)
            return
        }
        if (participants.isNotEmpty()) {
            handler.onMemberLeave(client, systemMessage)
        }
    }

    private fun handleMemberJoinEvent(data: Data, botId: UUID?, eventId: UUID, payload: Payload, client: WireClient) {
        val participants = data.user_ids
        val originalSystemMessage = getSystemMessage(eventId, payload)

        // Check if this bot got added to the conversation
        if (participants.any { it == botId }) {
            val systemMessage = originalSystemMessage.copy(
                conversation = client.getConversation(),
                type = "conversation.create" // hack the type
            )
            handler.onNewConversation(client, systemMessage)
            return
        }

        // Check if we still have some prekeys available. Upload new prekeys if needed
        handler.validatePreKeys(client, participants.size)
        val systemMessage = originalSystemMessage.copy(userIds = data.user_ids)
        handler.onMemberJoin(client, systemMessage)
    }

    private fun handleMessageAddEvent(payload: Payload, client: WireClient, eventId: UUID, data: Data) {
        val from = payload.from
        val processor = GenericMessageProcessor(client, handler)
        val genericMessage = decrypt(client, payload)
        val messageId = UUID.fromString(genericMessage.messageId)
        val msgBase = MessageBase(eventId, messageId, payload.conversation, data.sender, from, payload.time)
        processor.process(msgBase, genericMessage)
        handler.onEvent(client, from, genericMessage)
    }

    private fun getSystemMessage(eventId: UUID, payload: Payload): SystemMessage {
        val conversation = Conversation(
            payload.conversation, payload.data.name, payload.data.creator,
            payload.data.members.allMembers()
        )
        return SystemMessage(
            eventId, payload.type, payload.time, payload.from,
            conversation, conversation.id, conversation.members.map { UUID.fromString(it.userId) }
        )
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
