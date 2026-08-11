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

package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.authenticated.conversation.ConversationRoleChange
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventMapperTest {

    @Test
    fun givenSessionRefreshSuggestedDTO_whenMapping_thenSessionRefreshSuggestedEventIsReturned() {
        val eventId = "event-id"
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)

        val result = mapper.fromEventContentDTO(eventId, EventContentDTO.User.SessionRefreshSuggestedDTO)

        val event = assertIs<Event.User.SessionRefreshSuggested>(result)
        assertEquals(eventId, event.id)
    }

    @Test
    fun givenDeletedConversationDTO_whenMapping_thenTimestampIsMappedToInstant() {
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.DeletedConversationDTO(
            qualifiedConversation = QualifiedID("conversation-id", "domain"),
            qualifiedFrom = TestUser.NETWORK_ID,
            time = timestamp.toString()
        )

        val result = mapper.fromEventContentDTO("event-id", dto)

        val event = assertIs<Event.Conversation.DeletedConversation>(result)
        assertEquals(timestamp, event.dateTime)
    }

    @Test
    fun givenMemberRoleUpdateDTO_whenMapping_thenTimestampIsMappedToInstant() {
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.MemberUpdateDTO(
            qualifiedConversation = QualifiedID("conversation-id", "domain"),
            qualifiedFrom = TestUser.NETWORK_ID,
            time = timestamp.toString(),
            from = TestUser.NETWORK_ID.value,
            roleChange = ConversationRoleChange(
                user = TestUser.NETWORK_ID.value,
                qualifiedUserId = TestUser.NETWORK_ID,
                role = "wire_admin",
                mutedRef = null,
                mutedStatus = null,
                isArchiving = null,
                archivedRef = null
            )
        )

        val result = mapper.fromEventContentDTO("event-id", dto)

        val event = assertIs<Event.Conversation.MemberChanged.MemberChangedRole>(result)
        assertEquals(timestamp, event.dateTime)
    }

    @Test
    fun givenMutedStatusUpdateDTO_whenMapping_thenMutedReferenceIsMappedToInstant() {
        val eventTimestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mutedReference = Instant.parse("2026-07-24T12:01:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.MemberUpdateDTO(
            qualifiedConversation = QualifiedID("conversation-id", "domain"),
            qualifiedFrom = TestUser.NETWORK_ID,
            time = eventTimestamp.toString(),
            from = TestUser.NETWORK_ID.value,
            roleChange = ConversationRoleChange(
                user = TestUser.NETWORK_ID.value,
                qualifiedUserId = TestUser.NETWORK_ID,
                role = null,
                mutedRef = mutedReference.toString(),
                mutedStatus = 3,
                isArchiving = null,
                archivedRef = null
            )
        )

        val result = mapper.fromEventContentDTO("event-id", dto)

        val event = assertIs<Event.Conversation.MemberChanged.MemberMutedStatusChanged>(result)
        assertEquals(mutedReference, event.mutedConversationChangedTime)
    }

    @Test
    fun givenArchivedStatusUpdateWithoutReference_whenMapping_thenEventTimestampIsUsed() {
        val eventTimestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.MemberUpdateDTO(
            qualifiedConversation = QualifiedID("conversation-id", "domain"),
            qualifiedFrom = TestUser.NETWORK_ID,
            time = eventTimestamp.toString(),
            from = TestUser.NETWORK_ID.value,
            roleChange = ConversationRoleChange(
                user = TestUser.NETWORK_ID.value,
                qualifiedUserId = TestUser.NETWORK_ID,
                role = null,
                mutedRef = null,
                mutedStatus = null,
                isArchiving = true,
                archivedRef = null
            )
        )

        val result = mapper.fromEventContentDTO("event-id", dto)

        val event = assertIs<Event.Conversation.MemberChanged.MemberArchivedStatusChanged>(result)
        assertEquals(eventTimestamp, event.archivedConversationChangedTime)
    }
}
