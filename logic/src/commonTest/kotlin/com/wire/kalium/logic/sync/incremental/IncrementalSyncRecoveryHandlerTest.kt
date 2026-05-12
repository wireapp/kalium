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
import com.wire.kalium.logic.util.arrangement.repository.EventRepositoryArrangementMokkeryImpl
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }

        var wasInvoked = false
        // when
        recoveryHandler.recover(CoreFailure.SyncEventOrClientNotFound) {
            wasInvoked = true
        }

        // then
        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                eventRepository.clearLastSavedEventId()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                restartSlowSyncProcessForRecoveryUseCase.invoke()
            }
        }
        assertTrue(wasInvoked)
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetryIncrementalSync() = runTest {
        // given
        val (arrangement, recoveryHandler) = arrange {
            withOldestEventIdReturning(Either.Right("oldestEventId"))
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }

        var wasInvoked = false

        // when
        recoveryHandler.recover(CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))) {
            wasInvoked = true
        }

        // then
        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                restartSlowSyncProcessForRecoveryUseCase.invoke()
            }
        }
        assertTrue(wasInvoked)
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenShouldNotClearLastSavedEventId() = runTest {
        // given
        val (arrangement, recoveryHandler) = arrange {
            withOldestEventIdReturning(Either.Right("oldestEventId"))
            withClearLastEventIdReturning(Either.Right(Unit))
            withUpdateLastSavedEventIdReturning(Either.Right(Unit))
        }

        // when
        recoveryHandler.recover(CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))) {}

        // then
        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                eventRepository.clearLastSavedEventId()
            }
        }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        EventRepositoryArrangement by EventRepositoryArrangementMokkeryImpl() {
        val restartSlowSyncProcessForRecoveryUseCase = mock<RestartSlowSyncProcessForRecoveryUseCase>(mode = MockMode.autoUnit)

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(
                restartSlowSyncProcessForRecoveryUseCase,
                eventRepository
            )
        }

        suspend fun arrange(): Pair<Arrangement, IncrementalSyncRecoveryHandler> = run {
            configure()
            this@Arrangement to incrementalSyncRecoveryHandler
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit = {}) = Arrangement(configure).arrange()
    }
}
