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

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant

data object MeetingMapper {
    @Suppress("LongParameterList", "LongMethod")
    fun fromViewToModel(
        meetingId: QualifiedIDEntity,
        conversationId: QualifiedIDEntity,
        creatorId: QualifiedIDEntity,
        creationDate: Instant,
        lastEditDate: Instant?,
        title: String,
        startDate: Instant,
        endDate: Instant?,
        trial: Boolean,
        recurrenceFrequency: MeetingEntity.RecurrenceEntity.Frequency?,
        recurrenceInterval: Long?,
        recurrenceEndDate: Instant?,
    ): MeetingEntity = MeetingEntity(
        meetingId = meetingId,
        conversationId = conversationId,
        creatorId = creatorId,
        createdAt = creationDate,
        updatedAt = lastEditDate ?: creationDate,
        title = title,
        startTime = startDate,
        endTime = endDate,
        trial = trial,
        recurrence = when {
            recurrenceFrequency != null && recurrenceInterval != null -> MeetingEntity.RecurrenceEntity(
                frequency = recurrenceFrequency,
                interval = recurrenceInterval,
                until = recurrenceEndDate
            )
            else -> null
        }
    )
}

