/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.meeting

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity.RecurrenceEntity
import com.wire.kalium.util.DateTimeUtil.asStartOfDay
import com.wire.kalium.util.DateTimeUtil.currentInstant
import kotlin.time.Duration.Companion.days

/** Unsupported recurrence retains the app's ignore policy. */
fun MeetingDTO.toMeetingEntity(): MeetingEntity? {
    val mappedRecurrence = recurrence?.toMeetingRecurrenceEntity()
    if (recurrence != null && mappedRecurrence == null) return null
    return MeetingEntity(
        meetingId = meetingId.toDao(),
        conversationId = conversationId.toDao(),
        creatorId = creatorId.toDao(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        startTime = startTime,
        endTime = endTime,
        tzid = tzid,
        recurrence = mappedRecurrence,
        type = MeetingEntity.Type.valueOf(type.name),
        link = link,
        hasPassword = hasPassword,
    )
}

fun MeetingRecurrenceDTO.toMeetingRecurrenceEntity(): RecurrenceEntity? {
    val mappedFrequency = when (frequency) {
        MeetingFrequencyDTO.DAILY -> Meeting.Recurrence.Frequency.DAILY
        MeetingFrequencyDTO.WEEKLY -> Meeting.Recurrence.Frequency.WEEKLY
        MeetingFrequencyDTO.MONTHLY, MeetingFrequencyDTO.YEARLY -> return null
    }
    return if (mappedFrequency to (interval ?: 1L) in Meeting.Recurrence.SUPPORTED_RECURRENCES) {
        RecurrenceEntity(RecurrenceEntity.Frequency.valueOf(mappedFrequency.name), interval, until)
    } else null
}

private const val OCCURRENCE_GENERATION_WINDOW_DAYS = 90
private const val OUTDATED_MEETING_RETENTION_DAYS = 30
fun occurrenceGenerationUntil() = currentInstant().asStartOfDay().plus((OCCURRENCE_GENERATION_WINDOW_DAYS + 1).days)
fun occurrenceOutdatedThreshold() = currentInstant().asStartOfDay().minus(OUTDATED_MEETING_RETENTION_DAYS.days)
