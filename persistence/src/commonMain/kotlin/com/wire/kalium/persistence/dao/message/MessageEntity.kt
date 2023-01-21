package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("LongParameterList")
sealed class MessageEntity(
    open val id: String,
    open val content: MessageEntityContent,
    open val conversationId: QualifiedIDEntity,
    open val date: Instant,
    open val senderUserId: QualifiedIDEntity,
    open val status: Status,
    open val visibility: Visibility,
    open val isSelfMessage: Boolean,
) {

    data class Regular(
        override val id: String,
        override val conversationId: QualifiedIDEntity,
        override val date: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val content: MessageEntityContent.Regular,
        override val isSelfMessage: Boolean = false,
        val senderName: String?,
        val senderClientId: String,
        val editStatus: EditStatus,
        val reactions: ReactionsEntity = ReactionsEntity.EMPTY,
        val expectsReadConfirmation: Boolean = false
    ) : MessageEntity(id, content, conversationId, date, senderUserId, status, visibility, isSelfMessage)

    data class System(
        override val id: String,
        override val content: MessageEntityContent.System,
        override val conversationId: QualifiedIDEntity,
        override val date: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val isSelfMessage: Boolean = false,
        val senderName: String?,
    ) : MessageEntity(id, content, conversationId, date, senderUserId, status, visibility, isSelfMessage)

    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    sealed class EditStatus {
        object NotEdited : EditStatus()
        data class Edited(val lastDate: Instant) : EditStatus()

        override fun toString(): String {
            return when (this) {
                is NotEdited -> "NOT_EDITED"
                is Edited -> "EDITED_AT: ${this.lastDate}"
            }
        }
    }

    enum class UploadStatus {
        /**
         * There was no attempt done to upload the asset's data to remote (server) storage.
         */
        NOT_UPLOADED,

        /**
         * The asset is currently being uploaded and will be saved internally after a successful upload
         * @see UPLOADED
         */
        IN_PROGRESS,

        /**
         * The asset was uploaded and saved in the internal storage, that should be only readable by this Kalium client.
         */
        UPLOADED,

        /**
         * The last attempt at uploading and saving this asset's data failed.
         */
        FAILED
    }

    enum class DownloadStatus {
        /**
         * There was no attempt done to fetch the asset's data from remote (server) storage.
         */
        NOT_DOWNLOADED,

        /**
         * The asset is currently being downloaded and will be saved internally after a successful download
         * @see SAVED_INTERNALLY
         */
        IN_PROGRESS,

        /**
         * The asset was downloaded and saved in the internal storage, that should be only readable by this Kalium client.
         */
        SAVED_INTERNALLY,

        /**
         * The asset was downloaded internally and saved in an external storage, readable by other software on the machine that this Kalium
         * client is currently running on.
         *
         * _.e.g_: Asset was saved in Downloads, Desktop or other user-chosen directory.
         */
        SAVED_EXTERNALLY,

        /**
         * The last attempt at fetching and saving this asset's data failed.
         */
        FAILED
    }

    @Serializable
    enum class ContentType {
        TEXT, ASSET, KNOCK, MEMBER_CHANGE, MISSED_CALL, RESTRICTED_ASSET,
        CONVERSATION_RENAMED, UNKNOWN, FAILED_DECRYPTION, REMOVED_FROM_TEAM, CRYPTO_SESSION_RESET
    }

    enum class MemberChangeType {
        ADDED, REMOVED
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN;

        val isVisible get() = this == VISIBLE
    }

    @Serializable
    data class Mention(
        @SerialName("start") val start: Int,
        @SerialName("length") val length: Int,
        @SerialName("userId") val userId: QualifiedIDEntity
    )
}

sealed class MessageEntityContent {

    sealed class Regular : MessageEntityContent()

    sealed class System : MessageEntityContent()
    sealed class Signaling : MessageEntityContent()

