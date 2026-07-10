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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.user.UserAssetId
import kotlinx.datetime.Instant

data class MeetingOccurrence(
    val occurrenceId: String,
    val meetingId: MeetingId,
    val conversationId: ConversationId,
    val conversationName: String,
    val conversationType: ConversationType,
    val title: String,
    val startTime: Instant,
    val endTime: Instant?,
    val recurrence: Recurrence?,
    val selfRole: SelfRole,
) {
    sealed interface ConversationType {
        data object Group : ConversationType
        data class Meeting(val previewPictures: List<UserAssetId>) : ConversationType
        data class Channel(val isPrivateChannel: Boolean) : ConversationType
        data class OneOnOne(val previewPicture: UserAssetId?) : ConversationType
    }

    data class Recurrence(
        val frequency: Frequency,
        val interval: Long,
        val until: Instant?
    ) {
        enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
    }

    enum class SelfRole { Creator, Member }
}
