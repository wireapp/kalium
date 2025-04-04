/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.ktor.util.encodeBase64
import kotlinx.datetime.Instant

object TestEvent {

    fun memberJoin(eventId: String = "eventId", members: List<Member> = listOf()) = Event.Conversation.MemberJoin(
        eventId,
        TestConversation.ID,
        TestUser.USER_ID,
        members,
        Instant.UNIX_FIRST_DATE,
    )

    fun memberLeave(eventId: String = "eventId", members: List<UserId> = listOf()) = Event.Conversation.MemberLeave(
        eventId,
        TestConversation.ID,
        TestUser.USER_ID,
        members,
        Instant.UNIX_FIRST_DATE,
        reason = MemberLeaveReason.Left
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

    fun memberChangeArchivedStatus(eventId: String = "eventId", isArchiving: Boolean = true) =
        Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
            eventId,
            TestConversation.ID,
            "2022-03-30T15:36:00.000Z",
            "2022-03-31T16:36:00.000Zp",
            isArchiving,
        )

    fun memberChangeIgnored(eventId: String = "eventId") = Event.Conversation.MemberChanged.IgnoredMemberChanged(
        eventId,
        TestConversation.ID,
    )

    fun clientRemove(eventId: String = "eventId", clientId: ClientId) = Event.User.ClientRemove(eventId, clientId)
    fun userDelete(eventId: String = "eventId", userId: UserId) = Event.User.UserDelete(eventId, userId)
    fun updateUser(eventId: String = "eventId", userId: UserId) = Event.User.Update(
        id = eventId,
        userId = userId,
        accentId = null,
        ssoIdDeleted = false,
        name = "newName",
        handle = null,
        email = null,
        previewAssetId = null,
        completeAssetId = null,
        supportedProtocols = null
    )

    fun newClient(eventId: String = "eventId", clientId: ClientId = ClientId("client")) = Event.User.NewClient(
        eventId, TestClient.CLIENT
    )

    fun newConnection(eventId: String = "eventId", status: ConnectionState = ConnectionState.PENDING) = Event.User.NewConnection(
        eventId,
        Connection(
            conversationId = "conversationId",
            from = "from",
            lastUpdate = Instant.UNIX_FIRST_DATE,
            qualifiedConversationId = TestConversation.ID,
            qualifiedToId = TestUser.USER_ID,
            status = status,
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
        Instant.UNIX_FIRST_DATE,
    )

    fun receiptModeUpdate(eventId: String = "eventId") = Event.Conversation.ConversationReceiptMode(
        eventId,
        TestConversation.ID,
        receiptMode = Conversation.ReceiptMode.ENABLED,
        senderUserId = TestUser.USER_ID
    )

    fun accessUpdate(eventId: String = "eventId") = Event.Conversation.AccessUpdate(
        id = eventId,
        conversationId = TestConversation.ID,
        access = setOf(Conversation.Access.PRIVATE),
        accessRole = setOf(Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.SERVICE),
        qualifiedFrom = TestUser.USER_ID
    )

    fun teamMemberLeave(eventId: String = "eventId") = Event.Team.MemberLeave(
        eventId,
        teamId = "teamId",
        memberId = "memberId",
        Instant.UNIX_FIRST_DATE,
    )

    fun timerChanged(eventId: String = "eventId") = Event.Conversation.ConversationMessageTimer(
        id = eventId,
        conversationId = TestConversation.ID,
        messageTimer = 3000,
        senderUserId = TestUser.USER_ID,
        Instant.UNIX_FIRST_DATE,
    )

    fun userPropertyReadReceiptMode(eventId: String = "eventId") = Event.UserProperty.ReadReceiptModeSet(
        id = eventId,
        value = true
    )

    fun foldersUpdate(eventId: String = "eventId") = Event.UserProperty.FoldersUpdate(
        id = eventId,
        folders = listOf(
            FolderWithConversations(
                id = "folder1",
                name = "Favorites",
                type = FolderType.FAVORITE,
                conversationIdList = listOf(TestConversation.ID)
            )
        )
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
        Instant.UNIX_FIRST_DATE,
        encryptedContent,
        encryptedExternalContent
    )

    fun newMLSMessageEvent(
        dateTime: Instant,
        subConversationId: SubconversationId? = null
    ) = Event.Conversation.NewMLSMessage(
        "eventId",
        TestConversation.ID,
        subConversationId,
        TestUser.USER_ID,
        dateTime,
        "content".encodeBase64(),
    )

    fun newConversationEvent() = Event.Conversation.NewConversation(
        id = "eventId",
        conversationId = TestConversation.ID,
        dateTime = Instant.UNIX_FIRST_DATE,
        conversation = TestConversation.CONVERSATION_RESPONSE,
        senderUserId = TestUser.SELF.id
    )

    fun newMLSWelcomeEvent() = Event.Conversation.MLSWelcome(
        "eventId",
        TestConversation.ID,
        TestUser.USER_ID,
        "dummy-message",
        timestampIso = "2022-03-30T15:36:00.000Z"
    )

    fun codeUpdated() = Event.Conversation.CodeUpdated(
        id = "eventId",
        conversationId = TestConversation.ID,
        code = "code",
        key = "key",
        uri = "uri",
        isPasswordProtected = false
    )

    fun codeDeleted() = Event.Conversation.CodeDeleted(
        id = "eventId",
        conversationId = TestConversation.ID,
    )

    fun typingIndicator(typingIndicatorMode: Conversation.TypingIndicatorMode) = Event.Conversation.TypingIndicator(
        id = "eventId",
        conversationId = TestConversation.ID,
        senderUserId = TestUser.OTHER_USER_ID,
        timestampIso = "2022-03-30T15:36:00.000Z",
        typingIndicatorMode = typingIndicatorMode
    )

    fun newConversationProtocolEvent() = Event.Conversation.ConversationProtocol(
        id = "eventId",
        conversationId = TestConversation.ID,
        protocol = Conversation.Protocol.MIXED,
        senderUserId = TestUser.OTHER_USER_ID
    )

    fun newConversationChannelAddPermissionEvent() = Event.Conversation.ConversationChannelAddPermission(
        id = "eventId",
        conversationId = TestConversation.ID,
        channelAddPermission = ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS,
        senderUserId = TestUser.OTHER_USER_ID
    )

    fun newFeatureConfigEvent() = Event.FeatureConfig.AppLockUpdated(
        id = "eventId",
        model = AppLockModel(
            inactivityTimeoutSecs = 60,
            status = Status.ENABLED
        )
    )

    fun newUnknownFeatureUpdate() = Event.FeatureConfig.UnknownFeatureUpdated(
        id = "eventId",
    )

    fun Event.wrapInEnvelope(
        isTransient: Boolean = false,
        source: EventSource = EventSource.LIVE
    ): EventEnvelope {
        return EventEnvelope(this, EventDeliveryInfo.LegacyEventDeliveryInfo(isTransient, source))
    }

    val liveDeliveryInfo = EventDeliveryInfo.LegacyEventDeliveryInfo(false, EventSource.LIVE)
    val nonLiveDeliveryInfo = EventDeliveryInfo.LegacyEventDeliveryInfo(false, EventSource.PENDING)
}
