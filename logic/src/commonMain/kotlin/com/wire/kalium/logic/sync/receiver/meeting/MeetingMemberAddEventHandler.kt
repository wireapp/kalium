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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlinx.coroutines.flow.firstOrNull

internal interface MeetingMemberAddEventHandler {
    suspend fun handle(event: Event.Meeting.MemberAdd): Either<CoreFailure, Unit>
}

internal class MeetingMemberAddEventHandlerImpl(
    private val meetingRepository: MeetingRepository,
    private val userRepository: UserRepository,
    private val notificationEventsManager: NotificationEventsManager,
) : MeetingMemberAddEventHandler {
    override suspend fun handle(event: Event.Meeting.MemberAdd): Either<CoreFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        return meetingRepository.handleMeetingFetchAndUpsert(meetingId = event.meetingId, eventLogger = eventLogger).map { meeting ->
            meeting?.let {
                notificationEventsManager.scheduleMeetingNotification(
                    LocalNotification.Meeting.Invite(
                        eventId = event.id,
                        meetingId = meeting.meetingId,
                        conversationId = meeting.conversationId,
                        meetingTitle = meeting.title,
                        author = event.senderUserId?.getNotificationAuthor(),
                        time = event.dateTime,
                        startTime = meeting.startTime,
                        endTime = meeting.endTime,
                    )
                )
            }
        }
    }

    private suspend fun UserId.getNotificationAuthor(): LocalNotificationMessageAuthor? =
        userRepository.observeUser(this).firstOrNull()?.let { user ->
            LocalNotificationMessageAuthor(user.name ?: "", user.previewPicture)
        }
}
