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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.notification.toLocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.util.createEventProcessingLogger
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.firstOrNull

internal interface MeetingDeleteEventHandler {
    suspend fun handle(event: Event.Meeting.Delete): Either<StorageFailure, Unit>
}

@Inject
internal class MeetingDeleteEventHandlerImpl(
    @UserSessionBinding("meetingRepository")
    private val meetingRepository: MeetingRepository,
    @UserSessionBinding("userRepository")
    private val userRepository: UserRepository,
    @UserSessionBinding("notificationEventsManager")
    private val notificationEventsManager: NotificationEventsManager,
) : MeetingDeleteEventHandler {
    override suspend fun handle(event: Event.Meeting.Delete): Either<StorageFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)

        val meeting = meetingRepository.getMeeting(event.meetingId).getOrNull()
        return meetingRepository.deleteMeetingLocally(event.meetingId)
            .onSuccess {
                meeting?.let {
                    notificationEventsManager.scheduleMeetingNotification(
                        LocalNotification.Meeting.Cancel(
                            eventId = event.id,
                            meetingId = meeting.meetingId,
                            conversationId = meeting.conversationId,
                            meetingTitle = meeting.title,
                            author = event.senderUserId?.let {
                                userRepository.observeUser(it).firstOrNull()?.toLocalNotificationMessageAuthor()
                            },
                            time = event.dateTime,
                        )
                    )
                }
                eventLogger.logSuccess()
            }
            .onFailure { eventLogger.logFailure(it) }
    }
}
