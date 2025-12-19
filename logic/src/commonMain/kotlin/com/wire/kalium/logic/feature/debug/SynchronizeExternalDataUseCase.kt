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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.sync.incremental.EventProcessor

internal fun interface SynchronizeExternalDataUseCase {

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

public sealed class SynchronizeExternalDataResult {
    public data object Success : SynchronizeExternalDataResult()
    public data class Failure(val coreFailure: CoreFailure) : SynchronizeExternalDataResult()
}

internal class SynchronizeExternalDataUseCaseImpl(
    val eventRepository: EventRepository,
    val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider
) : SynchronizeExternalDataUseCase {

    override suspend operator fun invoke(
        data: String,
    ): SynchronizeExternalDataResult {
        return transactionProvider.transaction("SynchronizeExternalData") { transactionContext ->
            eventRepository.parseExternalEvents(data).foldToEitherWhileRight(Unit) { event, _ ->
                eventProcessor.processEvent(transactionContext, event)
            }
        }
            .fold({
                return@fold SynchronizeExternalDataResult.Failure(it)
            }, {
                return@fold SynchronizeExternalDataResult.Success
            })
    }
}
