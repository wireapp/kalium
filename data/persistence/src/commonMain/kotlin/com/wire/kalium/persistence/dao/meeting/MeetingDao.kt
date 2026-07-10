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
package com.wire.kalium.persistence.dao.meeting

import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

interface MeetingDao {
    suspend fun upsertMeetings(meetings: List<MeetingEntity>)
}

internal class MeetingDaoImpl(
    private val meetingsQueries: MeetingsQueries,
    private val writeDispatcher: WriteDispatcher,
) : MeetingDao {
    override suspend fun upsertMeetings(meetings: List<MeetingEntity>) {
        withContext(writeDispatcher.value) {
            meetingsQueries.transaction {
                meetings.forEach { meeting ->
                    meetingsQueries.upsertMeeting(
                        meeting_id = meeting.meetingId,
                        conversation_id = meeting.conversationId,
                        creator_id = meeting.creatorId,
                        creation_date = meeting.createdAt,
                        last_edit_date = meeting.updatedAt,
                        title = meeting.title,
                        start_date = meeting.startTime,
                        end_date = meeting.endTime,
                        trial = meeting.trial,
                        recurrence_frequency = meeting.recurrence?.frequency,
                        recurrence_interval = meeting.recurrence?.interval,
                        recurrence_end_date = meeting.recurrence?.until,
                    )
                }
            }
        }
    }
}
