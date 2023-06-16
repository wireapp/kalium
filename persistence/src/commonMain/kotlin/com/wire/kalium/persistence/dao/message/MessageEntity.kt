/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAssetIdEntity
import com.wire.kalium.persistence.dao.UserIDEntity
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
        val expireAfterMs: Long? = null,
        val selfDeletionStartDate: Instant? = null,
        val reactions: ReactionsEntity = ReactionsEntity.EMPTY,
        val expectsReadConfirmation: Boolean = false
    ) : MessageEntity(
        id = id,
        content = content,
        conversationId = conversationId,
        date = date,
        senderUserId = senderUserId,
        status = status,
        visibility = visibility,
        isSelfMessage = isSelfMessage
    )

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
    ) : MessageEntity(
        id = id,
        content = content,
        conversationId = conversationId,
        date = date,
        senderUserId = senderUserId,
        status = status,
        visibility = visibility,
        isSelfMessage = isSelfMessage
    )

    enum class Status {
        /**
         * The message was stored locally and is ready to be sent.
         */
        PENDING,

        /**
         * The message was sent to the backend.
         */
        SENT,

        /**
         * The message was marked as read locally.
         */
        READ,

        /**
         * The message failed to be sent, general errors, e.g. self backend not available, etc.
         */
        FAILED,

        /**
         * The message failed to be sent because the conversation owner is not available.
         * Note that this is currently only relevant for federated conversations.
         */
        FAILED_REMOTELY
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

    enum class ConfirmationType {
        READ, DELIVERED, UNRECOGNIZED
    }

    @Serializable
    enum class ContentType {
        TEXT, ASSET, KNOCK, MEMBER_CHANGE, MISSED_CALL, RESTRICTED_ASSET,
        CONVERSATION_RENAMED, UNKNOWN, FAILED_DECRYPTION, REMOVED_FROM_TEAM, CRYPTO_SESSION_RESET,
        NEW_CONVERSATION_RECEIPT_MODE, CONVERSATION_RECEIPT_MODE_CHANGED, HISTORY_LOST, CONVERSATION_MESSAGE_TIMER_CHANGED,
        CONVERSATION_CREATED
    }

    enum class MemberChangeType {
        ADDED, REMOVED, CREATION_ADDED, FAILED_TO_ADD
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
    data class NewConversationReceiptMode(val receiptMode: Boolean) : System()
    data class ConversationReceiptModeChanged(val receiptMode: Boolean) : System()
    data class ConversationMessageTimerChanged(val messageTimer: Long?) : System()
    object HistoryLost : System()
    object ConversationCreated : System()
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
    val isSelfMessage: Boolean,
    val senderUserId: QualifiedIDEntity,
)

data class NotificationMessageEntity(
    val id: String,
    val contentType: MessageEntity.ContentType,
    val isSelfDelete: Boolean,
    val senderUserId: QualifiedIDEntity,
    val senderImage: UserAssetIdEntity?,

    val date: Instant,
    val senderName: String?,
    val text: String?,
    val assetMimeType: String?,
    val isQuotingSelf: Boolean,

    val conversationId: QualifiedIDEntity,
    val conversationName: String?,
    val mutedStatus: ConversationEntity.MutedStatus,
    val conversationType: ConversationEntity.Type,
)

sealed class MessagePreviewEntityContent {

    data class Text(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class Asset(val senderName: String?, val type: AssetTypeEntity) : MessagePreviewEntityContent()

    data class MentionedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class QuotedSelf(val senderName: String?, val messageBody: String) : MessagePreviewEntityContent()

    data class MissedCall(val senderName: String?) : MessagePreviewEntityContent()

    data class Knock(val senderName: String?) : MessagePreviewEntityContent()

    data class MembersAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MembersRemoved(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MembersFailedToAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MembersCreationAdded(
        val senderName: String?,
        val otherUserIdList: List<UserIDEntity>,
        val isContainSelfUserId: Boolean,
    ) : MessagePreviewEntityContent()

    data class MemberJoined(val senderName: String?) : MessagePreviewEntityContent()

    data class MemberLeft(val senderName: String?) : MessagePreviewEntityContent()

    data class ConversationNameChange(val adminName: String?) : MessagePreviewEntityContent()

    data class TeamMemberRemoved(val userName: String?) : MessagePreviewEntityContent()
    data class Ephemeral(val isGroupConversation: Boolean) : MessagePreviewEntityContent()
    object CryptoSessionReset : MessagePreviewEntityContent()
    object Unknown : MessagePreviewEntityContent()

}

enum class AssetTypeEntity {
    IMAGE,
    VIDEO,
    AUDIO,
    GENERIC_ASSET
}

typealias UnreadContentCountEntity = Map<MessageEntity.ContentType, Int>
