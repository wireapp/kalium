package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.util.EPOCH_FIRST_DAY
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
    val access: List<Access>,
    val accessRole: List<AccessRole>
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

    val supportsUnreadMessageCount
        get() = type in setOf(Type.ONE_ON_ONE, Type.GROUP)

    sealed class ProtocolInfo {
        object Proteus : ProtocolInfo()
        data class MLS(
            val groupId: String,
            val groupState: GroupState,
            val epoch: ULong,
            val keyingMaterialLastUpdate: Instant
        ) : ProtocolInfo() {
            enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }
        }
    }
}

sealed class ConversationDetails(open val conversation: Conversation) {

    data class Self(override val conversation: Conversation) : ConversationDetails(conversation)

    data class OneOne(
        override val conversation: Conversation,
        val otherUser: OtherUser,
        val connectionState: ConnectionState,
        val legalHoldStatus: LegalHoldStatus,
        val userType: UserType,
        val unreadMessagesCount: Long,
        val lastUnreadMessage: Message?
    ) : ConversationDetails(conversation)

    data class Group(
        override val conversation: Conversation,
        val legalHoldStatus: LegalHoldStatus,
        val hasOngoingCall: Boolean = false,
        val unreadMessagesCount: Long,
        val lastUnreadMessage: Message?
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
            accessRole = accessRole
        )
    )
}

data class MembersInfo(val self: Member, val otherMembers: List<Member>)

data class Member(val id: UserId, val role: Role) { // TODO Kubaz rename to ConversationMember
    sealed class Role {
        object Member : Role()
        object Admin : Role()
        data class Unknown(val name: String) : Role()
    }
}

data class MemberDetails(val user: User, val role: Member.Role)

typealias ClientId = PlainId

data class Recipient(val id: UserId, val clients: List<ClientId>)
