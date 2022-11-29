package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SignalingMessage(
    val id: String,
    val content: MessageContent.Signaling,
    val conversationId: ConversationId,
    val date: String,
    val senderUserId: UserId,
    val senderClientId: ClientId
) {
    override fun toString(): String {
        val typeKey = "type"

        val properties: MutableMap<String, String> = when (content) {
            is MessageContent.TextEdited -> mutableMapOf(
                typeKey to "textEdit"
            )

            is MessageContent.Calling -> mutableMapOf(
                typeKey to "calling"
            )

            is MessageContent.DeleteMessage -> mutableMapOf(
                typeKey to "delete"
            )

            is MessageContent.DeleteForMe -> mutableMapOf(
                typeKey to "deleteForMe",
                "messageId" to content.messageId.obfuscateId(),
            )

            is MessageContent.LastRead -> mutableMapOf(
                typeKey to "lastRead",
                "time" to "${content.time}",
            )

            is MessageContent.Availability -> mutableMapOf(
                typeKey to "availability",
                "content" to "$content",
            )

            is MessageContent.Cleared -> mutableMapOf(
                typeKey to "cleared",
                "content" to "$content",
            )

            is MessageContent.Reaction -> mutableMapOf(
                typeKey to "reaction",
                "content" to "$content",
            )

            is MessageContent.Receipt -> mutableMapOf(
                typeKey to "receipt",
                "content" to "$content",
            )

            MessageContent.Ignored -> mutableMapOf(
                typeKey to "ignored",
                "content" to "$content",
            )
        }

        val standardProperties = mapOf(
            "id" to id.obfuscateId(),
            "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
            "date" to date,
            "senderUserId" to senderUserId.value.obfuscateId(),
            "senderClientId" to senderClientId.value.obfuscateId(),
        )

        properties.putAll(standardProperties)

        return Json.encodeToString(properties.toMap())
    }

}
