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

package com.wire.kalium.logic.framework

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.ktor.util.encodeBase64
import kotlinx.datetime.Instant

object TestEvent {

    fun memberJoin(eventId: String = "eventId", members: List<Member> = listOf()) = Event.Conversation.MemberJoin(
        eventId,
        TestConversation.ID,
        false,
        TestUser.USER_ID,
        members,
        "2022-03-30T15:36:00.000Z"
    )

    fun memberLeave(eventId: String = "eventId", members: List<Member> = listOf()) = Event.Conversation.MemberLeave(
        eventId,
        TestConversation.ID,
        false,
        TestUser.USER_ID,
        listOf(),
        "2022-03-30T15:36:00.000Z"
    )

    fun memberChange(eventId: String = "eventId", member: Member) = Event.Conversation.MemberChanged.MemberChangedRole(
        eventId,
        TestConversation.ID,
        "2022-03-30T15:36:00.000Z",
        false,
        member
    )

    fun memberChangeMutedStatus(eventId: String = "eventId") = Event.Conversation.MemberChanged.MemberMutedStatusChanged(
        eventId,
        TestConversation.ID,
        "2022-03-30T15:36:00.000Z",
        false,
        MutedConversationStatus.AllAllowed,
        "2022-03-30T15:36:00.000Zp"
    )

    fun memberChangeArchivedStatus(eventId: String = "eventId", isArchiving: Boolean = true) = Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
        eventId,
        TestConversation.ID,
        "2022-03-30T15:36:00.000Z",
        false,
        "2022-03-31T16:36:00.000Zp",
        isArchiving,
    )

    fun memberChangeIgnored(eventId: String = "eventId") = Event.Conversation.MemberChanged.IgnoredMemberChanged(
        eventId,
        TestConversation.ID,
        false,
    )

    fun clientRemove(eventId: String = "eventId", clientId: ClientId) = Event.User.ClientRemove(false, eventId, clientId)
    fun userDelete(eventId: String = "eventId", userId: UserId) = Event.User.UserDelete(false, eventId, userId)
    fun updateUser(eventId: String = "eventId", userId: UserId) = Event.User.Update(
        eventId,
        false, userId.toString(), null, false, "newName", null, null, null, null, null
    )

    fun newClient(eventId: String = "eventId", clientId: ClientId = ClientId("client")) = Event.User.NewClient(
        false, eventId, TestClient.CLIENT
    )

    fun newConnection(eventId: String = "eventId") = Event.User.NewConnection(
        false,
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
        false,
        TestUser.USER_ID,
        "2022-03-30T15:36:00.000Z"
    )

    fun renamedConversation(eventId: String = "eventId") = Event.Conversation.RenamedConversation(
        eventId,
        TestConversation.ID,
        false,
        "newName",
        TestUser.USER_ID,
        "2022-03-30T15:36:00.000Z"
    )

    fun receiptModeUpdate(eventId: String = "eventId") = Event.Conversation.ConversationReceiptMode(
        eventId,
        TestConversation.ID,
        false,
        receiptMode = Conversation.ReceiptMode.ENABLED,
        senderUserId = TestUser.USER_ID
    )

    fun teamUpdated(eventId: String = "eventId") = Event.Team.Update(
        eventId,
        teamId = "teamId",
        name = "teamName",
        transient = false,
        icon = "icon",
    )

    fun teamMemberJoin(eventId: String = "eventId") = Event.Team.MemberJoin(
        eventId,
        teamId = "teamId",
        false,
        memberId = "memberId"
    )

    fun teamMemberLeave(eventId: String = "eventId") = Event.Team.MemberLeave(
        eventId,
        teamId = "teamId",
        memberId = "memberId",
        timestampIso = "2022-03-30T15:36:00.000Z",
        transient = false
    )

    fun teamMemberUpdate(eventId: String = "eventId", permissionCode: Int) = Event.Team.MemberUpdate(
        eventId,
        teamId = "teamId",
        memberId = "memberId",
        permissionCode = permissionCode,
        transient = false
    )

    fun timerChanged(eventId: String = "eventId") = Event.Conversation.ConversationMessageTimer(
        id = eventId,
        conversationId = TestConversation.ID,
        transient = false,
        messageTimer = 3000,
        senderUserId = TestUser.USER_ID,
        timestampIso = "2022-03-30T15:36:00.000Z"
    )

    fun userPropertyReadReceiptMode(eventId: String = "eventId") = Event.UserProperty.ReadReceiptModeSet(
        id = eventId,
        transient = false,
        value = true
    )

    fun newMessageEvent(
        encryptedContent: String,
        senderUserId: UserId = TestUser.USER_ID,
        encryptedExternalContent: EncryptedData? = null
    ) = Event.Conversation.NewMessage(
        "eventId",
        TestConversation.ID,
        false,
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
        false,
        null,
        TestUser.USER_ID,
        timestamp.toIsoDateTimeString(),
        "content".encodeBase64(),
    )

    fun newConversationEvent() = Event.Conversation.NewConversation(
        id = "eventId",
        conversationId = TestConversation.ID,
        transient = false,
        timestampIso = "timestamp",
        conversation = TestConversation.CONVERSATION_RESPONSE,
        senderUserId = TestUser.SELF.id
    )

    fun newMLSWelcomeEvent() = Event.Conversation.MLSWelcome(
        "eventId",
        TestConversation.ID,
        false,
        TestUser.USER_ID,
        "dummy-message",
        timestampIso = "2022-03-30T15:36:00.000Z"
    )

    fun newAccessUpdateEvent() = Event.Conversation.AccessUpdate(
        id = "eventId",
        conversationId = TestConversation.ID,
        data = TestConversation.CONVERSATION_RESPONSE,
        qualifiedFrom = TestUser.USER_ID,
        transient = false
    )

    fun codeUpdated() = Event.Conversation.CodeUpdated(
        id = "eventId",
        conversationId = TestConversation.ID,
        transient = false,
        code = "code",
        key = "key",
        uri = "uri",
        isPasswordProtected = false
    )

    fun codeDeleted() = Event.Conversation.CodeDeleted(
        id = "eventId",
        conversationId = TestConversation.ID,
        transient = false,
    )

    fun typingIndicator(typingIndicatorMode: Conversation.TypingIndicatorMode) = Event.Conversation.TypingIndicator(
        id = "eventId",
        conversationId = TestConversation.ID,
        transient = true,
        senderUserId = TestUser.USER_ID,
        timestampIso = "2022-03-30T15:36:00.000Z",
        typingIndicatorMode = typingIndicatorMode
    )
}
