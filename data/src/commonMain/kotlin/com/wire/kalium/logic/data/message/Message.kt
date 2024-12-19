/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration

@Suppress("LongParameterList")
sealed interface Message {
    val id: String
    val content: MessageContent
    val conversationId: ConversationId
    val date: Instant
    val senderUserId: UserId
    val status: Status
    val expirationData: ExpirationData?
    val sender: User?

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
        override val date: Instant,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val senderUserName: String? = null,
        override val sender: User? = null,
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
                        "code" to "${content.errorCode}",
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

                is MessageContent.Location -> mutableMapOf(
                    typeKey to "location",
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
        override val date: Instant,
        override val senderUserId: UserId,
        override val senderClientId: ClientId,
        override val status: Status,
        override val senderUserName: String? = null,
        override val isSelfMessage: Boolean,
        override val expirationData: ExpirationData?,
        override val sender: User? = null
    ) : Sendable {
        @Suppress("LongMethod")
        override fun toLogString(): String {
            return "${toLogMap().toJsonElement()}"
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod")
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

                is MessageContent.DataTransfer -> mutableMapOf(
                    typeKey to "dataTransfer",
                    "content" to content.toLogMap(),
                )

                is MessageContent.InCallEmoji -> mutableMapOf(
                    typeKey to "inCallEmoji",
                    "content" to content.emojis
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
        override val date: Instant,
        override val senderUserId: UserId,
        override val status: Status,
        override val visibility: Visibility = Visibility.VISIBLE,
        override val expirationData: ExpirationData?,
        override val sender: User? = null,
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

                is MessageContent.ConversationVerifiedMLS -> mutableMapOf(
                    typeKey to "conversationVerifiedMLS"
                )

                is MessageContent.ConversationDegradedProteus -> mutableMapOf(
                    typeKey to "conversationDegradedProteus"
                )

                is MessageContent.ConversationVerifiedProteus -> mutableMapOf(
                    typeKey to "conversationVerifiedProteus"
                )

                is MessageContent.FederationStopped.ConnectionRemoved -> mutableMapOf(
                    typeKey to "federationConnectionRemoved"
                )

                is MessageContent.FederationStopped.Removed -> mutableMapOf(
                    typeKey to "federationRemoved"
                )

                is MessageContent.ConversationProtocolChanged -> mutableMapOf(
                    typeKey to "conversationProtocolChanged"
                )

                is MessageContent.ConversationProtocolChangedDuringACall -> mutableMapOf(
                    typeKey to "conversationProtocolChangedDuringACall"
                )

                is MessageContent.ConversationStartedUnverifiedWarning -> mutableMapOf(
                    typeKey to "conversationStartedUnverifiedWarning"
                )

                MessageContent.LegalHold.ForConversation.Disabled -> mutableMapOf(
                    typeKey to "legalHoldDisabledForConversation"
                )
                MessageContent.LegalHold.ForConversation.Enabled -> mutableMapOf(
                    typeKey to "legalHoldEnabledForConversation"
                )
                is MessageContent.LegalHold.ForMembers.Disabled -> mutableMapOf(
                    typeKey to "legalHoldDisabledForMembers",
                    "members" to content.members.fold("") { acc, member ->
                        "$acc, ${member.value.obfuscateId()}@${member.domain.obfuscateDomain()}"
                    }
                )
                is MessageContent.LegalHold.ForMembers.Enabled -> mutableMapOf(
                    typeKey to "legalHoldEnabledForMembers",
                    "members" to content.members.fold("") { acc, member ->
                        "$acc, ${member.value.obfuscateId()}@${member.domain.obfuscateDomain()}"
                    }
                )
            }

            val standardProperties = mapOf(
                "id" to id.obfuscateId(),
                "conversationId" to conversationId.toLogString(),
                "date" to date.toIsoDateTimeString(),
                "senderUserId" to senderUserId.value.obfuscateId(),
                "status" to "$status",
                "visibility" to "$visibility",
            )

            properties.putAll(standardProperties)

            return properties.toMap()
        }
    }

    sealed class Status {

        private companion object {
            const val statusKey = "status"
            const val readCountKey = "readCount"
        }

        open fun toLogString(): String {
            return "${toLogMap().toJsonElement()}"
        }

        open fun toLogMap(): Map<String, Any?> = mapOf(statusKey to "Status.Unknown")

        data object Pending : Status() {
            override fun toLogMap() = mapOf(statusKey to "Status.Pending")
        }

        data object Sent : Status() {
            override fun toLogMap() = mapOf(statusKey to "Status.Sent")
        }

        data object Delivered : Status() {
            override fun toLogMap() = mapOf(statusKey to "Status.Delivered")
        }

        data class Read(val readCount: Long) : Status() {
            override fun toLogMap() = mapOf(
                statusKey to "Status.Read",
                readCountKey to "$readCount"
            )
        }

        data object Failed : Status() {
            override fun toLogMap() = mapOf(statusKey to "Status.Failed")
        }

        data object FailedRemotely : Status() {
            override fun toLogMap() = mapOf(statusKey to "Status.FailedRemotely")
        }
    }

    sealed class EditStatus {
        data object NotEdited : EditStatus()
        data class Edited(val lastEditInstant: Instant) : EditStatus()

        override fun toString(): String = when (this) {
            is NotEdited -> "NOT_EDITED"
            is Edited -> "EDITED_${lastEditInstant.toIsoDateTimeString()}"
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
                "time" to this.lastEditInstant.toIsoDateTimeString()
            )
        }
    }

    @Serializable
    data class ExpirationData(
        @SerialName("expire_after") val expireAfter: Duration,
        @SerialName("self_deletion_status") val selfDeletionStatus: SelfDeletionStatus = SelfDeletionStatus.NotStarted
    ) {

        @Serializable
        sealed class SelfDeletionStatus {

            @Serializable
            data object NotStarted : SelfDeletionStatus()

            @Serializable
            data class Started(@SerialName("self_deletion_end_date") val selfDeletionEndDate: Instant) : SelfDeletionStatus()

            fun toLogMap(): Map<String, String> = when (this) {
                is NotStarted -> mutableMapOf(
                    "value" to "NOT_STARTED"
                )

                is Started -> mutableMapOf(
                    "value" to "STARTED",
                    "end-time" to this.selfDeletionEndDate.toString()
                )
            }

            fun toLogString(): String {
                return Json.encodeToString(toLogMap())
            }
        }

        fun timeLeftForDeletion(): Duration {
            return if (selfDeletionStatus is SelfDeletionStatus.Started) {

                val timeLeft = selfDeletionStatus.selfDeletionEndDate - Clock.System.now()
                // timeLeft can be a negative value if the self deletion end date already passed, if so then 0 seconds should be returned
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
            "expire-end-time" to expireEndTimeElement().toString(),
            "deletion-status" to selfDeletionStatus.toLogMap()
        )

        private fun expireEndTimeElement(): String? {
            return when (val selfDeletionStatus = selfDeletionStatus) {
                SelfDeletionStatus.NotStarted -> null
                is SelfDeletionStatus.Started ->
                    selfDeletionStatus.selfDeletionEndDate.toIsoDateTimeString()
            }
        }
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

    data object CompleteDelivery : DeliveryStatus()

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

data class MessageAssetStatus(
    val id: String,
    val conversationId: ConversationId,
    val transferStatus: AssetTransferStatus
)
