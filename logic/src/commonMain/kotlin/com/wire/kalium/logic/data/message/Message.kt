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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration

@Suppress("LongParameterList")
sealed interface Message {
    val id: String
    val content: MessageContent
    val conversationId: ConversationId
    val date: String
    val senderUserId: UserId
    val status: Status
    val expirationData: ExpirationData?

    /**
     * Messages that can be sent from one client to another.
     */
    sealed interface Sendable : Message {
        override val content: MessageContent.FromProto
        val senderUserName: String? // TODO we can get it from entity but this will need a lot of changes in use cases,
        val isSelfMessage: Boolean
        val senderClientId: ClientId

        fun toLogString(): String
        fun toLogMap(): Map<String, Any?>
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
        override val isSelfMessage: Boolean,
        override val senderClientId: ClientId,
        val editStatus: EditStatus,
        override val expirationData: ExpirationData? = null,
        val reactions: Reactions = Reactions.EMPTY,
        val expectsReadConfirmation: Boolean = false,
        val deliveryStatus: DeliveryStatus = DeliveryStatus.CompleteDelivery
    ) : Sendable, Standalone {

        override fun toLogString(): String {
            return "${toLogMap().toJsonElement()}"
        }
        @Suppress("LongMethod")
        override fun toLogMap(): Map<String, Any?> {
            val typeKey = "type"
            val properties: MutableMap<String, Any?> = when (content) {
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
                    typeKey to "knock",
                    "hot" to "${content.hotKnock}"
                )

                is MessageContent.Unknown -> mutableMapOf(
                    typeKey to "unknown"
                )

                is MessageContent.Composite -> mutableMapOf(
                    typeKey to "composite"
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to conversationId.toLogString(),
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "status" to "$status",
                "visibility" to "$visibility",
                "senderClientId" to senderClientId.value.obfuscateId(),
                "editStatus" to editStatus.toLogMap(),
                "expectsReadConfirmation" to "$expectsReadConfirmation",
                "deliveryStatus" to deliveryStatus.toLogMap(),
                "expirationData" to expirationData?.toLogMap()
            )

            properties.putAll(standardProperties)

            return properties.toMap()
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
        override val isSelfMessage: Boolean,
        override val expirationData: ExpirationData?
    ) : Sendable {
        @Suppress("LongMethod")
        override fun toLogString(): String {
            return "${toLogMap().toJsonElement()}"
        }

        @Suppress("LongMethod")
        override fun toLogMap(): Map<String, Any?> {
            val typeKey = "type"

            val properties: MutableMap<String, Any> = when (content) {
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
                )

                is MessageContent.Cleared -> mutableMapOf(
                    typeKey to "cleared",
                )

                is MessageContent.Reaction -> mutableMapOf(
                    typeKey to "reaction",
                )

                is MessageContent.Receipt -> mutableMapOf(
                    typeKey to "receipt",
                    "content" to content.toLogMap(),
                )

                MessageContent.Ignored -> mutableMapOf(
                    typeKey to "ignored",
                    "content" to content.getType(),
                )

                is MessageContent.ButtonAction -> mutableMapOf(
                    typeKey to "buttonAction"
                )

                is MessageContent.ButtonActionConfirmation -> mutableMapOf(
                    typeKey to "buttonActionConfirmation"
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to conversationId.toLogString(),
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "senderClientId" to senderClientId.value.obfuscateId(),
            )

            properties.putAll(standardProperties)

            return properties.toMap()
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
        override val expirationData: ExpirationData?,
        // TODO(refactor): move senderName to inside the specific `content`
        //                 instead of having it nullable in all system messages
        val senderUserName: String? = null
    ) : Message, Standalone {
        fun toLogString(): String {
            return "${toLogMap().toJsonElement()}"
        }

        @Suppress("LongMethod", "ComplexMethod")
        fun toLogMap(): Map<String, Any?> {
            val typeKey = "type"
            val properties: MutableMap<String, String> = when (content) {
                is MessageContent.MemberChange -> mutableMapOf(
                    typeKey to "memberChange",
                    "members" to content.members.fold("") { acc, member ->
                         "$acc, ${member.value.obfuscateId()}@${member.domain.obfuscateDomain()}"
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

                is MessageContent.NewConversationReceiptMode -> mutableMapOf(
                    typeKey to "newConversationReceiptMode"
                )

                is MessageContent.ConversationReceiptModeChanged -> mutableMapOf(
                    typeKey to "conversationReceiptModeChanged"
                )

                MessageContent.HistoryLost -> mutableMapOf(
                    typeKey to "conversationMightLostHistory"
                )

                MessageContent.HistoryLostProtocolChanged -> mutableMapOf(
                    typeKey to "conversationMightLostHistoryProtocolChanged"
                )

                is MessageContent.ConversationMessageTimerChanged -> mutableMapOf(
                    typeKey to "conversationMessageTimerChanged"
                )

                is MessageContent.ConversationCreated -> mutableMapOf(
                    typeKey to "conversationCreated"
                )

                is MessageContent.MLSWrongEpochWarning -> mutableMapOf(
                    typeKey to "mlsWrongEpochWarning"
                )

                is MessageContent.ConversationDegradedMLS -> mutableMapOf(
                    typeKey to "conversationDegradedMLS"
                )

                is MessageContent.ConversationDegradedProteus -> mutableMapOf(
                    typeKey to "conversationDegradedProteus"
                )

                is MessageContent.ConversationProtocolChanged -> mutableMapOf(
                    typeKey to "conversationProtocolChanged"
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to conversationId.toLogString(),
                "date" to date,
                "senderUserId" to senderUserId.value.obfuscateId(),
                "status" to "$status",
                "visibility" to "$visibility",
            )

            properties.putAll(standardProperties)

            return properties.toMap()
        }
    }

    enum class Status {
        PENDING, SENT, READ, FAILED, FAILED_REMOTELY
    }

    sealed class EditStatus {
        object NotEdited : EditStatus()
        data class Edited(val lastTimeStamp: String) : EditStatus()

        override fun toString(): String = when (this) {
            is NotEdited -> "NOT_EDITED"
            is Edited -> "EDITED_$lastTimeStamp"
        }

        fun toLogString(): String {
            val properties = toLogMap()
            return Json.encodeToString(properties)
        }

        fun toLogMap(): Map<String, String> = when (this) {
            is NotEdited -> mutableMapOf(
                "value" to "NOT_EDITED"
            )

            is Edited -> mutableMapOf(
                "value" to "EDITED",
                "time" to this.lastTimeStamp
            )
        }
    }

    data class ExpirationData(
        val expireAfter: Duration,
        val selfDeletionStatus: SelfDeletionStatus = SelfDeletionStatus.NotStarted
    ) {

        sealed class SelfDeletionStatus {
            object NotStarted : SelfDeletionStatus()

            data class Started(val selfDeletionStartDate: Instant) : SelfDeletionStatus()

            fun toLogMap(): Map<String, String> = when (this) {
                is NotStarted -> mutableMapOf(
                    "value" to "NOT_STARTED"
                )

                is Started -> mutableMapOf(
                    "value" to "STARTED",
                    "time" to this.selfDeletionStartDate.toString()
                )
            }

            fun toLogString(): String {
                return Json.encodeToString(toLogMap())
            }
        }

        fun timeLeftForDeletion(): Duration {
            return if (selfDeletionStatus is SelfDeletionStatus.Started) {
                val timeElapsedSinceSelfDeletionStartDate = Clock.System.now() - selfDeletionStatus.selfDeletionStartDate

                // time left for deletion it can be a negative value if the time difference between the self deletion start date and
                // now is greater than expire after millis, we normalize it to 0 seconds
                val timeLeft = expireAfter - timeElapsedSinceSelfDeletionStartDate

                if (timeLeft.isNegative()) {
                    Duration.ZERO
                } else {
                    timeLeft
                }
            } else {
                expireAfter
            }
        }

        fun toLogString(): String {
            return Json.encodeToString(toLogMap())
        }

        fun toLogMap(): Map<String, Any?> = mapOf(
            "expire-after" to expireAfter.inWholeSeconds.toString(),
            "expire-start-time" to expireStartTimeElement().toString(),
            "deletion-status" to selfDeletionStatus.toLogMap()
        )

        private fun expireStartTimeElement(): String? {
            return when (val selfDeletionStatus = selfDeletionStatus) {
                SelfDeletionStatus.NotStarted -> null
                is SelfDeletionStatus.Started ->
                    selfDeletionStatus.selfDeletionStartDate.toIsoDateTimeString()
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
    val isSelfMessage: Boolean,
    val senderUserId: UserId
)

enum class AssetType {
    IMAGE,
    VIDEO,
    AUDIO,
    GENERIC_ASSET
}

typealias ReactionsCount = Map<String, Int>
typealias UserReactions = Set<String>

sealed class DeliveryStatus {
    data class PartialDelivery(
        val recipientsFailedWithNoClients: List<UserId>,
        val recipientsFailedDelivery: List<UserId>
    ) : DeliveryStatus()

    object CompleteDelivery : DeliveryStatus()

    fun toLogMap(): Map<String, String> = when (this) {
        is PartialDelivery -> mutableMapOf(
            "value" to "PARTIAL_DELIVERY",
            "failed-with-no-clients" to recipientsFailedWithNoClients.joinToString(",") { it.toLogString() },
            "failed-delivery" to recipientsFailedDelivery.joinToString(",") { it.toLogString() }
        )

        is CompleteDelivery -> mutableMapOf(
            "value" to "COMPLETE_DELIVERY"
        )
    }

    fun toLogString(): String {
        return Json.encodeToString(toLogMap())
    }
}
