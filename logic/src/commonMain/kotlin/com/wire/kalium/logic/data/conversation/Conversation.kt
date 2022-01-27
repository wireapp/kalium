package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId

typealias ConversationId = QualifiedID

sealed class Conversation(val id: ConversationId, val membersInfo: MembersInfo, val name: String) {
    class Self(id: ConversationId, membersInfo: MembersInfo, name: String) : Conversation(id, membersInfo, name)

    class OneOne(
        id: ConversationId,
        membersInfo: MembersInfo,
        name: String,
        val connectionState: ConnectionState,
        val federationStatus: FederationStatus,
        val legalHoldStatus: LegalHoldStatus
    ) : Conversation(id, membersInfo, name) {
        enum class ConnectionState {
            // The other user has sent a connection request to this one
            INCOMING,

            // This user has sent a connection request to another user
            OUTGOING,

            // The connection is complete and the conversation is in its normal state
            ACCEPTED
        }

        enum class FederationStatus {
            NONE, GUEST, EXTERNAL
        }
    }

    class Group(id: ConversationId, membersInfo: MembersInfo, name: String) : Conversation(id, membersInfo, name)
}

class MembersInfo(val self: Member, val otherMembers: List<Member>)

class Member(override val id: UserId) : User

typealias ClientId = PlainId

data class Recipient(val member: Member, val clients: List<ClientId>)
