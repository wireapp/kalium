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

package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.Conversation.ReceiptMode
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.serialization.json.JsonNull

sealed class Event(open val id: String, open val transient: Boolean) {

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

    open fun toLogMap(): Map<String, Any?> = mapOf(typeKey to "Event.Unknown")

    sealed class Conversation(
        id: String,
        override val transient: Boolean,
        open val conversationId: ConversationId
    ) : Event(id, transient) {
        data class AccessUpdate(
            override val id: String,
            override val conversationId: ConversationId,
            val data: ConversationResponse,
            val qualifiedFrom: UserId,
            override val transient: Boolean
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val senderUserId: UserId,
            val senderClientId: ClientId,
            val timestampIso: String,
            val content: String,
            val encryptedExternalContent: EncryptedData?
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val subconversationId: SubconversationId?,
            val senderUserId: UserId,
            val timestampIso: String,
            val content: String
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val senderUserId: UserId,
            val timestampIso: String,
            val conversation: ConversationResponse
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val addedBy: UserId,
            val members: List<Member>,
            val timestampIso: String
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val removedBy: UserId,
            val removedList: List<UserId>,
            val timestampIso: String
        ) : Conversation(id, transient, conversationId) {

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
            transient: Boolean,
        ) : Conversation(id, transient, conversationId) {
            class MemberChangedRole(
                override val id: String,
                override val conversationId: ConversationId,
                override val timestampIso: String,
                override val transient: Boolean,
                val member: Member?,
            ) : MemberChanged(id, conversationId, timestampIso, transient) {

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
                override val transient: Boolean,
                val mutedConversationStatus: MutedConversationStatus,
                val mutedConversationChangedTime: String
            ) : MemberChanged(id, conversationId, timestampIso, transient) {

                override fun toLogMap(): Map<String, Any?> = mapOf(
                    typeKey to "Conversation.MemberMutedStatusChanged",
                    idKey to id.obfuscateId(),
                    conversationIdKey to conversationId.toLogString(),
                    timestampIsoKey to timestampIso,
                    "mutedConversationStatus" to mutedConversationStatus.status,
                    "mutedConversationChangedTime" to mutedConversationChangedTime
                )
            }

            data class IgnoredMemberChanged(
                override val id: String,
                override val conversationId: ConversationId,
                override val transient: Boolean
            ) : MemberChanged(id, conversationId, "", transient) {

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
            override val transient: Boolean,
            val senderUserId: UserId,
            val message: String,
            val timestampIso: String = DateTimeUtil.currentIsoDateTimeString()
        ) : Conversation(id, transient, conversationId) {
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
            override val transient: Boolean,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val conversationName: String,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, transient, conversationId) {
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
            override val transient: Boolean,
            val receiptMode: ReceiptMode,
            val senderUserId: UserId
        ) : Conversation(id, transient, conversationId) {

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
            override val transient: Boolean,
            val messageTimer: Long?,
            val senderUserId: UserId,
            val timestampIso: String
        ) : Conversation(id, transient, conversationId) {

            override fun toLogMap() = mapOf(
                typeKey to "Conversation.ConversationMessageTimer",
                idKey to id.obfuscateId(),
                conversationIdKey to conversationId.toLogString(),
                "messageTime" to messageTimer,
                senderUserIdKey to senderUserId.toLogString(),
                timestampIsoKey to timestampIso
            )
        }
    }

    sealed class Team(
        id: String,
        open val teamId: String,
        transient: Boolean,
    ) : Event(id, transient) {
        data class Update(
            override val id: String,
            override val transient: Boolean,
            override val teamId: String,
            val icon: String,
            val name: String,
        ) : Team(id, teamId, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Team.Update",
                idKey to id.obfuscateId(),
                teamIdKey to teamId,
                "icon" to icon,
                "name" to name,
            )
        }

        data class MemberJoin(
            override val id: String,
            override val teamId: String,
            override val transient: Boolean,
            val memberId: String,
        ) : Team(id, teamId, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Team.MemberJoin",
                idKey to id.obfuscateId(),
                teamIdKey to teamId.obfuscateId(),
                memberIdKey to memberId.obfuscateId(),
            )
        }

