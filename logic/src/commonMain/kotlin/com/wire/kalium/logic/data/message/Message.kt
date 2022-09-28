package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

@Suppress("LongParameterList")
sealed class Message(
    open val id: String,
    open val content: MessageContent,
    open val conversationId: ConversationId,
    open val date: String,
    open val senderUserId: UserId,
    open val status: Status,
    open val visibility: Visibility
) {

    data class Regular(
        override val id: String,
        override val content: MessageContent.Regular,
        override val conversationId: ConversationId,
        override val date: String,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        val senderClientId: ClientId,
        val editStatus: EditStatus
    ) : Message(id, content, conversationId, date, senderUserId, status, visibility) {
        override fun toString(): String {
            val contentString: String
            when (content) {
                is MessageContent.Text, is MessageContent.TextEdited, is MessageContent.Calling, is MessageContent.DeleteMessage -> {
                    contentString = ""
                }

                is MessageContent.Asset -> {
                    contentString = "content: {name: ${content.value.name}, sizeInBytes:${content.value.sizeInBytes}, mimeType: ${
                        content.value.mimeType}, metaData : ${content.value.metadata}, downloadStatus: ${content.value.downloadStatus}, " +
                            "uploadStatus: ${content.value.uploadStatus}}, remoteData - otrKeySize: ${content.value.remoteData.otrKey.size}"
                }

                is MessageContent.RestrictedAsset -> {
                    contentString = "content:{sizeInBytes:${content.sizeInBytes} ," +
                            " mimeType:${content.mimeType}"
                }

                is MessageContent.DeleteForMe -> {
                    contentString = "content:{messageId:${content.messageId.obfuscateId()}"
                }

                is MessageContent.LastRead -> {
                    contentString = "content:{time:${content.time}"
                }

                is MessageContent.FailedDecryption -> {
                    contentString = "content:{size:${content.encodedData?.size}"
                }

                else -> {
                    contentString = "content:$content"
                }
            }
            return "id: ${id.obfuscateId()} " +
                    "$contentString  conversationId:${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}*** " +
                    "date:$date  senderUserId:${senderUserId.value.obfuscateId()}  status:$status visibility:$visibility " +
                    "senderClientId${senderClientId.value.obfuscateId()}  editStatus:$editStatus"
        }
    }

    data class System(
        override val id: String,
        override val content: MessageContent.System,
        override val conversationId: ConversationId,
        override val date: String,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE
    ) : Message(id, content, conversationId, date, senderUserId, status, visibility) {
        override fun toString(): String {
            var contentString = ""
            when (content) {
                is MessageContent.MemberChange -> {
                    content.members.map {
                        contentString += "${it.value.obfuscateId()}@${it.domain.obfuscateDomain()}"
                    }
                }
                else -> {
                    contentString = content.toString()
                }
            }

            return "id:${id.obfuscateId()} " +
                    "content:$contentString " +
                    "conversationId:${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}*** " +
                    "date:$date  senderUserId:${senderUserId.value.obfuscateId()}  status:$status  visibility:$visibility"
        }
    }

    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    sealed class EditStatus {
        object NotEdited : EditStatus()
        data class Edited(val lastTimeStamp: String) : EditStatus()
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
        UPLOAD_IN_PROGRESS,

        /**
         * The asset was uploaded and saved in the internal storage, that should be only readable by this Kalium client.
         */
        UPLOADED,

        /**
         * The last attempt at uploading and saving this asset's data failed.
         */
        FAILED_UPLOAD
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
        DOWNLOAD_IN_PROGRESS,

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
        FAILED_DOWNLOAD
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}
