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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.network.exceptions.KaliumException.InvalidRequestError
import com.wire.kalium.network.exceptions.isMeetingNotFound

internal interface MeetingCreateEventHandler {
    suspend fun handle(event: Event.Meeting.Create): Either<CoreFailure, Unit>
}

internal class MeetingCreateEventHandlerImpl(
    private val meetingRepository: MeetingRepository,
) : MeetingCreateEventHandler {
    override suspend fun handle(event: Event.Meeting.Create): Either<CoreFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        return meetingRepository.fetchAndPersistMeeting(event.meetingId)
            .onSuccess { eventLogger.logSuccess() }
            .flatMapLeft { failure ->
                when {
                    failure is NetworkFailure.FeatureNotSupported -> {
                        eventLogger.logComplete(
                            status = EventLoggingStatus.SKIPPED,
                            extraInfo = arrayOf("info" to "Meetings feature not supported by current API version")
                        )
                        Either.Right(Unit)
                    }

                    failure is MeetingDataSource.MeetingNotSupportedFailure -> {
                        eventLogger.logComplete(
                            status = EventLoggingStatus.SKIPPED,
                            extraInfo = arrayOf("info" to "Meeting not supported by current API version")
                        )
                        Either.Right(Unit)
                    }

                    failure.isMeetingNotFound() -> {
                        eventLogger.logComplete(
                            status = EventLoggingStatus.SKIPPED,
                            extraInfo = arrayOf("info" to "Meeting not found on server")
                        )
                        Either.Right(Unit)
                    }

                    else -> {
                        eventLogger.logFailure(failure = failure)
                        Either.Left(failure)
                    }
                }
            }
            .map {}
    }
}

private fun CoreFailure.isMeetingNotFound(): Boolean =
    ((this as? NetworkFailure.ServerMiscommunication)?.kaliumException as? InvalidRequestError)?.isMeetingNotFound() == true
