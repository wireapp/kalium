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
package com.wire.kalium.logic.sync.receiver.meeting

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant

internal fun meetingCreateEvent(): Event.Meeting.Create = Event.Meeting.Create(
    id = EVENT_ID,
    meetingId = MEETING_ID,
    dateTime = Instant.UNIX_FIRST_DATE,
)

internal fun meetingDeleteEvent(): Event.Meeting.Delete = Event.Meeting.Delete(
    id = EVENT_ID,
    meetingId = MEETING_ID,
    dateTime = Instant.UNIX_FIRST_DATE,
)

internal fun meetingUpdateEvent(): Event.Meeting.Update = Event.Meeting.Update(
    id = EVENT_ID,
    meetingId = MEETING_ID,
    dateTime = Instant.UNIX_FIRST_DATE,
)

internal fun meetingMemberAddEvent(): Event.Meeting.MemberAdd = Event.Meeting.MemberAdd(
    id = EVENT_ID,
    meetingId = MEETING_ID,
    dateTime = Instant.UNIX_FIRST_DATE,
)

private const val EVENT_ID = "eventId"
private val MEETING_ID = MeetingId("meetingId", "domain")
