package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import kotlinx.datetime.Clock

sealed class Event(open val id: String) {

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
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "qualifiedFrom: ${qualifiedFrom.value.obfuscateId()}@${qualifiedFrom.domain.obfuscateDomain()} "
            }
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
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "senderUserId: ${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()} " +
                        "senderClientId:${senderClientId.value.obfuscateId()} " +
                        "timestampIso: $timestampIso"
            }
        }

        data class NewMLSMessage(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
            val content: String
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "senderUserId: ${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()} " +
                        "timestampIso: $timestampIso"
            }
        }

        data class NewConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val timestampIso: String,
            val conversation: ConversationResponse
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "timestampIso: $timestampIso"
            }
        }

        data class MemberJoin(
            override val id: String,
            override val conversationId: ConversationId,
            val addedBy: UserId,
            val members: List<Member>,
            val timestampIso: String
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "addedBy: ${addedBy.value.obfuscateId()}@${addedBy.domain.obfuscateDomain()} members:$members " +
                        "timestampIso: $timestampIso"
            }
        }

        data class MemberLeave(
            override val id: String,
            override val conversationId: ConversationId,
            val removedBy: UserId,
            val removedList: List<UserId>,
            val timestampIso: String
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "removedBy: ${removedBy.value.obfuscateId()}@${removedBy.domain.obfuscateDomain()}" +
                        "timestampIso: $timestampIso"
            }
        }

        open class MemberChanged(
            override val id: String,
            override val conversationId: ConversationId,
            val timestampIso: String,
            val member: Member?,
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "member: $member timestampIso: $timestampIso"
            }
        }

        data class IgnoredMemberChanged(
            override val id: String,
            override val conversationId: ConversationId
        ) : MemberChanged(id, conversationId, "", null)

        data class MLSWelcome(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val message: String,
            val timestampIso: String = Clock.System.now().toString()
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "timestampIso: $timestampIso " +
                        "senderUserId:${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}"
            }
        }

        data class DeletedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "timestampIso: $timestampIso " +
                        "senderUserId:${senderUserId.value.obfuscateId()}@${senderUserId.domain.obfuscateDomain()}"
            }
        }

        data class RenamedConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val conversationName: String,
            val senderUserId: UserId,
            val timestampIso: String,
        ) : Conversation(id, conversationId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "conversationId: ${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()} " +
                        "senderUserId: ${senderUserId.toString().obfuscateId()} " +
                        "timestampIso: $timestampIso " +
                        "conversationName: $conversationName}"
            }
        }
    }

    sealed class Team(
        id: String,
        open val teamId: String
    ) : Event(id) {
        data class Update(
            override val id: String,
            override val teamId: String,
            val icon: String,
            val name: String,
        ) : Team(id, teamId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "teamId: $teamId " +
                        "icon: $icon " +
                        "name: $name"
            }
        }

        data class MemberJoin(
            override val id: String,
            override val teamId: String,
            val memberId: String,
        ) : Team(id, teamId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "teamId: $teamId " +
                        "memberId: $memberId"
            }
        }

        data class MemberLeave(
            override val id: String,
            override val teamId: String,
            val memberId: String,
            val timestampIso: String,
            ) : Team(id, teamId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "teamId: $teamId " +
                        "timestampIso: $timestampIso " +
                        "memberId: $memberId"
            }
        }

        data class MemberUpdate(
            override val id: String,
            override val teamId: String,
            val memberId: String,
            val permissionCode: Int?,
        ) : Team(id, teamId) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} " +
                        "teamId: $teamId " +
                        "permissionCode: $permissionCode " +
                        "memberId: $memberId"
            }
        }

    }

    sealed class FeatureConfig(
        id: String,
    ) : Event(id) {
        data class FileSharingUpdated(
            override val id: String,
            val model: ConfigsStatusModel
        ) : FeatureConfig(id)

        data class MLSUpdated(
            override val id: String,
            val model: MLSModel
        ) : FeatureConfig(id)

        data class ClassifiedDomainsUpdated(
            override val id: String,
            val model: ClassifiedDomainsModel,
        ) : FeatureConfig(id)

        data class UnknownFeatureUpdated(
            override val id: String
        ) : FeatureConfig(id)
    }

    sealed class User(
        id: String,
    ) : Event(id) {

        data class Update(
            override val id: String,
            val userId: String,
            val accentId: Int?,
            val ssoIdDeleted: Boolean?,
            val name: String?,
            val handle: String?,
            val email: String?,
            val previewAssetId: String?,
            val completeAssetId: String?,
        ) : User(id) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} userId: ${userId.obfuscateId()}"
            }
        }

        data class NewConnection(
            override val id: String,
            val connection: Connection
        ) : User(id) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()}"
            }
        }

        data class ClientRemove(override val id: String, val clientId: ClientId) : User(id) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} clientId: ${clientId.value.obfuscateId()} "
            }
        }

        data class UserDelete(override val id: String, val userId: UserId) : User(id) {
            override fun toString(): String {
                return "id: ${id.obfuscateId()} userId: ${userId.value.obfuscateId()}@${userId.domain.obfuscateDomain()} "
            }
        }
    }

    data class Unknown(override val id: String) : Event(id)
}
