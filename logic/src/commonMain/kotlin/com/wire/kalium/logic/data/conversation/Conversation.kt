package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType

data class Conversation(
    val id: ConversationId,
    val name: String?,
    val type: Type,
    val teamId: TeamId?,
    val protocol: ProtocolInfo,
    val mutedStatus: MutedConversationStatus,
    val lastNotificationDate: String?,
    val lastModifiedDate: String?,
    val access: List<Access>,
    val accessRole: List<AccessRole>?
) {
    enum class Type { SELF, ONE_ON_ONE, GROUP, CONNECTION_PENDING }
    enum class AccessRole { TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE }
    enum class Access { PRIVATE, INVITE, LINK, CODE }
}

sealed class ProtocolInfo {
    object Proteus : ProtocolInfo()
    data class MLS(val groupId: String, val groupState: GroupState) : ProtocolInfo() {
        enum class GroupState { PENDING, PENDING_WELCOME_MESSAGE, ESTABLISHED }
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
    ) : ConversationDetails(conversation)

    data class Group(
        override val conversation: Conversation,
        val legalHoldStatus: LegalHoldStatus,
        val hasOngoingCall: Boolean = false
    ) : ConversationDetails(conversation)

    data class Connection(
        val conversationId: ConversationId,
        val otherUser: OtherUser?,
        val userType: UserType,
        val lastModifiedDate: String?,
        val connection: com.wire.kalium.logic.data.user.Connection,
        val protocolInfo: ProtocolInfo,
        val access: List<Conversation.Access>,
        val accessRole: List<Conversation.AccessRole>?
    ) : ConversationDetails(
        Conversation(
            id = conversationId,
            name = otherUser?.name,
            type = Conversation.Type.CONNECTION_PENDING,
            teamId = otherUser?.teamId,
            protocolInfo,
            mutedStatus = MutedConversationStatus.AllAllowed,
            lastNotificationDate = null,
            lastModifiedDate = lastModifiedDate,
            access = access,
            accessRole = accessRole
        )
    )
}

data class MembersInfo(val self: Member, val otherMembers: List<Member>)

data class Member(val id: UserId, val role: Role) {
    sealed class Role {
        object Member : Role()
        object Admin : Role()
        data class Unknown(val name: String) : Role()
    }
}

data class MemberDetails(val user: User, val role: Member.Role)

typealias ClientId = PlainId

data class Recipient(val id: UserId, val clients: List<ClientId>)
