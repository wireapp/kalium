package com.wire.kalium.logic.framework

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

object TestEvent {

    fun memberJoin(eventId: String = "eventId", members: List<Member> = listOf()) = Event.Conversation.MemberJoin(
        eventId,
        TestConversation.ID,
        TestUser.USER_ID,
        members,
        "2022-03-30T15:36:00.000Z"
    )

    fun memberChange(eventId: String = "eventId", member: Member) = Event.Conversation.MemberChanged.MemberChangedRole(
        eventId,
        TestConversation.ID,
        "2022-03-30T15:36:00.000Z",
        member
    )

    fun memberChangeMutedStatus(eventId: String = "eventId") = Event.Conversation.MemberChanged.MemberMutedStatusChanged(
        eventId,
        TestConversation.ID,
        "2022-03-30T15:36:00.000Z",
        MutedConversationStatus.AllAllowed,
        "2022-03-30T15:36:00.000Zp"
    )

    fun memberChangeIgnored(eventId: String = "eventId") = Event.Conversation.MemberChanged.IgnoredMemberChanged(
        eventId,
        TestConversation.ID,
    )

    fun clientRemove(eventId: String = "eventId", clientId: ClientId) = Event.User.ClientRemove(eventId, clientId)
    fun userDelete(eventId: String = "eventId", userId: UserId) = Event.User.UserDelete(eventId, userId)
    fun updateUser(eventId: String = "eventId", userId: UserId) = Event.User.Update(
        eventId, userId.toString(), null, false, "newName", null, null, null, null
    )

    fun newConnection(eventId: String = "eventId") = Event.User.NewConnection(
        eventId,
        Connection(
            conversationId = "conversationId",
            from = "from",
            lastUpdate = "lastUpdate",
            qualifiedConversationId = TestConversation.ID,
            qualifiedToId = TestUser.USER_ID,
            status = ConnectionState.PENDING,
            toId = "told?"
        )
    )

    fun deletedConversation(eventId: String = "eventId") = Event.Conversation.DeletedConversation(
        eventId,
        TestConversation.ID,
        TestUser.USER_ID,
        "2022-03-30T15:36:00.000Z"
    )

    fun renamedConversation(eventId: String = "eventId") = Event.Conversation.RenamedConversation(
        eventId,
        TestConversation.ID,
        "newName",
        TestUser.USER_ID,
        "2022-03-30T15:36:00.000Z"
    )

    fun teamUpdated(eventId: String = "eventId") = Event.Team.Update(
        eventId,
        teamId = "teamId",
        name = "teamName",
        icon = "icon",
    )

    fun teamMemberJoin(eventId: String = "eventId") = Event.Team.MemberJoin(
        eventId,
        teamId = "teamId",
        memberId = "memberId"
    )

    fun teamMemberLeave(eventId: String = "eventId") = Event.Team.MemberLeave(
        eventId,
        teamId = "teamId",
        memberId = "memberId",
        timestampIso = "2022-03-30T15:36:00.000Z"
    )

    fun teamMemberUpdate(eventId: String = "eventId", permissionCode: Int) = Event.Team.MemberUpdate(
        eventId,
        teamId = "teamId",
        memberId = "memberId",
        permissionCode = permissionCode
    )

    fun newMessageEvent(
        encryptedContent: String,
        senderUserId: UserId = TestUser.USER_ID,
        encryptedExternalContent: EncryptedData? = null
    ) = Event.Conversation.NewMessage(
        "eventId",
        TestConversation.ID,
        senderUserId,
        TestClient.CLIENT_ID,
        "time",
        encryptedContent,
        encryptedExternalContent
    )

    fun newMLSMessageEvent(
        timestamp: Instant
    ) = Event.Conversation.NewMLSMessage(
        "eventId",
        TestConversation.ID,
        TestUser.USER_ID,
        timestamp.toString(),
        "content"
    )
}
