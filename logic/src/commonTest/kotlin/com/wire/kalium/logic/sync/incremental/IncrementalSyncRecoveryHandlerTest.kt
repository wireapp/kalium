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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IncrementalSyncRecoveryHandlerTest {

    @Test
    fun givenClientOrEventNotFoundFailure_whenRecovering_thenClearLastEventIdAndRestartSlowSync() = runTest {
        val oldestEventId = "oldestEventId"
        // given
        val (arrangement, recoveryHandler) = arrange {
            withOldestEventIdReturning(Either.Right(oldestEventId))
            withClearLastEventIdReturning(Either.Right(Unit))
            withUpdateLastProcessedEventIdReturning(Either.Right(Unit))
        }

        var wasInvoked = false
        // when
        recoveryHandler.recover(CoreFailure.SyncEventOrClientNotFound) {
            wasInvoked = true
        }

        // then
        with(arrangement) {
            verify(eventRepository)
                .suspendFunction(eventRepository::clearLastProcessedEventId)
                .wasInvoked(exactly = once)

            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasInvoked(exactly = once)
        }
        assertTrue(wasInvoked)
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetryIncrementalSync() = runTest {
        // given
        val (arrangement, recoveryHandler) = arrange {
            withOldestEventIdReturning(Either.Right("oldestEventId"))
            withUpdateLastProcessedEventIdReturning(Either.Right(Unit))
        }

        var wasInvoked = false

        // when
        recoveryHandler.recover(CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))) {
            wasInvoked = true
        }

        // then
        with(arrangement) {
            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasNotInvoked()
        }
        assertTrue(wasInvoked)
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenShouldNotClearLastProcessedEventId() = runTest {
        // given
        val (arrangement, recoveryHandler) = arrange {
            withOldestEventIdReturning(Either.Right("oldestEventId"))
            withClearLastEventIdReturning(Either.Right(Unit))
            withUpdateLastProcessedEventIdReturning(Either.Right(Unit))
        }

        // when
        recoveryHandler.recover(CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))) {}

        // then
        with(arrangement) {
            verify(eventRepository)
                .suspendFunction(eventRepository::clearLastProcessedEventId)
                .wasNotInvoked()
        }
    }

    private class Arrangement(private val configure: Arrangement.() -> Unit) :
        EventRepositoryArrangement by EventRepositoryArrangementImpl() {

        @Mock
        val restartSlowSyncProcessForRecoveryUseCase = mock(classOf<RestartSlowSyncProcessForRecoveryUseCase>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(
                restartSlowSyncProcessForRecoveryUseCase,
                eventRepository
            )
        }

        fun arrange(): Pair<Arrangement, IncrementalSyncRecoveryHandler> = run {
            configure()
            this@Arrangement to incrementalSyncRecoveryHandler
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit = {}) = Arrangement(configure).arrange()
    }
}
