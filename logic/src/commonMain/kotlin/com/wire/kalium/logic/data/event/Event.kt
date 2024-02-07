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

package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.Conversation.Protocol
import com.wire.kalium.logic.data.conversation.Conversation.ReceiptMode
import com.wire.kalium.logic.data.conversation.Conversation.TypingIndicatorMode
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.logStructuredJson
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A wrapper that joins [Event] with its [EventDeliveryInfo].
 */
data class EventEnvelope(
    val event: Event,
    val deliveryInfo: EventDeliveryInfo,
) {
    override fun toString(): String {
        return super.toString()
    }

    fun toLogString(): String = toLogMap().toJsonElement().toString()

    fun toLogMap(): Map<String, Any?> = mapOf(
        "event" to event.toLogMap(),
        "deliveryInfo" to deliveryInfo.toLogMap()
    )
}

/**
 * Data class representing information about the delivery of an event.
 *
 * @property isTransient Specifies whether the event is transient.
 * Transient events are events that only matter if the user is online/active. For example "user is typing",
 * and call signaling (mute/unmute), which are irrelevant after a few minutes. These are likely to not even
 * be stored in the backend.
 * @property source The source of the event.
 * @see EventSource
 */
data class EventDeliveryInfo(
    val isTransient: Boolean,
    val source: EventSource,
) {
    fun toLogMap(): Map<String, Any?> = mapOf(
        "isTransient" to isTransient,
        "source" to source.name
    )
}

/**
 * Represents an event.
 *
 * @property id The ID of the event. As of Jan 2024, the ID used by the backend is
 * _not_ guaranteed to be unique, so comparing the full object might be necessary.
 */
sealed class Event(open val id: String) {

    private companion object {
        const val typeKey = "type"
        const val idKey = "id"
        const val featureStatusKey = "status"
        const val clientIdKey = "clientId"
        const val userIdKey = "userId"
        const val conversationIdKey = "conversationId"
        const val senderUserIdKey = "senderUserId"
        const val teamIdKey = "teamId"
        const val memberIdKey = "memberId"
        const val timestampIsoKey = "timestampIso"
        const val selfDeletionDurationKey = "selfDeletionDuration"
    }

    open fun toLogString(): String {
        return "${toLogMap().toJsonElement()}"
    }

    abstract fun toLogMap(): Map<String, Any?>

    sealed class Conversation(
        id: String,
        open val conversationId: ConversationId
    ) : Event(id) {
        data class AccessUpdate(
            override val id: String,
            override val conversationId: ConversationId,
            val data: ConversationResponse,
            val qualifiedFrom: UserId,
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.AccessUpdate",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "qualifiedFrom" to qualifiedFrom.toLogString()
            )
        }

