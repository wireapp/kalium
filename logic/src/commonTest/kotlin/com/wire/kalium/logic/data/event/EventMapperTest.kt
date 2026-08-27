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
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.authenticated.conversation.ConversationRoleChange
import com.wire.kalium.network.api.authenticated.notification.AdminlessDeleteReminderData
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlinx.datetime.Instant
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    @Test
    fun givenSystemMemberUpdate_whenMapping_thenItMatchesRegularMemberUpdate() {
        val eventId = "event-id"
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val roleChange = ConversationRoleChange(
            user = TestConversation.NETWORK_USER_ID1.value,
            qualifiedUserId = TestConversation.NETWORK_USER_ID1,
            role = "wire_admin",
            mutedRef = null,
            mutedStatus = null,
            isArchiving = null,
            archivedRef = null,
        )
        val regularEvent = EventContentDTO.Conversation.MemberUpdateDTO(
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = TestUser.NETWORK_ID,
            time = TIMESTAMP,
            from = TestUser.NETWORK_ID.value,
            roleChange = roleChange,
        )
        val systemEvent = EventContentDTO.Conversation.SystemMemberUpdateDTO(
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = null,
            time = TIMESTAMP,
            from = "system",
            roleChange = roleChange,
        )

        val regularResult = assertIs<Event.Conversation.MemberChanged.MemberChangedRole>(
            mapper.fromEventContentDTO(eventId, regularEvent)
        )
        val systemResult = assertIs<Event.Conversation.MemberChanged.MemberChangedRole>(
            mapper.fromEventContentDTO(eventId, systemEvent)
        )

        assertEquals(regularResult.id, systemResult.id)
        assertEquals(regularResult.conversationId, systemResult.conversationId)
        assertEquals(regularResult.dateTime, systemResult.dateTime)
        assertEquals(regularResult.member, systemResult.member)
    }

    @Test
    fun givenSystemDeleteWithoutQualifiedFrom_whenMapping_thenSenderIsNull() {
        val eventId = "event-id"
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val event = EventContentDTO.Conversation.SystemDeletedConversationDTO(
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = null,
            time = TIMESTAMP,
        )

        val result = assertIs<Event.Conversation.DeletedConversation>(
            mapper.fromEventContentDTO(eventId, event)
        )

        assertEquals(eventId, result.id)
        assertEquals(event.qualifiedConversation.value, result.conversationId.value)
        assertEquals(event.qualifiedConversation.domain, result.conversationId.domain)
        assertEquals(TIMESTAMP, result.dateTime.toIsoDateTimeString())
        assertNull(result.senderUserId)
    }

    @Test
    fun givenAdminlessReminderEvents_whenMapping_thenSemanticFieldsArePreserved() {
        val eventId = "event-id"
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val data = AdminlessDeleteReminderData(DELETION_TIME)
        val regularEvent = EventContentDTO.Conversation.AdminlessDeleteReminderDTO(
            conversation = TestConversation.NETWORK_ID.value,
            data = data,
            from = TestUser.NETWORK_ID.value,
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = TestUser.NETWORK_ID,
            teamId = "team-id",
            time = EVENT_TIME,
            via = "user",
        )
        val systemEvent = EventContentDTO.Conversation.SystemAdminlessDeleteReminderDTO(
            conversation = TestConversation.NETWORK_ID.value,
            data = data,
            from = TestUser.NETWORK_ID.value,
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = null,
            teamId = "team-id",
            time = EVENT_TIME,
            via = "system",
        )

        val regularResult = assertIs<Event.Conversation.AdminlessDeleteReminder>(
            mapper.fromEventContentDTO(eventId, regularEvent)
        )
        val systemResult = assertIs<Event.Conversation.AdminlessDeleteReminder>(
            mapper.fromEventContentDTO(eventId, systemEvent)
        )

        assertEquals(eventId, regularResult.id)
        assertEquals(regularEvent.qualifiedConversation.value, regularResult.conversationId.value)
        assertEquals(regularEvent.qualifiedConversation.domain, regularResult.conversationId.domain)
        assertEquals(TestUser.USER_ID, regularResult.senderUserId)
        assertEquals(EVENT_TIME, regularResult.dateTime)
        assertEquals(DELETION_TIME, regularResult.deletionScheduledFor)
        assertEquals(regularResult.copy(senderUserId = null), systemResult)
    }

    private companion object {
        const val TIMESTAMP = "2026-07-30T10:00:00.000Z"
        val EVENT_TIME = Instant.parse("2026-07-16T12:00:00.000Z")
        val DELETION_TIME = Instant.parse("2026-07-20T12:00:00.000Z")
    }
}
