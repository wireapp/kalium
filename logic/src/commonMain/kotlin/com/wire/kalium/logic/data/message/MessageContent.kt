package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

sealed class MessageContent {

    sealed class System : MessageContent()
    sealed class FromProto : MessageContent()
    sealed class Regular : FromProto()
    sealed class Signaling : FromProto()

    // client message content types
    data class Text(
        val value: String,
        val mentions: List<MessageMention> = listOf(),
        val quotedMessageReference: QuoteReference? = null,
        val quotedMessageDetails: QuotedMessageDetails? = null
    ) : Regular()

    data class QuoteReference(
        val quotedMessageId: String,
        /**
         * The hash of the text of the quoted message
         */
        val quotedMessageSha256: ByteArray?,
        val isVerified: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as QuoteReference

            if (quotedMessageId != other.quotedMessageId) return false
            if (quotedMessageSha256 != null) {
                if (other.quotedMessageSha256 == null) return false
                if (!quotedMessageSha256.contentEquals(other.quotedMessageSha256)) return false
            } else if (other.quotedMessageSha256 != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = quotedMessageId.hashCode()
            result = 31 * result + (quotedMessageSha256?.contentHashCode() ?: 0)
            return result
        }

    }

    data class QuotedMessageDetails(
        val senderId: UserId,
        val senderName: String,
        val isQuotingSelfUser: Boolean,
        /**
         * Indicates that the hash of the quote
         * matched the hash of the original message
         */
        val isVerified: Boolean,
        val messageId: String,
        val timeInstant: Instant,
        val editInstant: Instant?,
        val quotedContent: Content
    ) {

        sealed interface Content

        data class Text(val value: String) : Content

        data class Asset(
            val assetName: String?,
            val assetMimeType: String
        ) : Content

        object Deleted : Content

        object Invalid : Content
    }

    data class Calling(val value: String) : Regular()
    data class Asset(val value: AssetContent) : Regular()
    data class DeleteMessage(val messageId: String) : Regular()
    data class TextEdited(
        val editMessageId: String,
        val newContent: String,
        val newMentions: List<MessageMention> = listOf()
    ) : Regular()

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

    data class Reaction(
        val messageId: String,
        val emojiSet: Set<String>
    ) : Regular()

    data class Knock(val hotKnock: Boolean) : Regular()

    data class Unknown( // messages that aren't yet handled properly but stored in db in case
        val typeName: String? = null,
        val encodedData: ByteArray? = null,
        val hidden: Boolean = false
    ) : Regular()

    object Empty : Regular()

    data class Cleared(
        @Deprecated("Use qualified id instead", ReplaceWith("conversationId"))
        val unqualifiedConversationId: String,
        val conversationId: ConversationId?,
        val time: Instant
    ) : Regular()

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

    data class ConversationRenamed(val conversationName: String) : System()

    data class TeamMemberRemoved(val userName: String) : System()

    object MissedCall : System()

    data class Availability(val status: UserAvailabilityStatus) : Signaling()

    // we can add other types to be processed, but signaling ones shouldn't be persisted
    object Ignored : Signaling() // messages that aren't processed in any way

    data class FailedDecryption(val encodedData: ByteArray? = null) : Regular()
}