        data class NewMessage(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val senderClientId: ClientId,
            val timestampIso: String,
            val content: String,
            val encryptedExternalContent: EncryptedData?
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.NewMessage",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                senderUserIdKey to senderUserId.toLogString(),
                "senderClientId" to senderClientId.value.obfuscateId(),
                timestampIsoKey to timestampIso
            )
        }

        data class NewMLSMessage(
            override val id: String,
            override val conversationId: ConversationId,
            val subconversationId: SubconversationId?,
            val senderUserId: UserId,
            val timestampIso: String,
            val content: String
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.NewMLSMessage",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                senderUserIdKey to senderUserId.toLogString(),
                timestampIsoKey to timestampIso
            )
        }

        data class NewConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
            val conversation: ConversationResponse
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.NewConversation",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                timestampIsoKey to timestampIso
            )
        }

        data class MemberJoin(
            override val id: String,
            override val conversationId: ConversationId,
            val addedBy: UserId,
            val members: List<Member>,
            val timestampIso: String
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.MemberJoin",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "addedBy" to addedBy.toLogString(),
                "members" to members.map { it.toMap() },
                timestampIsoKey to timestampIso
            )
        }

        data class MemberLeave(
            override val id: String,
            override val conversationId: ConversationId,
            val removedBy: UserId,
            val removedList: List<UserId>,
            val timestampIso: String,
            val reason: MemberLeaveReason
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.MemberLeave",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "removedBy" to removedBy.toLogString(),
                timestampIsoKey to timestampIso
            )
        }

        sealed class MemberChanged(
            override val id: String,
            override val conversationId: ConversationId,
            open val timestampIso: String,
        ) : Conversation(id, conversationId) {
            class MemberChangedRole(
                override val id: String,
                override val conversationId: ConversationId,
                override val timestampIso: String,
                val member: Member?,
            ) : MemberChanged(id, conversationId, timestampIso) {

                override fun toLogMap(): Map<String, Any?> = mapOf(
                    typeKey to "Conversation.MemberChangedRole",
                    idKey to id.obfuscateId(),
                    conversationIdKey to conversationId.toLogString(),
                    "member" to (member?.toMap() ?: JsonNull),
                    timestampIsoKey to timestampIso
                )
            }

            data class MemberMutedStatusChanged(
                override val id: String,
                override val conversationId: ConversationId,
                override val timestampIso: String,
                val mutedConversationStatus: MutedConversationStatus,
                val mutedConversationChangedTime: String
            ) : MemberChanged(id, conversationId, timestampIso) {

                override fun toLogMap(): Map<String, Any?> = mapOf(
                    typeKey to "Conversation.MemberMutedStatusChanged",
                    idKey to id.obfuscateId(),
                    conversationIdKey to conversationId.toLogString(),
                    timestampIsoKey to timestampIso,
                    "mutedConversationStatus" to mutedConversationStatus.status,
                    "mutedConversationChangedTime" to mutedConversationChangedTime
                )
            }

            data class MemberArchivedStatusChanged(
                override val id: String,
                override val conversationId: ConversationId,
                override val timestampIso: String,
                val archivedConversationChangedTime: String,
                val isArchiving: Boolean
            ) : MemberChanged(id, conversationId, timestampIso) {

                override fun toLogMap(): Map<String, Any?> = mapOf(
                    typeKey to "Conversation.MemberArchivedStatusChanged",
                    idKey to id.obfuscateId(),
                    conversationIdKey to conversationId.toLogString(),
                    timestampIsoKey to timestampIso,
                    "isArchiving" to isArchiving,
                    "archivedConversationChangedTime" to archivedConversationChangedTime
                )
            }

            data class IgnoredMemberChanged(
                override val id: String,
                override val conversationId: ConversationId,
            ) : MemberChanged(id, conversationId, "") {

                override fun toLogMap(): Map<String, Any?> = mapOf(
                    typeKey to "Conversation.IgnoredMemberChanged",
                    idKey to id.obfuscateId(),
                    conversationIdKey to conversationId.toLogString(),
                )
            }
        }

        data class MLSWelcome(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val message: String,
            val timestampIso: String = DateTimeUtil.currentIsoDateTimeString()
        ) : Conversation(id, conversationId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.MLSWelcome",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                timestampIsoKey to timestampIso,
                senderUserIdKey to senderUserId.toLogString()
            )
        }

        data class DeletedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, conversationId) {

            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.DeletedConversation",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                timestampIsoKey to timestampIso,
                senderUserIdKey to senderUserId.toLogString()
            )
        }

        data class RenamedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val conversationName: String,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, conversationId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.RenamedConversation",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                senderUserIdKey to senderUserId.toLogString(),
                "conversationName" to conversationName,
                timestampIsoKey to timestampIso,
            )
        }

        data class ConversationReceiptMode(
            override val id: String,
            override val conversationId: ConversationId,
            val receiptMode: ReceiptMode,
            val senderUserId: UserId
        ) : Conversation(id, conversationId) {

            override fun toLogMap() = mapOf(
                typeKey to "Conversation.ConversationReceiptMode",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "receiptMode" to receiptMode.name,
                senderUserIdKey to senderUserId.toLogString(),
            )
        }

        data class ConversationMessageTimer(
            override val id: String,
            override val conversationId: ConversationId,
            val messageTimer: Long?,
            val senderUserId: UserId,
            val timestampIso: String
        ) : Conversation(id, conversationId) {

            override fun toLogMap() = mapOf(
                typeKey to "Conversation.ConversationMessageTimer",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "messageTime" to messageTimer,
                senderUserIdKey to senderUserId.toLogString(),
                timestampIsoKey to timestampIso
            )
        }

        data class CodeUpdated(
            override val id: String,
            override val conversationId: ConversationId,
            val key: String,
            val code: String,
            val uri: String,
            val isPasswordProtected: Boolean,
        ) : Conversation(id, conversationId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(typeKey to "Conversation.CodeUpdated")
        }

        data class CodeDeleted(
            override val id: String,
            override val conversationId: ConversationId,
        ) : Conversation(id, conversationId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(typeKey to "Conversation.CodeDeleted")
        }

        data class TypingIndicator(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
            val typingIndicatorMode: TypingIndicatorMode,
        ) : Conversation(id, conversationId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Conversation.TypingIndicator",
                conversationIdKey to conversationId.toLogString(),
                "typingIndicatorMode" to typingIndicatorMode.name,
                senderUserIdKey to senderUserId.toLogString(),
                timestampIsoKey to timestampIso
            )
        }

        data class ConversationProtocol(
            override val id: String,
            override val conversationId: ConversationId,
            val protocol: Protocol,
            val senderUserId: UserId
        ) : Conversation(id, conversationId) {
            override fun toLogMap() = mapOf(
                typeKey to "Conversation.ConversationProtocol",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "protocol" to protocol.name,
                senderUserIdKey to senderUserId.toLogString(),
            )
        }
    }

    sealed class Team(
        id: String,
        open val teamId: String,
    ) : Event(id) {

        data class MemberLeave(
            override val id: String,
            override val teamId: String,
            val memberId: String,
            val timestampIso: String,
        ) : Team(id, teamId) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Team.MemberLeave",
                idKey to id.obfuscateId(),
                teamIdKey to teamId.obfuscateId(),
                timestampIsoKey to timestampIso,
                memberIdKey to memberId.obfuscateId(),
            )
        }
    }

    sealed class FeatureConfig(
        id: String,
    ) : Event(id) {
        data class FileSharingUpdated(
            override val id: String,
            val model: ConfigsStatusModel
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.FileSharingUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class MLSUpdated(
            override val id: String,
            val model: MLSModel
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.MLSUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "allowedUsers" to model.allowedUsers.map { it.value.obfuscateId() }
            )
        }

        data class MLSMigrationUpdated(
            override val id: String,
            val model: MLSMigrationModel
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.MLSUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "startTime" to model.startTime,
                "endTime" to model.endTime
            )
        }

        data class ClassifiedDomainsUpdated(
            override val id: String,
            val model: ClassifiedDomainsModel,
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.ClassifiedDomainsUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "domains" to model.config.domains.map { it.obfuscateDomain() }
            )
        }

        data class ConferenceCallingUpdated(
            override val id: String,
            val model: ConferenceCallingModel,
        ) : FeatureConfig(id) {
            override fun toLogMap() = mapOf(
                typeKey to "FeatureConfig.ConferenceCallingUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class GuestRoomLinkUpdated(
            override val id: String,
            val model: ConfigsStatusModel,
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.GuestRoomLinkUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class SelfDeletingMessagesConfig(
            override val id: String,
            val model: SelfDeletingMessagesModel,
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.SelfDeletingMessagesConfig",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                selfDeletionDurationKey to model.config.enforcedTimeoutSeconds
            )
        }

        data class MLSE2EIUpdated(
            override val id: String,
            val model: E2EIModel
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.MLSE2EIUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "config" to model.config
            )
        }

        data class AppLockUpdated(
            override val id: String,
            val model: AppLockModel
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.AppLockUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "timeout" to model.inactivityTimeoutSecs
            )
        }

        data class UnknownFeatureUpdated(
            override val id: String,
        ) : FeatureConfig(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.UnknownFeatureUpdated",
                idKey to id.obfuscateId(),
            )
        }
    }

    sealed class User(
        id: String,
    ) : Event(id) {

        data class Update(
            override val id: String,
            val userId: UserId,
            val accentId: Int?,
            val ssoIdDeleted: Boolean?,
            val name: String?,
            val handle: String?,
            val email: String?,
            val previewAssetId: String?,
            val completeAssetId: String?,
            val supportedProtocols: Set<SupportedProtocol>?
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.Update",
                idKey to id.obfuscateId(),
                userIdKey to userId.toLogString()
            )
        }

        data class NewConnection(
            override val id: String,
            val connection: Connection
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.NewConnection",
                idKey to id.obfuscateId(),
                "connection" to connection.toMap()
            )
        }

        data class ClientRemove(
            override val id: String,
            val clientId: ClientId
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.ClientRemove",
                idKey to id.obfuscateId(),
                clientIdKey to clientId.value.obfuscateId()
            )
        }

        data class UserDelete(
            override val id: String,
            val userId: UserId,
            val timestampIso: String = DateTimeUtil.currentIsoDateTimeString() // TODO we are not receiving it from API
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.UserDelete",
                idKey to id.obfuscateId(),
                userIdKey to "${userId.toLogString()}",
                timestampIsoKey to timestampIso
            )
        }

        data class NewClient(
            override val id: String,
            val client: Client,
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.NewClient",
                idKey to id.obfuscateId(),
                clientIdKey to client.id.value.obfuscateId(),
                "registrationTime" to client.registrationTime,
                "model" to (client.model ?: ""),
                "clientType" to client.type,
                "deviceType" to client.deviceType,
                "label" to (client.label ?: ""),
                "isMLSCapable" to client.isMLSCapable
            )
        }

        data class LegalHoldRequest(
            override val id: String,
            val clientId: ClientId,
            val lastPreKey: LastPreKey,
            val userId: UserId
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.LegalHold-request",
                idKey to id.obfuscateId(),
                "clientId" to clientId.value.obfuscateId(),
                "userId" to userId.toLogString(),
            )
        }

        data class LegalHoldEnabled(
            override val id: String,
            val userId: UserId
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.LegalHold-enabled",
                idKey to id.obfuscateId(),
                "userId" to userId.toLogString()
            )
        }

        data class LegalHoldDisabled(
            override val id: String,
            val userId: UserId
        ) : User(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.LegalHold-disabled",
                idKey to id.obfuscateId(),
                "userId" to userId.toLogString()
            )
        }
    }

    sealed class UserProperty(
        id: String,
    ) : Event(id) {

        data class ReadReceiptModeSet(
            override val id: String,
            val value: Boolean,
        ) : UserProperty(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.UserProperty.ReadReceiptModeSet",
                idKey to id.obfuscateId(),
                "value" to "$value"
            )
        }

        data class TypingIndicatorModeSet(
            override val id: String,
            val value: Boolean,
        ) : UserProperty(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.UserProperty.TypingIndicatorModeSet",
                idKey to id.obfuscateId(),
                "value" to "$value"
            )
        }
    }

    data class Unknown(
        override val id: String,
        val unknownType: String,
        val cause: String? = null
    ) : Event(id) {
        override fun toLogMap(): Map<String, Any?> = mapOf(
            typeKey to "User.UnknownEvent",
            idKey to id.obfuscateId(),
            "unknownType" to unknownType,
            "cause" to cause
        )
    }

    sealed class Federation(
        id: String,
    ) : Event(id) {

        data class Delete(
            override val id: String,
            val domain: String,
        ) : Federation(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Federation.Delete",
                idKey to id.obfuscateId(),
                "domain" to domain
            )
        }

        data class ConnectionRemoved(
            override val id: String,
            val domains: List<String>,
        ) : Federation(id) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Federation.ConnectionRemoved",
                idKey to id.obfuscateId(),
                "domains" to domains
            )
        }
    }
}

internal enum class EventLoggingStatus {
    SUCCESS,
    FAILURE,
    SKIPPED
}

/**
 * Logs event processing.
 * Underlying implementation detail is using the common [KaliumLogger.logStructuredJson] to log structured JSON.
 */
internal fun KaliumLogger.logEventProcessing(
    status: EventLoggingStatus,
    event: Event,
    vararg extraInfo: Pair<String, Any>
) {
    val logMap = mapOf("event" to event.toLogMap()) + extraInfo.toMap()

    when (status) {
        EventLoggingStatus.SUCCESS -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "success"
            logStructuredJson(KaliumLogLevel.INFO, "Success handling event", finalMap)
        }

        EventLoggingStatus.FAILURE -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "failure"
            logStructuredJson(KaliumLogLevel.ERROR, "Failure handling event", finalMap)
        }

        EventLoggingStatus.SKIPPED -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "skipped"
            logStructuredJson(KaliumLogLevel.WARN, "Skipped handling event", finalMap)
        }
    }
}
