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
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.network.api.authenticated.notification.AdminlessDeleteReminderData
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.authenticated.conversation.ConversationRoleChange
import com.wire.kalium.network.api.authenticated.conversation.ConversationNameUpdateEvent
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlin.time.Instant
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationAccessInfoDTO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.wire.kalium.logic.data.id.QualifiedID as ModelQualifiedID
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID

class EventMapperTest {

    @Test
    fun meetingConversationUsesTheExistingCreationEventAndKeepsItsGroupType() {
        val response = com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE.copy(
            conversationGroupType = com.wire.kalium.network.api.authenticated.conversation.ConversationResponse.GroupType.Meeting)
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.NewMeetingConversationDTO(response.id, TestUser.NETWORK_ID, timestamp, response)
        val event = assertIs<Event.Conversation.NewConversation>(mapper.fromEventContentDTO("meeting", dto))
        assertEquals("meeting", event.id)
        assertEquals(ModelQualifiedID(response.id.value, response.id.domain), event.conversationId)
        assertEquals(ModelQualifiedID(TestUser.NETWORK_ID.value, TestUser.NETWORK_ID.domain), event.senderUserId)
        assertEquals(timestamp, event.dateTime)
        assertEquals(response, event.conversation)
    }

    @Test
    fun metadataPayloadsHaveStableDistinctSystemIdentitiesAndKeepTheirQueueIdentity() {
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.ConversationRenameDTO(
            NetworkQualifiedID("conversation-id", "domain"), TestUser.NETWORK_ID,
            Instant.parse("2026-07-24T12:00:00.000Z"), ConversationNameUpdateEvent("Renamed")
        )
        val events = mapper.fromStoredDTO("notification", listOf(dto, dto), false).map {
            assertIs<Event.Conversation.RenamedConversation>(it.event)
        }
        assertEquals(listOf("notification", "notification"), events.map { it.id })
        assertEquals(listOf("notification", "notification:1"), events.map { it.systemMessageId })
        assertEquals(events, mapper.fromStoredDTO("notification", listOf(dto, dto), false).map { it.event })
    }

