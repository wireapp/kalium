package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

sealed class MessageContent {

    /**
     * Messages that are not sent between client, but
     * are instead generated by this client and stored locally
     * to be displayed and in-lined with the rest of the content
     * of a conversation.
     * These messages can be created based on events received
     * from the backend (_e.g._ users added/removed from a conversation),
     * or our own logic (_e.g._ missed call).
     */
    sealed class System : MessageContent()

    /**
     * Content that can be serialized/deserialized using the
     * regular Protobuf, in order to be transmitted to/from
     * other clients.
     * @see ProtoContentMapper
     */
    sealed class FromProto : MessageContent()

    /**
     * Main content of messages created by users/bot,
     * It's expected that this content will form the
     * main "Conversation View" (_i.e._ the list of
     * messages inside a conversation).
     *
     * Examples: [Text], [Asset], [Knock], Locations (coordinates).
     */
    sealed class Regular : FromProto()

    /**
     * Content that is transferred between clients, but
     * do NOT bring a standalone content to users, that is:
     * these are helping to enrich already existing messages,
     * or provide other sorts of auxiliary features.
     *
     * Examples: [Receipt], [Reaction], [DeleteMessage],
     * [DeleteForMe], [TextEdited], [UserAvailabilityStatus],
     * [Calling], crypto session reset, etc.
     */
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

    data class Asset(val value: AssetContent) : Regular()

    data class RestrictedAsset(
        val mimeType: String,
        val sizeInBytes: Long,
        val name: String
    ) : Regular()

    data class DeleteForMe(
        val messageId: String,
        val conversationId: ConversationId,
    ) : Signaling()

    data class Calling(val value: String) : Signaling()

    data class DeleteMessage(val messageId: String) : Signaling()

    data class TextEdited(
        val editMessageId: String,
        val newContent: String,
        val newMentions: List<MessageMention> = listOf()
    ) : Signaling()

    data class Knock(val hotKnock: Boolean) : Regular()

    data class Unknown( // messages that aren't yet handled properly but stored in db in case
        val typeName: String? = null,
        val encodedData: ByteArray? = null,
        val hidden: Boolean = false
    ) : Regular()

    data class Cleared(
        val conversationId: ConversationId,
        val time: Instant
    ) : Signaling()

    // server message content types
    // TODO: rename members to userList
    sealed class MemberChange(open val members: List<UserId>) : System() {
        data class Added(override val members: List<UserId>) : MemberChange(members)
        data class Removed(override val members: List<UserId>) : MemberChange(members)
    }

    data class LastRead(
        val messageId: String,
        val conversationId: ConversationId,
        val time: Instant
    ) : Signaling()

    data class ConversationRenamed(val conversationName: String) : System()

    data class TeamMemberRemoved(val userName: String) : System()

    object MissedCall : System()

    data class Reaction(
        val messageId: String,
        val emojiSet: Set<String>
    ) : Signaling()

    data class Availability(val status: UserAvailabilityStatus) : Signaling()

    data class Receipt(val type: ReceiptType, val messageIds: List<String>) : Signaling()

    // we can add other types to be processed, but signaling ones shouldn't be persisted
    object Ignored : Signaling() // messages that aren't processed in any way

    data class FailedDecryption(val encodedData: ByteArray? = null, val isDecryptionResolved: Boolean) : Regular()

    object SessionReset : Signaling()
}

sealed class MessagePreviewContent {

    sealed class WithUser(open val username: String?) : MessagePreviewContent() {

        data class Text(override val username: String?, val messageBody: String) : WithUser(username)

        data class Asset(override val username: String?, val type: AssetType) : WithUser(username)

        data class MentionedSelf(override val username: String?) : WithUser(username)

        data class QuotedSelf(override val username: String?) : WithUser(username)

        data class Knock(override val username: String?) : WithUser(username)

        data class MembersAdded(
            val adminName: String?,
            val count: Int, // TODO add usernames
        ) : WithUser(adminName)

        data class MembersRemoved(
            val adminName: String?,
            val count: Int, // TODO add usernames
        ) : WithUser(adminName)

        data class ConversationNameChange(val adminName: String?) : WithUser(adminName)

        data class TeamMemberRemoved(val userName: String?) : WithUser(userName)

        data class MissedCall(override val username: String?) : WithUser(username)

    }

    object Unknown : MessagePreviewContent()

}
