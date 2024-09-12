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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.sync.incremental.EventProcessor

fun interface SynchronizeExternalDataUseCase {

    /**
     * Consume event data coming from an external source.
     *
     * @param data NotificationResponse serialized to JSON
     * @return an [SynchronizeExternalDataResult] containing a [CoreFailure] in case anything goes wrong
     */
    suspend operator fun invoke(
        data: String,
    ): SynchronizeExternalDataResult

}

sealed class SynchronizeExternalDataResult {
    data object Success : SynchronizeExternalDataResult()
    data class Failure(val coreFailure: CoreFailure) : SynchronizeExternalDataResult()
}

internal class SynchronizeExternalDataUseCaseImpl(
    val eventRepository: EventRepository,
    val eventProcessor: EventProcessor
) : SynchronizeExternalDataUseCase {

    override suspend operator fun invoke(
        data: String,
    ): SynchronizeExternalDataResult {
        return eventRepository.parseExternalEvents(data).foldToEitherWhileRight(Unit) { event, _ ->
            eventProcessor.processEvent(event)
        }.fold({
            return@fold SynchronizeExternalDataResult.Failure(it)
        }, {
            return@fold SynchronizeExternalDataResult.Success
        })
    }
}
