package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("LongParameterList")
sealed interface Message {
    val id: String
    val content: MessageContent
    val conversationId: ConversationId
    val date: String
    val senderUserId: UserId
    val status: Status

    /**
     * Messages that can be sent from one client to another.
     */
    sealed interface Sendable : Message {
        override val content: MessageContent.FromProto
        val senderUserName: String? // TODO we can get it from entity but this will need a lot of changes in use cases,
        val isSelfMessage: Boolean
        val senderClientId: ClientId
    }

    /**
     * Messages with a content that stands by itself in
     * the list of messages within a conversation.
     * For example, a text message or system message.
     *
     * A counter example would be a message edit or a reaction.
     * These are just "attached" to another message.
     *
     * @see MessageContent.Regular
     * @see MessageContent.System
     */
    sealed interface Standalone : Message {
        val visibility: Visibility
    }

    data class Regular(
        override val id: String,
        override val content: MessageContent.Regular,
        override val conversationId: ConversationId,
        override val date: String,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val senderUserName: String? = null,
        override val isSelfMessage: Boolean = false,
        override val senderClientId: ClientId,
        val editStatus: EditStatus,
        val reactions: Reactions = Reactions.EMPTY,
        val expectsReadConfirmation: Boolean = false
    ) : Sendable, Standalone {
        @Suppress("LongMethod")
        override fun toString(): String {
            val typeKey = "type"
            val properties: MutableMap<String, String> = when (content) {
                is MessageContent.Text -> mutableMapOf(
                    typeKey to "text"
                )

                is MessageContent.Asset -> mutableMapOf(
                    typeKey to "asset",
                    "sizeInBytes" to "${content.value.sizeInBytes}",
                    "mimeType" to content.value.mimeType,
                    "metaData" to "${content.value.metadata}",
                    "downloadStatus" to "${content.value.downloadStatus}",
                    "uploadStatus" to "${content.value.uploadStatus}",
                    "otrKeySize" to "${content.value.remoteData.otrKey.size}",
                )

                is MessageContent.RestrictedAsset -> mutableMapOf(
                    typeKey to "restrictedAsset",
                    "sizeInBytes" to "${content.sizeInBytes}",
                    "mimeType" to content.mimeType,
                )

                is MessageContent.FailedDecryption -> {
                    mutableMapOf(
                        typeKey to "failedDecryption",
                        "size" to "${content.encodedData?.size}",
                    )
                }

                is MessageContent.Knock -> mutableMapOf(
                    typeKey to "knock"
                )

                is MessageContent.Unknown -> mutableMapOf(
                    typeKey to "unknown"
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "status" to "$status",
                "visibility" to "$visibility",
                "senderClientId" to senderClientId.value.obfuscateId(),
                "editStatus" to "$editStatus",
                "expectsReadConfirmation" to "$expectsReadConfirmation"
            )

            properties.putAll(standardProperties)

            return Json.encodeToString(properties.toMap())
        }
    }

    data class Signaling(
        override val id: String,
        override val content: MessageContent.Signaling,
        override val conversationId: ConversationId,
        override val date: String,
        override val senderUserId: UserId,
        override val senderClientId: ClientId,
        override val status: Status,
        override val senderUserName: String? = null,
        override val isSelfMessage: Boolean = false,
    ) : Sendable {
        override fun toString(): String {
            val typeKey = "type"

            val properties: MutableMap<String, String> = when (content) {
                is MessageContent.TextEdited -> mutableMapOf(
                    typeKey to "textEdit"
                )

                is MessageContent.Calling -> mutableMapOf(
                    typeKey to "calling"
                )

                is MessageContent.ClientAction -> mutableMapOf(
                    typeKey to "clientAction"
                )

                is MessageContent.DeleteMessage -> mutableMapOf(
                    typeKey to "delete"
                )

                is MessageContent.DeleteForMe -> mutableMapOf(
                    typeKey to "deleteForMe",
                    "messageId" to content.messageId.obfuscateId(),
                )

                is MessageContent.LastRead -> mutableMapOf(
                    typeKey to "lastRead",
                    "time" to "${content.time}",
                )

                is MessageContent.Availability -> mutableMapOf(
                    typeKey to "availability",
                    "content" to "$content",
                )

                is MessageContent.Cleared -> mutableMapOf(
                    typeKey to "cleared",
                    "content" to "$content",
                )

                is MessageContent.Reaction -> mutableMapOf(
                    typeKey to "reaction",
                    "content" to "$content",
                )

                is MessageContent.Receipt -> mutableMapOf(
                    typeKey to "receipt",
                    "content" to "$content",
                )

                MessageContent.Ignored -> mutableMapOf(
                    typeKey to "ignored",
                    "content" to "$content",
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "senderClientId" to senderClientId.value.obfuscateId(),
            )

            properties.putAll(standardProperties)

            return Json.encodeToString(properties.toMap())
        }

    }

    data class System(
        override val id: String,
        override val content: MessageContent.System,
        override val conversationId: ConversationId,
        override val date: String,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        // TODO(refactor): move senderName to inside the specific `content`
        //                 instead of having it nullable in all system messages
        val senderUserName: String? = null,
    ) : Message, Standalone {
        override fun toString(): String {

            val typeKey = "type"
            val properties: MutableMap<String, String> = when (content) {
                is MessageContent.MemberChange -> mutableMapOf(
                    typeKey to "memberChange",
                    "members" to content.members.fold("") { acc, member ->
                        return "$acc, ${member.value.obfuscateId()}@${member.domain.obfuscateDomain()}"
                    }
                )

                is MessageContent.ConversationRenamed -> mutableMapOf(
                    typeKey to "conversationRenamed"
                )

                MessageContent.MissedCall -> mutableMapOf(
                    typeKey to "missedCall"
                )

                is MessageContent.TeamMemberRemoved -> mutableMapOf(
                    typeKey to "teamMemberRemoved"
                )

                is MessageContent.CryptoSessionReset -> mutableMapOf(
                    typeKey to "cryptoSessionReset"
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "status" to "$status",
                "visibility" to "$visibility",
            )

            properties.putAll(standardProperties)

            return Json.encodeToString(properties.toMap())
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

    data class Reactions(
        val totalReactions: ReactionsCount,
        val selfUserReactions: UserReactions
    ) {
        companion object {
            val EMPTY = Reactions(emptyMap(), emptySet())
        }
    }
}

@Suppress("MagicNumber")
enum class UnreadEventType(val priority: Int) {
    KNOCK(1),
    MISSED_CALL(2),
    MENTION(3),
    REPLY(4),
    MESSAGE(5), // text or asset
    IGNORED(10),
}

data class MessagePreview(
    val id: String,
    val conversationId: ConversationId,
    val content: MessagePreviewContent,
    val date: String,
    val visibility: Message.Visibility,
    val isSelfMessage: Boolean
)

enum class AssetType {
    IMAGE,
    VIDEO,
    AUDIO,
    ASSET,
    FILE
}

typealias ReactionsCount = Map<String, Int>
typealias UserReactions = Set<String>
