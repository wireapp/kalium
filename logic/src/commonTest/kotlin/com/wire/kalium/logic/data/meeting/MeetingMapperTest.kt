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
package com.wire.kalium.logic.data.meeting

import com.wire.kalium.network.api.authenticated.meeting.MeetingFrequencyDTO
import com.wire.kalium.network.api.authenticated.meeting.MeetingRecurrenceDTO
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MeetingMapperTest {
    private val mapper = MeetingMapperImpl()

    @Test
    fun givenSupportedRecurrences_whenMapping_thenReturnsRecurrenceEntity() {
        val givenCases = listOf(
            MeetingFrequencyDTO.DAILY to null,
            MeetingFrequencyDTO.DAILY to 1L,
            MeetingFrequencyDTO.WEEKLY to 1L,
            MeetingFrequencyDTO.WEEKLY to 2L,
            MeetingFrequencyDTO.WEEKLY to 4L,
        )
        val expectedCases = listOf(
            MeetingEntity.RecurrenceEntity.Frequency.DAILY to null,
            MeetingEntity.RecurrenceEntity.Frequency.DAILY to 1L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 1L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 2L,
            MeetingEntity.RecurrenceEntity.Frequency.WEEKLY to 4L,
        )
        givenCases.forEachIndexed { index, (frequency, interval) ->
            val result = mapper.fromApiToDao(MeetingRecurrenceDTO(frequency = frequency, interval = interval, until = null))
            assertNotNull(result)
            assertEquals(expectedCases[index].first, result.frequency)
        }
    }

    @Test
    fun givenUnsupportedRecurrences_whenMapping_thenReturnsNull() {
        val givenCases = listOf(
            MeetingRecurrenceDTO(MeetingFrequencyDTO.DAILY, 2, null),
            MeetingRecurrenceDTO(MeetingFrequencyDTO.WEEKLY, 3, null),
            MeetingRecurrenceDTO(MeetingFrequencyDTO.MONTHLY, 1, null),
            MeetingRecurrenceDTO(MeetingFrequencyDTO.YEARLY, 1, null)
        )
        givenCases.forEach { recurrence ->
            assertNull(mapper.fromApiToDao(recurrence))
        }
    }
}
