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
package com.wire.kalium.logic.sync.periodic

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.sync.DefaultWorker
import com.wire.kalium.logic.sync.Result

/**
 *  Worker that is responsible for syncing meeting occurrences for recurring meetings.
 *  It removes outdated meetings and generates new occurrences to keep the UI window up to date.
 *  It's session scoped so the userId is implied from the scope.
 */
internal interface MeetingOccurrencesSyncWorker : DefaultWorker {
    override suspend fun doWork(): Result

    companion object Companion {
        const val NAME: String = "MeetingOccurrencesSyncWorker"
    }
}

internal class MeetingOccurrencesSyncWorkerImpl(
    private val meetingRepository: MeetingRepository,
    private val featureSupport: FeatureSupport,
) : MeetingOccurrencesSyncWorker {
    override suspend fun doWork(): Result = when (featureSupport.isMeetingsSupported) {
        false -> Result.Success
        true -> meetingRepository.syncMeetingOccurrences().fold(
            { Result.Failure },
            { Result.Success }
        )
    }
}
