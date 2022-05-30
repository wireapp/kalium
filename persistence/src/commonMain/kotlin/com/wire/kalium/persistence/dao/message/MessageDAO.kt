package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow

data class MessageEntity(
    val id: String,
    val content: MessageEntityContent,
    val conversationId: QualifiedIDEntity,
    val date: String,
    val senderUserId: QualifiedIDEntity,
    val senderClientId: String,
    val status: Status,
    val visibility: Visibility = Visibility.VISIBLE
) {
    sealed class MessageEntityContent {
        data class TextMessageContent(val messageBody: String) : MessageEntityContent()
        data class AssetMessageContent(
            val assetMimeType: String,
            val assetSizeInBytes: Long,
            val assetName: String? = null,
            val assetImageWidth: Int? = null,
            val assetImageHeight: Int? = null,
            val assetVideoWidth: Int? = null,
            val assetVideoHeight: Int? = null,
            val assetVideoDurationMs: Long? = null,
            val assetAudioDurationMs: Long? = null,
            val assetAudioNormalizedLoudness: ByteArray? = null,
            val assetOtrKey: ByteArray,
            val assetSha256Key: ByteArray,
            val assetId: String,
            val assetToken: String? = null,
            val assetDomain: String? = null,
            val assetEncryptionAlgorithm: String?,
            val assetDownloadStatus: DownloadStatus? = null,
        ) : MessageEntityContent()
        data class MemberJoinContent(val memberUserIdList: List<QualifiedIDEntity>) : MessageEntityContent()
        data class MemberLeaveContent(val memberUserIdList: List<QualifiedIDEntity>) : MessageEntityContent()
    }

    enum class Status {
        PENDING, SENT, READ, FAILED
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

    enum class ContentType {
        TEXT, ASSET, MEMBER_JOIN, MEMBER_LEAVE
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}

interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun updateAssetDownloadStatus(downloadStatus: MessageEntity.DownloadStatus, id: String, conversationId: QualifiedIDEntity)
    suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status)
    suspend fun getMessagesFromAllConversations(limit: Int, offset: Int): Flow<List<MessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?>
    suspend fun getMessagesByConversation(conversationId: QualifiedIDEntity, limit: Int, offset: Int): Flow<List<MessageEntity>>
    suspend fun getMessagesByConversationAfterDate(conversationId: QualifiedIDEntity, date: String): Flow<List<MessageEntity>>
    suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity>
}
