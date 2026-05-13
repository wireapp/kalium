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
package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveStaleOpenCallsCleanupUseCaseTest {

    @Test
    fun givenCallRepositoryEmitsCleanupStates_whenInvokingUseCase_thenCleanupStatesAreEmitted() = runTest {
        val flow = MutableStateFlow(false)
        val (_, useCase) = Arrangement()
            .withObserveStaleOpenCallsCleanupDoneReturning(flow)
            .arrange()

        useCase().test {
            assertEquals(false, awaitItem())

            flow.emit(true)
            assertEquals(true, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {
        private val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

        fun withObserveStaleOpenCallsCleanupDoneReturning(cleanupDoneFlow: Flow<Boolean>) = apply {
            every {
                callRepository.observeStaleOpenCallsCleanupDone()
            } returns cleanupDoneFlow
        }

        fun arrange() = this to ObserveStaleOpenCallsCleanupUseCaseImpl(callRepository)
    }
}
