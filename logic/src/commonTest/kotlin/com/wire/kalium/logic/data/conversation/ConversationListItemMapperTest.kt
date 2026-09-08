/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessagePreviewContent
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListItemEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListPositionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListUserEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class ConversationListItemMapperTest {
    private val mapper = MapperProvider.conversationMapper(QualifiedID("self", "wire.com"))
    private val id = QualifiedIDEntity("conversation", "wire.com")
    private val user = ConversationListUserEntity(
        QualifiedIDEntity("other", "wire.com"), null, null, 7, UserAvailabilityStatusEntity.NONE,
        UserTypeEntity.STANDARD, ConnectionEntity.State.PENDING, false, false,
    )
    private val message = MessagePreviewEntity(
        "message", id, MessagePreviewEntityContent.Text("Sender", "Last message"),
        MessageEntity.Visibility.VISIBLE, false, user.id,
    )
    private val row = ConversationListItemEntity(
        ConversationListPositionEntity(Instant.fromEpochSeconds(10), id), ConversationEntity.Type.GROUP,
        "Group", ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED, ConversationEntity.LegalHoldStatus.DISABLED,
        null, true, null, 7, 3, 2, 4, 1, null, message,
    )

    @Test
    fun savedDraftTakesPrecedenceOverTheLastMessage() {
        val result = assertIs<ConversationListItem.RegularGroup>(mapper.fromListItemEntity(row.copy(draftText = "Unsent")))
        assertEquals(MessagePreviewContent.Draft("Unsent"), result.preview?.content)
        assertEquals(true, result.preview?.isSelfMessage)
        assertEquals(ConversationListUnreadState(7, 3, 2, 4, 1), result.unreadState)
        assertEquals(MutedConversationStatus.OnlyMentionsAndRepliesAllowed, result.mutedStatus)
        assertEquals(ConversationListPosition(row.position.activityTime, id.toModel()), result.position)
    }

    @Test
    fun lastMessageRetainsContentAndSenderDirection() {
        val result = assertIs<ConversationListItem.WithMessages>(mapper.fromListItemEntity(row))
        assertEquals(MessagePreviewContent.WithUser.Text("Sender", "Last message"), result.preview?.content)
        assertEquals(false, result.preview?.isSelfMessage)
        assertNull(assertIs<ConversationListItem.WithMessages>(mapper.fromListItemEntity(row.copy(lastMessage = null))).preview)
    }

    @Test
    fun channelWithoutAccessMetadataDefaultsToPrivate() {
        val privateChannel = assertIs<ConversationListItem.Channel>(mapper.fromListItemEntity(row.copy(type = ConversationEntity.Type.CHANNEL)))
        assertEquals(ConversationListChannelAccess.PRIVATE, privateChannel.access)
        val publicChannel = assertIs<ConversationListItem.Channel>(mapper.fromListItemEntity(row.copy(
            type = ConversationEntity.Type.CHANNEL, channelAccess = ConversationEntity.ChannelAccess.PUBLIC,
        )))
        assertEquals(ConversationListChannelAccess.PUBLIC, publicChannel.access)
        assertTrue(publicChannel.isSelfUserMember)
    }

    @Test
    fun oneOnOneRetainsUserDataAndPendingConnectionHasNoMessageFields() {
        val oneOnOne = assertIs<ConversationListItem.OneOnOne>(mapper.fromListItemEntity(row.copy(
            type = ConversationEntity.Type.ONE_ON_ONE, otherUser = user,
        )))
        assertEquals(user.id.toModel(), oneOnOne.otherUser.id)
        assertNull(oneOnOne.otherUser.name)
        assertEquals(7, oneOnOne.otherUser.accentId)
        val pending = assertIs<ConversationListItem.PendingConnection>(mapper.fromListItemEntity(row.copy(
            type = ConversationEntity.Type.CONNECTION_PENDING, otherUser = user,
        )))
        assertEquals(oneOnOne.otherUser, pending.otherUser)
    }
}
