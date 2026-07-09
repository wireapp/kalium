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

package com.wire.kalium.network.api.authenticated.meeting

import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.model.UserId
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingDTO(
    @SerialName("qualified_id") val meetingId: MeetingId,
    @SerialName("qualified_conversation") val conversationId: ConversationId,
    @SerialName("qualified_creator") val creatorId: UserId,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant?,
    @SerialName("title") val title: String,
    @SerialName("start_time") val startTime: Instant,
    @SerialName("end_time") val endTime: Instant?,
    @SerialName("trial") val trial: Boolean,
    @SerialName("recurrence") val recurrence: MeetingRecurrenceDTO?
)

@Serializable
data class MeetingRecurrenceDTO(
    @SerialName("frequency") val frequency: MeetingFrequencyDTO,
    @SerialName("interval") val interval: Long,
    @SerialName("until") val until: Instant?
)

@Serializable
enum class MeetingFrequencyDTO {
    @SerialName("daily")
    DAILY,

    @SerialName("weekly")
    WEEKLY,

    @SerialName("monthly")
    MONTHLY,

    @SerialName("yearly")
    YEARLY
}
