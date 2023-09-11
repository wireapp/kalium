/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock

internal interface EventRepositoryArrangement {
    val eventRepository: EventRepository

    fun withOldestEventIdReturning(result: Either<CoreFailure, String>)

    fun withClearLastEventIdReturning(result: Either<StorageFailure, Unit>)

    fun withFetchMostRecentEventReturning(result: Either<CoreFailure, String>)

    fun withLastProcessedEventIdReturning(result: Either<StorageFailure, String>)

    fun withUpdateLastProcessedEventIdReturning(result: Either<StorageFailure, Unit>)
}

internal class EventRepositoryArrangementImpl : EventRepositoryArrangement {
    @Mock
    override val eventRepository = mock(classOf<EventRepository>())

    override fun withOldestEventIdReturning(result: Either<CoreFailure, String>) {
        given(eventRepository)
            .suspendFunction(eventRepository::fetchOldestAvailableEventId)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withClearLastEventIdReturning(result: Either<StorageFailure, Unit>) {
        given(eventRepository)
            .suspendFunction(eventRepository::clearLastProcessedEventId)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withFetchMostRecentEventReturning(result: Either<CoreFailure, String>) {
        given(eventRepository)
            .suspendFunction(eventRepository::fetchMostRecentEventId)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withLastProcessedEventIdReturning(result: Either<StorageFailure, String>) {
        given(eventRepository)
            .suspendFunction(eventRepository::lastProcessedEventId)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withUpdateLastProcessedEventIdReturning(result: Either<StorageFailure, Unit>) {
        given(eventRepository)
            .suspendFunction(eventRepository::updateLastProcessedEventId)
            .whenInvokedWith(any())
            .thenReturn(result)
    }
}
