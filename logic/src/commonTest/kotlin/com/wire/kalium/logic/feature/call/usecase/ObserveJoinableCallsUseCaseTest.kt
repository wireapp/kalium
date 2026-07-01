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
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ObserveJoinableCallsUseCaseTest {

    @Test
    fun givenJoinableCalls_whenInvokingObserveJoinableCallsUseCase_thenEmitsJoinableCalls() = runTest {
        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)
        val expectedCalls = mapOf(TestCall.CONVERSATION_ID to TestCall.groupIncomingCall(TestCall.CONVERSATION_ID))
        every {
            callRepository.joinableCallsByConversationIdFlow()
        } returns flowOf(expectedCalls)

        val result = ObserveJoinableCallsUseCaseImpl(callRepository)()

        result.test {
            assertEquals(expectedCalls, awaitItem())
            awaitComplete()
        }
    }
}
