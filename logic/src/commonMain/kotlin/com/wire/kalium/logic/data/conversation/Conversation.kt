package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.util.EPOCH_FIRST_DAY
import com.wire.kalium.network.utils.toJsonElement
import kotlinx.datetime.Instant

data class Conversation(
    val id: ConversationId,
    val name: String?,
    val type: Type,
    val teamId: TeamId?,
    val protocol: ProtocolInfo,
    val mutedStatus: MutedConversationStatus,
    val removedBy: UserId?,
    val lastNotificationDate: String?,
    val lastModifiedDate: String?,
    val lastReadDate: String,
    val lastUnreadMessageDate: String,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val creatorId: String?
) {

    fun isTeamGroup(): Boolean = (teamId != null)

    fun isGuestAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.GUEST))
    }

    fun isNonTeamMemberAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.NON_TEAM_MEMBER))
    }

    fun isServicesAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.SERVICE))
    }

    enum class Type {
        SELF,
        ONE_ON_ONE,
        GROUP,
        CONNECTION_PENDING;
    }

    enum class AccessRole {
        TEAM_MEMBER,
        NON_TEAM_MEMBER,
        GUEST,
        SERVICE,
        EXTERNAL;
    }

    enum class Access {
        PRIVATE,
        INVITE,
        LINK,
        CODE;
    }

    @Suppress("MagicNumber")
    enum class CipherSuite(val tag: Int) {
        UNKNOWN(0),
        MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519(1),
        MLS_128_DHKEMP256_AES128GCM_SHA256_P256(2),
        MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519(3),
        MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448(4),
        MLS_256_DHKEMP521_AES256GCM_SHA512_P521(5),
        MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448(6),
        MLS_256_DHKEMP384_AES256GCM_SHA384_P384(7);

        companion object {
            fun fromTag(tag: Int): CipherSuite = values().first { type -> type.tag == tag }
        }
    }

    val supportsUnreadMessageCount
        get() = type in setOf(Type.ONE_ON_ONE, Type.GROUP)

    sealed class ProtocolInfo {
        object Proteus : ProtocolInfo() {
            override fun name() = "Proteus"
        }

        data class MLS(
            val groupId: GroupID,
            val groupState: GroupState,
            val epoch: ULong,
            val keyingMaterialLastUpdate: Instant,
            val cipherSuite: CipherSuite
        ) : ProtocolInfo() {
            enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

            override fun name() = "MLS"
        }

        abstract fun name(): String
    }

    data class Member(val id: UserId, val role: Role) {
        sealed class Role {
            object Member : Role()
            object Admin : Role()
            data class Unknown(val name: String) : Role()

            override fun toString(): String =
                when (this) {
                    is Member -> "member"
                    is Admin -> "admin"
                    is Unknown -> this.name
                }
        }

        override fun toString(): String {
            return "${this.toMap().toJsonElement()}"
        }

        fun toMap(): Map<String, String> = mapOf(
            "id" to "${id.value.obfuscateId()}@${id.domain.obfuscateDomain()}",
            "role" to "$role"
        )
    }

}

sealed class ConversationDetails(open val conversation: Conversation) {

    data class Self(override val conversation: Conversation) : ConversationDetails(conversation)

    data class OneOne(
        override val conversation: Conversation,
        val otherUser: OtherUser,
        val legalHoldStatus: LegalHoldStatus,
        val userType: UserType,
        val unreadMessagesCount: Int = 0,
        val unreadMentionsCount: Long = 0L,
        val unreadContentCount: UnreadContentCount,
        val lastMessage: Message?
    ) : ConversationDetails(conversation)

    data class Group(
        override val conversation: Conversation,
        val legalHoldStatus: LegalHoldStatus,
        val hasOngoingCall: Boolean = false,
        val unreadMessagesCount: Int = 0,
        val unreadMentionsCount: Long = 0L,
        val unreadContentCount: UnreadContentCount,
        val lastMessage: Message?,
        val isSelfUserMember: Boolean,
        val isSelfUserCreator: Boolean
    ) : ConversationDetails(conversation)

    data class Connection(
        val conversationId: ConversationId,
        val otherUser: OtherUser?,
        val userType: UserType,
        val lastModifiedDate: String,
        val connection: com.wire.kalium.logic.data.user.Connection,
        val protocolInfo: Conversation.ProtocolInfo,
        val access: List<Conversation.Access>,
        val accessRole: List<Conversation.AccessRole>
    ) : ConversationDetails(
        Conversation(
            id = conversationId,
            name = otherUser?.name,
            type = Conversation.Type.CONNECTION_PENDING,
            teamId = otherUser?.teamId,
            protocol = protocolInfo,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = EPOCH_FIRST_DAY,
            access = access,
            accessRole = accessRole,
            creatorId = null,
            lastUnreadMessageDate = EPOCH_FIRST_DAY
        )
    )
}

data class MembersInfo(val self: Conversation.Member, val otherMembers: List<Conversation.Member>)

data class MemberDetails(val user: User, val role: Conversation.Member.Role)

typealias ClientId = PlainId

data class Recipient(val id: UserId, val clients: List<ClientId>)
enum class UnreadContentType {
    TEXT_OR_ASSET, KNOCK, MISSED_CALL, UNKNOWN, SYSTEM
}

typealias UnreadContentCount = Map<UnreadContentType, Int>
