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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnClientsRequestTest {

    @Test
    fun givenOngoingCall_whenClientsRequested_thenCallClientsInCallUpdater() = runTest {
        val (arrangement, callback) = Arrangement(backgroundScope).arrange()

        callback.onClientsRequest(inst = handle, conversationId = conversationId.toString(), arg = null)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationClientsInCallUpdater(conversationId)
        }
    }

    @Test
    fun givenOngoingCall_whenClientsRequested_thenCallEpochInfoUpdate() = runTest {
        val (arrangement, callback) = Arrangement(backgroundScope).arrange()

        callback.onClientsRequest(inst = handle, conversationId = conversationId.toString(), arg = null)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.epochInfoUpdater(conversationId)
        }
    }

    private class Arrangement(val testScope: CoroutineScope) {
        val conversationClientsInCallUpdater: ConversationClientsInCallUpdater = mock(MockMode.autoUnit)
        val epochInfoUpdater: EpochInfoUpdater = mock(MockMode.autoUnit)
        val qualifiedIdMapper = QualifiedIdMapper(TestUser.SELF.id)

        fun arrange() = this to OnClientsRequest(
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            epochInfoUpdater = epochInfoUpdater,
            qualifiedIdMapper = qualifiedIdMapper,
            callingScope = testScope,
        )
    }

    companion object {
        val handle = Handle(42)
        val conversationId = MockConversation.ID
    }
}
