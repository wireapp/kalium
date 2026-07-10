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
    fun fromApiToDao(meeting: MeetingDTO): MeetingEntity
    fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity
    fun fromApiToDao(frequency: MeetingFrequencyDTO): RecurrenceEntity.Frequency
}

internal class MeetingMapperImpl(private val idMapper: IdMapper = MapperProvider.idMapper()) : MeetingMapper {
    override fun fromApiToDao(meeting: MeetingDTO): MeetingEntity = MeetingEntity(
        meetingId = idMapper.fromApiToDao(meeting.meetingId),
        conversationId = idMapper.fromApiToDao(meeting.conversationId),
        creatorId = idMapper.fromApiToDao(meeting.creatorId),
        createdAt = meeting.createdAt,
        updatedAt = meeting.updatedAt,
        title = meeting.title,
        startTime = meeting.startTime,
        endTime = meeting.endTime,
        trial = meeting.trial,
        recurrence = meeting.recurrence?.let { fromApiToDao(it) }
    )

    override fun fromApiToDao(recurrence: MeetingRecurrenceDTO): RecurrenceEntity = RecurrenceEntity(
        frequency = fromApiToDao(recurrence.frequency),
        interval = recurrence.interval,
        until = recurrence.until
    )

    override fun fromApiToDao(frequency: MeetingFrequencyDTO): RecurrenceEntity.Frequency = when (frequency) {
        MeetingFrequencyDTO.DAILY -> RecurrenceEntity.Frequency.DAILY
        MeetingFrequencyDTO.WEEKLY -> RecurrenceEntity.Frequency.WEEKLY
        MeetingFrequencyDTO.MONTHLY -> RecurrenceEntity.Frequency.MONTHLY
        MeetingFrequencyDTO.YEARLY -> RecurrenceEntity.Frequency.YEARLY
    }
}