    @Test
    fun accessUpdatePreservesServerTimeAndKeepsMissingHistoricalTimeUnknown() {
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val date = Instant.parse("2001-01-01T00:00:00Z")
        val dto = EventContentDTO.Conversation.AccessUpdate(
            qualifiedConversation = TestConversation.ID.toApi(),
            qualifiedFrom = TestUser.NETWORK_ID,
            data = ConversationAccessInfoDTO(
                access = setOf(ConversationAccessDTO.INVITE),
                accessRole = setOf(ConversationAccessRoleDTO.SERVICE),
            ),
        )

        val legacyPayload = Json.encodeToJsonElement(dto).jsonObject
        val datedPayload = JsonObject(legacyPayload + ("time" to JsonPrimitive(date.toString())))
        val decoded = Json.decodeFromJsonElement<EventContentDTO.Conversation.AccessUpdate>(datedPayload)
        val event = assertIs<Event.Conversation.AccessUpdate>(mapper.fromEventContentDTO("access-event", decoded))
        assertEquals(date, event.dateTime)
        assertEquals(date, conversationAccessUpdate("access-event", decoded).dateTime)
        val decodedLegacy = Json.decodeFromJsonElement<EventContentDTO.Conversation.AccessUpdate>(legacyPayload)
        val legacy = assertIs<Event.Conversation.AccessUpdate>(mapper.fromEventContentDTO("legacy-event", decodedLegacy))
        assertNull(legacy.dateTime)
        assertNull(conversationAccessUpdate("legacy-event", decodedLegacy).dateTime)
    }

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
            qualifiedConversation = NetworkQualifiedID("conversation-id", "domain"),
            qualifiedFrom = TestUser.NETWORK_ID,
            time = timestamp.toString()
        )

        val result = mapper.fromEventContentDTO("event-id", dto)

        val event = assertIs<Event.Conversation.DeletedConversation>(result)
        assertEquals(timestamp, event.dateTime)
    }

    @Test
    fun givenDeletedMeetingConversationDTO_whenMapping_thenDeletedConversationEventIsReturned() {
        val eventId = "event-id"
        val conversationId = NetworkQualifiedID("conversation-id", "domain")
        val senderId = NetworkQualifiedID("sender-id", "domain")
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.DeletedMeetingConversationDTO(
            qualifiedConversation = conversationId,
            qualifiedFrom = senderId,
            time = timestamp.toString()
        )

        val result = mapper.fromEventContentDTO(eventId, dto)

        val event = assertIs<Event.Conversation.DeletedConversation>(result)
        assertEquals(eventId, event.id)
        assertEquals(ModelQualifiedID(conversationId.value, conversationId.domain), event.conversationId)
        assertEquals(ModelQualifiedID(senderId.value, senderId.domain), event.senderUserId)
        assertEquals(timestamp, event.dateTime)
    }

    @Test
    fun givenMeetingDeleteDTO_whenMapping_thenMeetingDeleteEventIsReturned() {
        val eventId = "event-id"
        val meetingId = NetworkQualifiedID("meeting-id", "domain")
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Meeting.MeetingDeleteDTO(
            qualifiedMeetingId = meetingId,
            time = timestamp
        )

        val result = mapper.fromEventContentDTO(eventId, dto)

        val event = assertIs<Event.Meeting.Delete>(result)
        assertEquals(eventId, event.id)
        assertEquals(ModelQualifiedID(meetingId.value, meetingId.domain), event.meetingId)
        assertEquals(timestamp, event.dateTime)
    }

    @Test
    fun givenMemberRoleUpdateDTO_whenMapping_thenTimestampIsMappedToInstant() {
        val timestamp = Instant.parse("2026-07-24T12:00:00.000Z")
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val dto = EventContentDTO.Conversation.MemberUpdateDTO(
            qualifiedConversation = NetworkQualifiedID("conversation-id", "domain"),
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
            qualifiedConversation = NetworkQualifiedID("conversation-id", "domain"),
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
            qualifiedConversation = NetworkQualifiedID("conversation-id", "domain"),
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
    fun systemMemberUpdateMapsTheAdminRoleWithoutASender() {
        val dto = EventContentDTO.Conversation.SystemMemberUpdateDTO(
            qualifiedConversation = TestConversation.NETWORK_ID, qualifiedFrom = null, time = TIMESTAMP,
            roleChange = ConversationRoleChange(
                user = TestConversation.NETWORK_USER_ID1.value, qualifiedUserId = TestConversation.NETWORK_USER_ID1,
                role = "wire_admin", mutedRef = null, mutedStatus = null, isArchiving = null, archivedRef = null,
            ),
        )
        val result = assertIs<Event.Conversation.MemberChanged.MemberChangedRole>(
            MapperProvider.eventMapper(TestUser.SELF.id).fromEventContentDTO("event-id", dto)
        )
        assertEquals(TestConversation.MEMBER_TEST1, result.member)
        assertEquals(ModelQualifiedID("valueConversation", "domainConversation"), result.conversationId)
        assertEquals(Instant.parse(TIMESTAMP), result.dateTime)
    }

    @Test
    fun systemDeletionMapsWithoutASender() {
        val dto = EventContentDTO.Conversation.SystemDeletedConversationDTO(TestConversation.NETWORK_ID, null, TIMESTAMP)
        assertEquals(
            Event.Conversation.DeletedConversation(
                "event-id", ModelQualifiedID("valueConversation", "domainConversation"), null, Instant.parse(TIMESTAMP),
            ),
            MapperProvider.eventMapper(TestUser.SELF.id).fromEventContentDTO("event-id", dto),
        )
    }

    @Test
    fun regularAndSystemRemindersMapTheScheduledTimeAndOptionalSender() {
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val data = AdminlessDeleteReminderData(DELETION_TIME)
        val regular = EventContentDTO.Conversation.AdminlessDeleteReminderDTO(
            conversation = TestConversation.NETWORK_ID.value, data = data, from = TestUser.NETWORK_ID.value,
            qualifiedConversation = TestConversation.NETWORK_ID, qualifiedFrom = TestUser.NETWORK_ID,
            time = EVENT_TIME, via = "user",
        )
        val system = EventContentDTO.Conversation.SystemAdminlessDeleteReminderDTO(
            conversation = TestConversation.NETWORK_ID.value, data = data,
            qualifiedConversation = TestConversation.NETWORK_ID, qualifiedFrom = null,
            time = EVENT_TIME, via = "system",
        )
        val expected = Event.Conversation.AdminlessDeleteReminder(
            "event-id", ModelQualifiedID("valueConversation", "domainConversation"), TestUser.USER_ID, EVENT_TIME, DELETION_TIME,
        )
        assertEquals(expected, mapper.fromEventContentDTO("event-id", regular))
        assertEquals(expected.copy(senderUserId = null), mapper.fromEventContentDTO("event-id", system))
    }

    @Test
    fun givenMeetingInviteWithOptionalSender_whenMapping_thenPreserveSender() {
        listOf(null, NetworkQualifiedID("inviter", "example.com")).forEach { sender ->
            val dto = EventContentDTO.Meeting.MeetingMemberAddDTO(
                qualifiedMeetingId = NetworkQualifiedID("meeting", "example.com"),
                time = Instant.parse("2026-09-23T12:00:00Z"),
                qualifiedFrom = sender,
            )
            val event = assertIs<Event.Meeting.MemberAdd>(MapperProvider.eventMapper(TestUser.SELF.id).fromEventContentDTO("invite", dto))
            assertEquals(sender?.let { ModelQualifiedID(it.value, it.domain) }, event.senderUserId)
        }
    }

    @Test
    fun givenAllMeetingEventsWithOptionalSender_whenMapping_thenPreserveSenderAndIdentity() {
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)
        val id = NetworkQualifiedID("meeting", "example.com")
        listOf(null, NetworkQualifiedID("sender", "example.com")).forEach { sender ->
            val events = listOf(
                EventContentDTO.Meeting.MeetingCreateDTO(id, EVENT_TIME, sender),
                EventContentDTO.Meeting.MeetingUpdateDTO(id, EVENT_TIME, sender),
                EventContentDTO.Meeting.MeetingDeleteDTO(id, EVENT_TIME, sender),
                EventContentDTO.Meeting.MeetingMemberAddDTO(id, EVENT_TIME, sender),
            )
            events.forEach { dto ->
                val event = mapper.fromEventContentDTO("event", dto)
                val actual = when (event) {
                    is Event.Meeting.Create -> event.senderUserId
                    is Event.Meeting.Update -> event.senderUserId
                    is Event.Meeting.Delete -> event.senderUserId
                    is Event.Meeting.MemberAdd -> event.senderUserId
                    else -> error("Unexpected event")
                }
                assertEquals("event", event.id)
                assertEquals(sender?.let { ModelQualifiedID(it.value, it.domain) }, actual)
            }
        }
    }

    private companion object {
        const val TIMESTAMP = "2026-07-30T10:00:00.000Z"
        val EVENT_TIME = Instant.parse("2026-07-16T12:00:00.000Z")
        val DELETION_TIME = Instant.parse("2026-07-20T12:00:00.000Z")
    }
}
