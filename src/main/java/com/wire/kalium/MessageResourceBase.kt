package com.wire.kalium

import kotlin.Throws
import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.models.MessageBase
import com.wire.kalium.backend.models.Conversation
import com.wire.kalium.backend.models.SystemMessage
import com.wire.kalium.backend.GenericMessageProcessor
import com.google.protobuf.InvalidProtocolBufferException
import com.wire.kalium.backend.models.Member
import com.wire.kalium.backend.models.Payload
import com.wire.kalium.tools.Logger
import java.util.Base64

abstract class MessageResourceBase(protected val handler: MessageHandlerBase?) {
    @Throws(Exception::class)
    protected fun handleMessage(eventId: UUID?, payload: Payload?, client: WireClient?) {
        val data = payload.data
        val botId = client.getId()
        when (payload.type) {
            "conversation.otr-message-add" -> {
                val from = payload.from
                Logger.debug("conversation.otr-message-add: bot: %s from: %s:%s", botId, from, data.sender)
                val processor = GenericMessageProcessor(client, handler)
                val genericMessage = decrypt(client, payload)
                val messageId = UUID.fromString(genericMessage.getMessageId())
                val msgBase = MessageBase(eventId, messageId, payload.convId, data.sender, from, payload.time)
                processor.process(msgBase, genericMessage)
                handler.onEvent(client, from, genericMessage)
            }
            "conversation.member-join" -> {
                Logger.debug("conversation.member-join: bot: %s", botId)

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
            "conversation.member-leave" -> {
                Logger.debug("conversation.member-leave: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                systemMessage.users = data.userIds

                // Check if this bot got removed from the conversation
                participants = data.userIds
                if (participants.remove(botId)) {
                    handler.onBotRemoved(botId, systemMessage)
                    return
                }
                if (!participants.isEmpty()) {
                    handler.onMemberLeave(client, systemMessage)
                }
            }
            "conversation.delete" -> {
                Logger.debug("conversation.delete: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)

                // Cleanup
                handler.onBotRemoved(botId, systemMessage)
            }
            "conversation.create" -> {
                Logger.debug("conversation.create: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                if (systemMessage.conversation.members != null) {
                    val self = Member()
                    self.id = botId
                    systemMessage.conversation.members.add(self)
                }
                handler.onNewConversation(client, systemMessage)
            }
            "conversation.rename" -> {
                Logger.debug("conversation.rename: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                handler.onConversationRename(client, systemMessage)
            }
            "user.connection" -> {
                val connection = payload.connection
                Logger.debug(
                    "user.connection: bot: %s, from: %s to: %s status: %s",
                    botId,
                    connection.from,
                    connection.to,
                    connection.status
                )
                val accepted = handler.onConnectRequest(client, connection.from, connection.to, connection.status)
                if (accepted) {
                    val conversation = Conversation()
                    conversation.id = connection.convId
                    systemMessage = SystemMessage()
                    systemMessage.id = eventId
                    systemMessage.from = connection.from
                    systemMessage.type = payload.type
                    systemMessage.conversation = conversation
                    handler.onNewConversation(client, systemMessage)
                }
            }
            else -> Logger.debug("Unknown event: %s", payload.type)
        }
    }

    private fun getSystemMessage(eventId: UUID?, payload: Payload?): SystemMessage? {
        val systemMessage = SystemMessage()
        systemMessage.id = eventId
        systemMessage.from = payload.from
        systemMessage.time = payload.time
        systemMessage.type = payload.type
        systemMessage.convId = payload.convId
        systemMessage.conversation = Conversation()
        systemMessage.conversation.id = payload.convId
        systemMessage.conversation.creator = payload.data.creator
        systemMessage.conversation.name = payload.data.name
        if (payload.data.members != null) systemMessage.conversation.members = payload.data.members.others
        return systemMessage
    }

    @Throws(CryptoException::class, InvalidProtocolBufferException::class)
    private fun decrypt(client: WireClient?, payload: Payload?): GenericMessage? {
        val from = payload.from
        val sender = payload.data.sender
        val cipher = payload.data.text
        val encoded = client.decrypt(from, sender, cipher)
        val decoded = Base64.getDecoder().decode(encoded)
        return GenericMessage.parseFrom(decoded)
    }
}
