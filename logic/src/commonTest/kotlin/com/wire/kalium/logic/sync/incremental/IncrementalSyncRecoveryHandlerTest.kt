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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangementImpl
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
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
            coVerify {
                eventRepository.clearLastProcessedEventId()
            }.wasInvoked(exactly = once)

            coVerify {
                restartSlowSyncProcessForRecoveryUseCase.invoke()
            }.wasInvoked(exactly = once)
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
            coVerify {
                restartSlowSyncProcessForRecoveryUseCase.invoke()
            }.wasNotInvoked()
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
            coVerify {
                eventRepository.clearLastProcessedEventId()
            }.wasNotInvoked()
        }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        EventRepositoryArrangement by EventRepositoryArrangementImpl() {
        val restartSlowSyncProcessForRecoveryUseCase = mock(RestartSlowSyncProcessForRecoveryUseCase::class)

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(
                restartSlowSyncProcessForRecoveryUseCase,
                eventRepository
            )
        }

        fun arrange(): Pair<Arrangement, IncrementalSyncRecoveryHandler> = run {
            runBlocking { configure() }
            this@Arrangement to incrementalSyncRecoveryHandler
        }
    }

    private companion object {
        fun arrange(configure: suspend Arrangement.() -> Unit = {}) = Arrangement(configure).arrange()
    }
}
