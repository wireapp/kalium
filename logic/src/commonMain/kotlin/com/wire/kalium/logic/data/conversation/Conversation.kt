package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId

typealias ConversationId = QualifiedID

data class Conversation(val id: ConversationId, val name: String?, val type: Type, val teamId: TeamId?) {
    enum class Type { SELF, ONE_ON_ONE, GROUP }
}

class MembersInfo(val self: Member, val otherMembers: List<Member>)

class Member(override val id: UserId) : User()

typealias ClientId = PlainId

data class Recipient(val member: Member, val clients: List<ClientId>)
