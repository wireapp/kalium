package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class Message(
    val id: String,
    val content: MessageContent,
    val conversationId: ConversationId,
    val date: String,
    val senderUserId: UserId,
    val senderClientId: ClientId,
    val status: Status,
    val editStatus : EditStatus,
    val visibility: Visibility = Visibility.VISIBLE
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    sealed class EditStatus{
        object NotEdited : EditStatus()
        data class Edited(val lastTimeStamp : String) : EditStatus()
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

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}
