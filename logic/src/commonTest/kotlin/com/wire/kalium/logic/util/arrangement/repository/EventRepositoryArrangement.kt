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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

internal interface EventRepositoryArrangement {
    val eventRepository: EventRepository

    suspend fun withOldestEventIdReturning(result: Either<CoreFailure, String>)

    suspend fun withClearLastEventIdReturning(result: Either<StorageFailure, Unit>)

    suspend fun withFetchMostRecentEventReturning(result: Either<CoreFailure, String>)

    suspend fun withLastProcessedEventIdReturning(result: Either<StorageFailure, String>)

    suspend fun withUpdateLastProcessedEventIdReturning(result: Either<StorageFailure, Unit>)
}

internal class EventRepositoryArrangementImpl : EventRepositoryArrangement {
    @Mock
    override val eventRepository = mock(EventRepository::class)

    override suspend fun withOldestEventIdReturning(result: Either<CoreFailure, String>) {
        coEvery {
            eventRepository.fetchOldestAvailableEventId()
        }.returns(result)
    }

    override suspend fun withClearLastEventIdReturning(result: Either<StorageFailure, Unit>) {
        coEvery {
            eventRepository.clearLastProcessedEventId()
        }.returns(result)
    }

    override suspend fun withFetchMostRecentEventReturning(result: Either<CoreFailure, String>) {
        coEvery {
            eventRepository.fetchMostRecentEventId()
        }.returns(result)
    }

    override suspend fun withLastProcessedEventIdReturning(result: Either<StorageFailure, String>) {
        coEvery {
            eventRepository.lastProcessedEventId()
        }.returns(result)
    }

    override suspend fun withUpdateLastProcessedEventIdReturning(result: Either<StorageFailure, Unit>) {
        coEvery {
            eventRepository.updateLastProcessedEventId(any())
        }.returns(result)
    }
}
