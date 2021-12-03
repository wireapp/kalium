package com.wire.kalium

import com.google.protobuf.InvalidProtocolBufferException
import com.waz.model.Messages.GenericMessage
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.models.backend.Access
import com.wire.kalium.models.backend.Conversation
import com.wire.kalium.models.backend.Data
import com.wire.kalium.models.backend.Payload
import com.wire.kalium.models.inbound.MessageBase
import com.wire.kalium.models.system.SystemMessage
import com.wire.kalium.tools.Logger
import java.util.Base64
import java.util.UUID
import javax.ws.rs.client.Client

class EventProcessor(private val handler: MessageHandler) : IEventProcessor {
    private var client: Client? = null
    private var crypto: Crypto? = null
    private var access: Access? = null

    @Throws(Exception::class)
    override fun processEvent(eventId: UUID, payload: Payload, wireClient: IWireClient) {
        val userId = wireClient.getUserId()
        Logger.debug("New event of type: '${payload.type}'; Content: $payload")
        when (payload.type) {
            // TODO: Replace with enum!
            "conversation.otr-message-add" -> {
                handleMessageAddEvent(payload, wireClient, eventId, payload.data!!)
            }
            "conversation.member-join" -> {
                handleMemberJoinEvent(payload.data!!, userId, eventId, payload, wireClient)
            }
            "conversation.member-leave" -> {
                handleMemberLeaveEvent(eventId, payload, payload.data!!, userId, wireClient)
            }
            "conversation.delete" -> {
                handleConversationDeleteEvent(eventId, payload, userId)
            }
            "conversation.create" -> {
                handleConversationCreatedEvent(eventId, payload, userId, wireClient)
            }
            "conversation.rename" -> {
                handleConversationRenameEvent(eventId, payload, wireClient)
            }
            "user.connection" -> {
                handleConnectionUpdateEvent(wireClient, eventId, payload)
            }
            "team.member-join" -> {
                handler.onNewTeamMember(wireClient, payload.data!!.user!!)
            }
            "user.update" -> {
                handler.onUserUpdate(eventId, payload.user!!.id)
            }
            else -> Logger.debug("Unknown event: %s", payload.type)
        }
    }

    fun addCrypto(crypto: Crypto): EventProcessor {
        this.crypto = crypto
        return this
    }

    fun addAccess(access: Access): EventProcessor {
        this.access = access
        return this
    }

    fun addClient(client: Client?): EventProcessor {
        this.client = client
        return this
    }

    private fun handleConnectionUpdateEvent(wireClient: IWireClient, eventId: UUID, payload: Payload) {
        val connection = payload.connection
        val accepted = handler.onConnectRequest(wireClient, connection!!.from, connection.to, connection.status)
        if (accepted) {
            val systemMessage = getSystemMessage(eventId, payload)
            handler.onNewConversation(wireClient, systemMessage)
        }
    }

    private fun handleConversationRenameEvent(eventId: UUID, payload: Payload, client: IWireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        handler.onConversationRename(client, systemMessage)
    }

    private fun handleConversationCreatedEvent(eventId: UUID, payload: Payload, botId: UUID, client: IWireClient) {
        val systemMessage = getSystemMessage(eventId, payload)
        handler.onNewConversation(client, systemMessage)
    }

    private fun handleConversationDeleteEvent(eventId: UUID, payload: Payload, botId: UUID) {
        val systemMessage = getSystemMessage(eventId, payload)

        // Cleanup
        handler.onBotRemoved(botId, systemMessage)
    }

    private fun handleMemberLeaveEvent(eventId: UUID, payload: Payload, data: Data, botId: UUID, client: IWireClient) {
        val participants = data.userIds!!
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

    private fun handleMemberJoinEvent(data: Data, botId: UUID?, eventId: UUID, payload: Payload, client: IWireClient) {
        val participants = data.userIds!!
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
        val systemMessage = originalSystemMessage.copy(userIds = data.userIds)
        handler.onMemberJoin(client, systemMessage)
    }

    private fun handleMessageAddEvent(payload: Payload, client: IWireClient, eventId: UUID, data: Data) {
        val from = payload.from
        val processor = GenericMessageProcessor(client, handler)
        val genericMessage = decrypt(client, payload)
        val messageId = UUID.fromString(genericMessage.messageId)
        val msgBase = MessageBase(eventId, messageId, payload.conversation!!, data.sender!!, from!!, payload.time!!)
        processor.process(msgBase, genericMessage)
        handler.onEvent(client, from, genericMessage)
    }

    private fun getSystemMessage(eventId: UUID, payload: Payload): SystemMessage {
        val conversation = Conversation(
            id = payload.conversation!!,
            name = payload.data!!.name,
            creator = payload.data.creator,
            members = listOf()
        )
        return SystemMessage(
            eventId,
            payload.type,
            payload.time!!,
            payload.from!!,
            conversation,
            conversation.id,
            null //fix me: conversation.members.map { UUID.fromString(it.userId) }
        )
    }

    @Throws(CryptoException::class, InvalidProtocolBufferException::class)
    private fun decrypt(client: IWireClient, payload: Payload): GenericMessage {
        val from = payload.from!!
        val sender = payload.data!!.sender!!
        val cipher = payload.data.text!!
        val encoded = client.decrypt(from, sender, cipher)
        val decoded = Base64.getDecoder().decode(encoded)
        return GenericMessage.parseFrom(decoded)
    }
}
