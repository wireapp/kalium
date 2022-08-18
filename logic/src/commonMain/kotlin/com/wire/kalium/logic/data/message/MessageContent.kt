package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

sealed class MessageContent {

    sealed class System : MessageContent()
    sealed class FromProto : MessageContent()
    sealed class Regular : FromProto()
    sealed class Signaling : FromProto()

    // client message content types
    data class Text(val value: String) : Regular()
    data class Calling(val value: String) : Regular()
    data class Asset(val value: AssetContent) : Regular()
    data class DeleteMessage(val messageId: String) : Regular()
    data class TextEdited(val editMessageId: String, val newContent: String) : Regular()
    data class RestrictedAsset(
        val mimeType: String,
        val sizeInBytes: Long,
        val name: String
    ) : Regular()

    data class DeleteForMe(
        val messageId: String,
        @Deprecated("Use qualified id instead", ReplaceWith("conversationId"))
        val unqualifiedConversationId: String,
        val conversationId: ConversationId?,
    ) : Regular()

    data class Knock(val hotKnock: Boolean) : Regular()

    data class Unknown( // messages that aren't yet handled properly but stored in db in case
        val typeName: String? = null,
        val encodedData: ByteArray? = null,
        val hidden: Boolean = false
    ) : Regular()

    object Empty : Regular()

    // server message content types
    // TODO: rename members to userList
    sealed class MemberChange(open val members: List<UserId>) : System() {
        data class Added(override val members: List<UserId>) : MemberChange(members)
        data class Removed(override val members: List<UserId>) : MemberChange(members)
    }

    data class LastRead(
        val messageId: String,
        @Deprecated("Use qualified id instead", ReplaceWith("conversationId"))
        val unqualifiedConversationId: String,
        val conversationId: ConversationId?,
        val time: Instant
    ) : Regular()

    object MissedCall : System()

    data class Availability(val status: UserAvailabilityStatus) : Signaling()

    // we can add other types to be processed, but signaling ones shouldn't be persisted
    object Ignored : Signaling() // messages that aren't processed in any way

    data class FailedDecryption(val encodedData: ByteArray? = null) : Regular()
}