        data class MemberLeave(
            override val id: String,
            override val transient: Boolean,
            override val teamId: String,
            val memberId: String,
            val timestampIso: String,
        ) : Team(id, teamId, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Team.MemberLeave",
                idKey to id.obfuscateId(),
                teamIdKey to teamId.obfuscateId(),
                timestampIsoKey to timestampIso,
                memberIdKey to memberId.obfuscateId(),
            )
        }

        data class MemberUpdate(
            override val id: String,
            override val teamId: String,
            override val transient: Boolean,
            val memberId: String,
            val permissionCode: Int?,
        ) : Team(id, teamId, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "Team.MemberUpdate",
                idKey to id.obfuscateId(),
                teamIdKey to teamId.obfuscateId(),
                "permissionCode" to "$permissionCode",
                memberIdKey to memberId.obfuscateId(),
            )
        }

    }

    sealed class FeatureConfig(
        id: String,
        transient: Boolean,
    ) : Event(id, transient) {
        data class FileSharingUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ConfigsStatusModel
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.FileSharingUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class MLSUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: MLSModel
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.MLSUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "allowedUsers" to model.allowedUsers.map { it.value.obfuscateId() }
            )
        }

        data class ClassifiedDomainsUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ClassifiedDomainsModel,
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.ClassifiedDomainsUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "domains" to model.config.domains.map { it.obfuscateDomain() }
            )
        }

        data class ConferenceCallingUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ConferenceCallingModel,
        ) : FeatureConfig(id, transient) {
            override fun toLogMap() = mapOf(
                typeKey to "FeatureConfig.ConferenceCallingUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class GuestRoomLinkUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ConfigsStatusModel,
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.GuestRoomLinkUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
            )
        }

        data class SelfDeletingMessagesConfig(
            override val id: String,
            override val transient: Boolean,
            val model: SelfDeletingMessagesModel,
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.SelfDeletingMessagesConfig",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                selfDeletionDurationKey to model.config.enforcedTimeoutSeconds
            )
        }

        data class MLSE2EIUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: E2EIModel
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.MLSE2EIUpdated",
                idKey to id.obfuscateId(),
                featureStatusKey to model.status.name,
                "config" to model.config
            )
        }

        data class UnknownFeatureUpdated(
            override val id: String,
            override val transient: Boolean,
        ) : FeatureConfig(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "FeatureConfig.UnknownFeatureUpdated",
                idKey to id.obfuscateId(),
            )
        }
    }

    sealed class User(
        id: String,
        transient: Boolean
    ) : Event(id, transient) {

        data class Update(
            override val id: String,
            override val transient: Boolean,
            val userId: String,
            val accentId: Int?,
            val ssoIdDeleted: Boolean?,
            val name: String?,
            val handle: String?,
            val email: String?,
            val previewAssetId: String?,
            val completeAssetId: String?,
        ) : User(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.Update",
                idKey to id.obfuscateId(),
                userIdKey to userId.obfuscateId()
            )
        }

        data class NewConnection(
            override val transient: Boolean,
            override val id: String,
            val connection: Connection
        ) : User(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.NewConnection",
                idKey to id.obfuscateId(),
                "connection" to connection.toMap()
            )
        }

        data class ClientRemove(
            override val transient: Boolean,
            override val id: String,
            val clientId: ClientId
        ) : User(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.ClientRemove",
                idKey to id.obfuscateId(),
                clientIdKey to clientId.value.obfuscateId()
            )
        }

        data class UserDelete(
            override val transient: Boolean,
            override val id: String,
            val userId: UserId,
            val timestampIso: String = DateTimeUtil.currentIsoDateTimeString() // TODO we are not receiving it from API
        ) : User(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.UserDelete",
                idKey to id.obfuscateId(),
                userIdKey to "${userId.toLogString()}",
                timestampIsoKey to timestampIso
            )
        }

        data class NewClient(
            override val transient: Boolean,
            override val id: String,
            val client: Client,
        ) : User(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.NewClient",
                idKey to id.obfuscateId(),
                clientIdKey to client.id.value.obfuscateId(),
                "registrationTime" to client.registrationTime,
                "model" to (client.model ?: ""),
                "clientType" to client.type,
                "deviceType" to client.deviceType,
                "label" to (client.label ?: "")
            )
        }
    }

    sealed class UserProperty(
        id: String,
        transient: Boolean
    ) : Event(id, transient) {

        data class ReadReceiptModeSet(
            override val id: String,
            override val transient: Boolean,
            val value: Boolean,
        ) : UserProperty(id, transient) {
            override fun toLogMap(): Map<String, Any?> = mapOf(
                typeKey to "User.UserProperty",
                idKey to id.obfuscateId(),
                "transient" to "$transient",
                "value" to "$value"
            )
        }
    }

    data class Unknown(
        override val id: String,
        override val transient: Boolean,
    ) : Event(id, transient) {
        override fun toLogMap(): Map<String, Any?> = mapOf(
            typeKey to "User.UnknownEvent",
            idKey to id.obfuscateId(),
        )
    }
}

internal enum class EventLoggingStatus {
    SUCCESS,
    FAILURE,
    SKIPPED
}

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
            val logJson = finalMap.toJsonElement()
            i("Success handling event: $logJson")
        }

        EventLoggingStatus.FAILURE -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "failure"
            val logJson = finalMap.toJsonElement()
            e("Failure handling event: $logJson")
        }

        EventLoggingStatus.SKIPPED -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "skipped"
            val logJson = finalMap.toJsonElement()
            w("Skipped handling event: $logJson")
        }

        else -> {
            val finalMap = logMap.toMutableMap()
            finalMap["outcome"] = "unknown"
            val logJson = finalMap.toJsonElement()
            w("Unknown outcome of event handling: $logJson")
        }
    }
}
