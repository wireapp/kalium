package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class MigratedMessage(
    val conversationId: ConversationId,
    val senderUserId: UserId,
    val senderClientId: ClientId,
    val timestampIso: String,
    val content: String,
    val encryptedProto: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MigratedMessage

        if (conversationId != other.conversationId) return false
        if (senderUserId != other.senderUserId) return false
        if (senderClientId != other.senderClientId) return false
        if (timestampIso != other.timestampIso) return false
        if (content != other.content) return false
        if (encryptedProto != null) {
            if (other.encryptedProto == null) return false
            if (!encryptedProto.contentEquals(other.encryptedProto)) return false
        } else if (other.encryptedProto != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conversationId.hashCode()
        result = 31 * result + senderUserId.hashCode()
        result = 31 * result + senderClientId.hashCode()
        result = 31 * result + timestampIso.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (encryptedProto?.contentHashCode() ?: 0)
        return result
    }
}
