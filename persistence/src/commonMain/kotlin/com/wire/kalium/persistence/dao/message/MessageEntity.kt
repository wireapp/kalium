package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("LongParameterList")
sealed class MessageEntity(
    open val id: String,
    open val content: MessageEntityContent,
    open val conversationId: QualifiedIDEntity,
    open val date: String,
    open val senderUserId: QualifiedIDEntity,
    open val status: Status,
    open val visibility: Visibility
) {

    data class Regular(
        override val id: String,
        override val conversationId: QualifiedIDEntity,
        override val date: String,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val content: MessageEntityContent.Regular,
        val senderClientId: String,
        val editStatus: EditStatus,
        val reactions: ReactionsEntity = ReactionsEntity.EMPTY
    ) : MessageEntity(id, content, conversationId, date, senderUserId, status, visibility)

    data class System(
        override val id: String,
        override val content: MessageEntityContent.System,
        override val conversationId: QualifiedIDEntity,
        override val date: String,
        override val senderUserId: QualifiedIDEntity,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE
    ) : MessageEntity(id, content, conversationId, date, senderUserId, status, visibility)

    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    sealed class EditStatus {
        object NotEdited : EditStatus()
        data class Edited(val lastTimeStamp: String) : EditStatus()

        override fun toString(): String {
            return when (this) {
                is NotEdited -> "NOT_EDITED"
                is Edited -> "EDITED_AT: ${this.lastTimeStamp}"
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
        CONVERSATION_RENAMED, UNKNOWN, FAILED_DECRYPTION, REMOVED_FROM_TEAM
    }

    enum class MemberChangeType {
        ADDED, REMOVED
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
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

    data class Text(
        val messageBody: String,
        val mentions: List<MessageEntity.Mention> = listOf()
    ) : Regular()

    data class Asset(
        val assetSizeInBytes: Long,
        // TODO: Make it not-nullable, fallback to
        //       message ID or something else if it comes
        //       without a name from the protobuf models
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

    data class FailedDecryption(val encodedData: ByteArray? = null) : Regular()

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
    data class ConversationRenamed(val conversationName: String) : System()
    data class TeamMemberRemoved(val userName: String) : System()
}
