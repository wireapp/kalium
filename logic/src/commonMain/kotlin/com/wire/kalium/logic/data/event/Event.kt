package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.utils.toJsonElement
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull

sealed class Event(open val id: String, open val transient: Boolean) {

    sealed class Conversation(
        id: String, override val transient: Boolean, open val conversationId: ConversationId
    ) : Event(id, transient) {
        data class AccessUpdate(
            override val id: String,
            override val conversationId: ConversationId,
            val data: ConversationResponse,
            val qualifiedFrom: UserId,
            override val transient: Boolean
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "qualifiedFrom" to "${qualifiedFrom.value.obfuscateId()}@${qualifiedFrom.domain.obfuscateDomain()}"
                )
                return "${properties.toJsonElement()}"
            }
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
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "senderUserId" to "${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}",
                    "senderClientId" to senderClientId.value.obfuscateId(),
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class NewMLSMessage(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val senderUserId: UserId,
            val timestampIso: String,
            val content: String
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "senderUserId" to "${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class NewConversation(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val timestampIso: String,
            val conversation: ConversationResponse
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MemberJoin(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val addedBy: UserId,
            val members: List<Member>,
            val timestampIso: String
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "addedBy" to "${addedBy.value.obfuscateId()}@${addedBy.domain.obfuscateDomain()}",
                    "members" to members.map { it.toMap() },
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MemberLeave(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val removedBy: UserId,
            val removedList: List<UserId>,
            val timestampIso: String
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "removedBy" to "${removedBy.value.obfuscateId()}@${removedBy.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
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
                override fun toString(): String {
                    val properties = mapOf(
                        "id" to id.obfuscateId(),
                        "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                        "member" to (member?.toMap() ?: JsonNull),
                        "timestampIso" to timestampIso
                    )
                    return "${properties.toJsonElement()}"
                }
            }

            data class MemberMutedStatusChanged(
                override val id: String,
                override val conversationId: ConversationId,
                override val timestampIso: String,
                override val transient: Boolean,
                val mutedConversationStatus: MutedConversationStatus,
                val mutedConversationChangedTime: String
            ) : MemberChanged(id, conversationId, timestampIso, transient) {
                override fun toString(): String {
                    val properties = mapOf(
                        "id" to id.obfuscateId(),
                        "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                        "timestampIso" to timestampIso,
                        "mutedConversationStatus" to mutedConversationStatus.status,
                        "mutedConversationChangedTime" to mutedConversationChangedTime
                    )
                    return "${properties.toJsonElement()}"
                }
            }

            data class IgnoredMemberChanged(
                override val id: String,
                override val conversationId: ConversationId,
                override val transient: Boolean
            ) : MemberChanged(id, conversationId, "", transient) {
                override fun toString(): String {
                    val properties = mapOf(
                        "id" to id.obfuscateId(),
                        "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    )
                    return "${properties.toJsonElement()}"
                }
            }
        }

        data class MLSWelcome(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val senderUserId: UserId,
            val message: String,
            val timestampIso: String = Clock.System.now().toString()
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso,
                    "senderUserId" to "${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}"
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class DeletedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso,
                    "senderUserId" to "${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}"
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class RenamedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            override val transient: Boolean,
            val conversationName: String,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, transient, conversationId) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "conversationId" to "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}",
                    "senderUserId" to "${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}",
                    "conversationName" to conversationName,
                    "timestampIso" to timestampIso,
                )
                return "${properties.toJsonElement()}"
            }
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
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "teamId" to teamId,
                    "icon" to icon,
                    "name" to name,
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MemberJoin(
            override val id: String,
            override val teamId: String,
            override val transient: Boolean,
            val memberId: String,
        ) : Team(id, teamId, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "teamId" to teamId.obfuscateId(),
                    "memberId" to memberId.obfuscateId(),
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MemberLeave(
            override val id: String,
            override val transient: Boolean,
            override val teamId: String,
            val memberId: String,
            val timestampIso: String,
        ) : Team(id, teamId, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "teamId" to teamId.obfuscateId(),
                    "timestampIso" to timestampIso,
                    "memberId" to memberId.obfuscateId(),
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MemberUpdate(
            override val id: String,
            override val teamId: String,
            override val transient: Boolean,
            val memberId: String,
            val permissionCode: Int?,
        ) : Team(id, teamId, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "teamId" to teamId.obfuscateId(),
                    "permissionCode" to "$permissionCode",
                    "memberId" to memberId.obfuscateId(),
                )
                return "${properties.toJsonElement()}"
            }
        }

    }

    sealed class FeatureConfig(
        id: String,
        transient: Boolean,
    ) : Event(id, transient) {
        data class FileSharingUpdated(
            override val id: String, override val transient: Boolean, val model: ConfigsStatusModel
        ) : FeatureConfig(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "status" to model.status.name,
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class MLSUpdated(
            override val id: String, override val transient: Boolean, val model: MLSModel
        ) : FeatureConfig(id, transient) {
            override fun toString(): String {
                val properties = mapOf("id" to id.obfuscateId(),
                    "status" to model.status.name,
                    "allowedUsers" to model.allowedUsers.map { it.value.obfuscateId() })
                return "${properties.toJsonElement()}"
            }
        }

        data class ClassifiedDomainsUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ClassifiedDomainsModel,
        ) : FeatureConfig(id, transient) {
            override fun toString(): String {
                val properties = mapOf("id" to id.obfuscateId(),
                    "status" to model.status.name,
                    "domains" to model.config.domains.map { it.obfuscateDomain() })
                return "${properties.toJsonElement()}"
            }
        }

        data class ConferenceCallingUpdated(
            override val id: String,
            override val transient: Boolean,
            val model: ConferenceCallingModel,
        ) : FeatureConfig(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "status" to model.status.name,
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class UnknownFeatureUpdated(
            override val id: String,
            override val transient: Boolean,
        ) : FeatureConfig(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                )
                return "${properties.toJsonElement()}"
            }
        }
    }

    sealed class User(
        id: String, transient: Boolean
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
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(), "userId" to userId.obfuscateId()
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class NewConnection(
            override val transient: Boolean, override val id: String, val connection: Connection
        ) : User(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(), "connection" to connection.toMap()
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class ClientRemove(
            override val transient: Boolean, override val id: String, val clientId: ClientId
        ) : User(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(), "clientId" to clientId.value.obfuscateId()
                )
                return "${properties.toJsonElement()}"
            }
        }

        data class UserDelete(
            override val transient: Boolean,
            override val id: String,
            val userId: UserId,
            val timestampIso: String = Clock.System.now().toString() // TODO we are not receiving it from API
        ) : User(id, transient) {
            override fun toString(): String {
                val properties = mapOf(
                    "id" to id.obfuscateId(),
                    "userId" to "${userId.value.obfuscateId()}@${userId.domain.obfuscateDomain()}",
                    "timestampIso" to timestampIso
                )
                return "${properties.toJsonElement()}"
            }
        }
    }

    data class Unknown(
        override val id: String,
        override val transient: Boolean,
    ) : Event(id, transient) {
        override fun toString(): String {
            val properties = mapOf(
                "id" to id.obfuscateId(),
            )
            return "${properties.toJsonElement()}"
        }
    }
}
