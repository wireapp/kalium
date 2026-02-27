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
import com.wire.kalium.logic.data.call.CallQuality
import com.wire.kalium.logic.data.call.CallQualityData
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveCallQualityDataUseCaseTest {

    @Test
    fun givenOngoingCallWithCallQualityDataUpdated_whenObservingCallQualityData_thenQualityDataIsEmitted() = runTest {
        // given
        val callQualityData = CallQualityData(CallQuality.POOR, 1,2,3)
        val (_, useCase) = Arrangement()
            .withObserveCallQualityDataReturning(flowOf(callQualityData))
            .arrange()
        // when
        useCase(ConversationId("conversation-id", "domain")).test {
            // then
            assertEquals(callQualityData, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenOngoingCallWithNoCallQualityDataUpdated_whenObservingCallQualityData_thenNoQualityDataIsEmitted() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withObserveCallQualityDataReturning(emptyFlow())
            .arrange()
        // when
        useCase(ConversationId("conversation-id", "domain")).test {
            // then
            awaitComplete()
        }
    }

    inner class Arrangement {
        internal val callRepository: CallRepository = mock(MockMode.autoUnit)

        fun withObserveCallQualityDataReturning(qualityDataFlow: Flow<CallQualityData>) = apply {
            every {
                callRepository.observeCallQualityData(any())
            } returns qualityDataFlow
        }

        internal fun arrange() = this to ObserveCallQualityDataUseCaseImpl(callRepository)
    }
}