    data class Text(
        val messageBody: String,
        val mentions: List<MessageEntity.Mention> = listOf(),
        /**
         * ID of a message being quoted.
         * When persisting the content, this is the ID that will be used for quotes.
         *
         * TODO(refactor): Consider removing this
         *                 Only exists to make it easier to insert into the DB
         *                 Otherwise we'd need to pass a full QuotedMessage object
         */
        val quotedMessageId: String? = null,
        val isQuoteVerified: Boolean? = null,
        /**
         * Details of the message being quoted.
         * Unused when inserting into the DB.
         */
        val quotedMessage: QuotedMessage? = null,
    ) : Regular() {
        data class QuotedMessage(
            val id: String,
            val senderId: QualifiedIDEntity,
            val isQuotingSelfUser: Boolean,
            /**
             * Indicates that the hash of the quote
             * matches the hash of the original message
             */
            val isVerified: Boolean,
            val senderName: String?,
            val dateTime: String,
            val editTimestamp: String?,
            val visibility: MessageEntity.Visibility,
            val contentType: MessageEntity.ContentType,
            val textBody: String?,
            val assetMimeType: String?,
            val assetName: String?,
        )
    }

    data class Asset(
        val assetSizeInBytes: Long,
        // TODO: Make it not-nullable, fallback to message ID or something else if it comes without a name from the protobuf models
        val assetName: String? = null,
        val assetMimeType: String,
        val assetUploadStatus: MessageEntity.UploadStatus? = null,
        val assetDownloadStatus: MessageEntity.DownloadStatus? = null,

        // remote data fields
        val assetOtrKey: ByteArray,
        val assetSha256Key: ByteArray,
        val assetId: String,
        val assetToken: String? = null,
        val assetDomain: String? = null,
        val assetEncryptionAlgorithm: String?,

        // metadata fields
        val assetWidth: Int? = null,
        val assetHeight: Int? = null,
        val assetDurationMs: Long? = null,
        val assetNormalizedLoudness: ByteArray? = null,
    ) : Regular()

    data class Knock(val hotKnock: Boolean) : Regular()

    data class Unknown(
        val typeName: String? = null,
        val encodedData: ByteArray? = null
    ) : Regular()

    data class FailedDecryption(
        val encodedData: ByteArray? = null,
        val isDecryptionResolved: Boolean,
        val senderUserId: QualifiedIDEntity,
        val senderClientId: String?,
    ) : Regular()

    data class MemberChange(
        val memberUserIdList: List<QualifiedIDEntity>,
        val memberChangeType: MessageEntity.MemberChangeType
    ) : System()

    data class RestrictedAsset(
        val mimeType: String,
        val assetSizeInBytes: Long,
        val assetName: String,
    ) : Regular()

    object MissedCall : System()
    object CryptoSessionReset : System()
    data class ConversationRenamed(val conversationName: String) : System()
    data class TeamMemberRemoved(val userName: String) : System()
}

/**
 * Simplified model of [MessageEntity]
 * used everywhere where there is no need to have all the fields
 * for example in conversation list or notifications
 */
data class MessagePreviewEntity(
    val id: String,
    val conversationId: QualifiedIDEntity,
    val content: MessagePreviewEntityContent,
    val date: String,
    val visibility: MessageEntity.Visibility,
    val isSelfMessage: Boolean
)

data class NotificationMessageEntity(
    val id: String,
    val content: MessagePreviewEntityContent,
    val conversationId: QualifiedIDEntity,
    val conversationName: String?,
    val conversationType: ConversationEntity.Type?,
    val date: String
)

sealed class MessagePreviewEntityContent {

    data class Text(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class Asset(val senderName: String?, val type: AssetTypeEntity) : MessagePreviewEntityContent()

    data class MentionedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class QuotedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class MissedCall(val senderName: String?) : MessagePreviewEntityContent()

    data class Knock(val senderName: String?) : MessagePreviewEntityContent()

    data class MemberChange(
        val adminName: String?,
        val count: Int, // TODO add usernames
        val type: MessageEntity.MemberChangeType
    ) : MessagePreviewEntityContent()

    data class ConversationNameChange(val adminName: String?) : MessagePreviewEntityContent()

    data class TeamMemberRemoved(val userName: String?) : MessagePreviewEntityContent()

    object CryptoSessionReset : MessagePreviewEntityContent()
    object Unknown : MessagePreviewEntityContent()

}

enum class AssetTypeEntity {
    IMAGE,
    VIDEO,
    AUDIO,
    ASSET,
    FILE
}

typealias UnreadContentCountEntity = Map<MessageEntity.ContentType, Int>
