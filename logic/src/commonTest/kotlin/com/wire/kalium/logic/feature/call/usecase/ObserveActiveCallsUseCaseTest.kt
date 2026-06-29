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

package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ObserveActiveCallsUseCaseTest {

    @Test
    fun givenAnEmptyCallList_whenInvoking_thenEmitsAnEmptyListOfCalls() = runTest {
        val (_, useCase) = Arrangement()
            .withActiveCallsFlow(flowOf(listOf()))
            .arrange()

        useCase().test {
            assertEquals(listOf(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAnActiveCall_whenInvoking_thenEmitsListWithThatActiveCall() = runTest {
        val (_, useCase) = Arrangement()
            .withActiveCallsFlow(flowOf(listOf(activeCall)))
            .arrange()

        useCase().test {
            assertEquals(listOf(activeCall), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAnEmptyCallList_whenInvoking_andActiveCallAppears_thenEmitsThatChange() = runTest {
        val activeCall = TestCall.oneOnOneEstablishedCall()
        val activeCallsFlow = MutableStateFlow<List<Call>>(listOf())
        val (_, useCase) = Arrangement()
            .withActiveCallsFlow(activeCallsFlow)
            .arrange()

        useCase().test {
            assertEquals(listOf(), awaitItem())

            activeCallsFlow.emit(listOf(activeCall))
            assertEquals(listOf(activeCall), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    inner class Arrangement {

        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to ObserveActiveCallsUseCaseImpl(callRepository)

        fun withActiveCallsFlow(calls: Flow<List<Call>>) = apply {
            everySuspend { callRepository.activeCallsFlow() } returns calls
        }
    }

    companion object {
        val activeCall = TestCall.oneOnOneEstablishedCall()
    }
}
