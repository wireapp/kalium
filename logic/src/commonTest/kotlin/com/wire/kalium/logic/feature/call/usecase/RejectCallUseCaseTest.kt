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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RejectCallUseCaseTest {

        private val callManager = mock<CallManager>(mode = MockMode.autoUnit)

        private val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

    private lateinit var rejectCallUseCase: RejectCallUseCase

    @BeforeTest
    fun setup() {
        rejectCallUseCase = RejectCallUseCase(lazy { callManager }, callRepository, TestKaliumDispatcher)
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeRejectCallOnce() = runTest(TestKaliumDispatcher.main) {
        val conversationId = ConversationId("someone", "wire.com")

        everySuspend {
            callManager.rejectCall(eq(conversationId))
        } returns (Unit)

        everySuspend {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
        } returns (Unit)

        rejectCallUseCase.invoke(conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            callManager.rejectCall(eq(conversationId))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            callRepository.updateCallStatusById(eq(conversationId), eq(CallStatus.REJECTED))
        }
    }

}
