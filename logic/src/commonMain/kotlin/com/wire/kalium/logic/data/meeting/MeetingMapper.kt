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

package com.wire.kalium.logic.data.meeting

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.persistence.dao.meeting.MeetingEntity.RecurrenceEntity

internal interface MeetingMapper {
    fun fromApiToDao(meeting: MeetingDTO): MeetingEntity?
    fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity?
}

internal class MeetingMapperImpl(private val idMapper: IdMapper = MapperProvider.idMapper()) : MeetingMapper {
    override fun fromApiToDao(meeting: MeetingDTO): MeetingEntity? {
        val recurrence = meeting.recurrence?.let { fromApiToDao(it) }
        return if (meeting.recurrence != null && recurrence == null) {
            null // it means the recurrence is not supported, so the meeting is ignored
        } else {
            MeetingEntity(
                meetingId = idMapper.fromApiToDao(meeting.meetingId),
                conversationId = idMapper.fromApiToDao(meeting.conversationId),
                creatorId = idMapper.fromApiToDao(meeting.creatorId),
                createdAt = meeting.createdAt,
                updatedAt = meeting.updatedAt,
                title = meeting.title,
                startTime = meeting.startTime,
                endTime = meeting.endTime,
                trial = meeting.trial,
                recurrence = recurrence
            )
        }
    }

    override fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity? = recurrence.frequency.toDaoFrequency()
        ?.takeIf { recurrence.frequency to (recurrence.interval ?: 1) in SUPPORTED_RECURRENCES }
        ?.let { RecurrenceEntity(frequency = it, interval = recurrence.interval, until = recurrence.until) }

    private fun MeetingFrequencyDTO.toDaoFrequency(): RecurrenceEntity.Frequency? =
        when (this) {
            MeetingFrequencyDTO.DAILY -> RecurrenceEntity.Frequency.DAILY
            MeetingFrequencyDTO.WEEKLY -> RecurrenceEntity.Frequency.WEEKLY
            MeetingFrequencyDTO.MONTHLY,
            MeetingFrequencyDTO.YEARLY -> null
        }

    private companion object {
        val SUPPORTED_RECURRENCES = listOf(
            MeetingFrequencyDTO.DAILY to 1L, // daily
            MeetingFrequencyDTO.WEEKLY to 1L, // weekly
            MeetingFrequencyDTO.WEEKLY to 2L, // every 2 weeks
            MeetingFrequencyDTO.WEEKLY to 4L // every 4 weeks
        )
    }
}
