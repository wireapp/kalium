/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PendingMeetingAlertsTest {
    @Test
    fun replayAndAcceptanceAreScopedToTheQualifiedMeeting() = runTest {
        val self = QualifiedIDEntity("meeting-alert-owner", "example.com")
        val time = Instant.parse("2026-11-04T12:00:00Z")
        val meeting = MeetingEntity(
            QualifiedIDEntity("meeting", self.domain), QualifiedIDEntity("conversation", self.domain), self,
            time, null, "Planning", time + 1.hours, time + 2.hours, "UTC", null,
        )
        val other = meeting.copy(meetingId = QualifiedIDEntity("meeting", "other.example.com"))
        withTestUserDatabase(self) { database ->
            val db = database.builder
            db.writeMessageEffects {
                listOf(meeting, meeting, other).forEach {
                    recordMeetingAlert(Event.Meeting.MemberAdd("event", it.meetingId.toModel(), time, null), it)
                }
            }
            val pending = PendingMeetingAlerts(db) { Instant.DISTANT_FUTURE }
            assertEquals(2, pending.read().size)
            pending.acknowledge("event", meeting.meetingId.toModel())
            assertEquals(other.meetingId.toModel(), PendingMeetingAlerts(db).read().single().meetingId)
        }
    }
}
