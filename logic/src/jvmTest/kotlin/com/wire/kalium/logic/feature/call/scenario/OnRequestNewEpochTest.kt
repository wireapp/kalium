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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
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
class OnRequestNewEpochTest {

    @Test
    fun givenOngoingCall_whenNewEpochRequested_thenCallEpochInfoUpdate() = runTest {
        val (arrangement, callback) = Arrangement(backgroundScope).arrange()

        callback.onRequestNewEpoch(
            inst = handle,
            conversationId = conversationId.toString(),
            arg = null
        )
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.epochInfoUpdater(conversationId)
        }
    }

    inner class Arrangement(private val testScope: CoroutineScope) {
        internal val epochInfoUpdater: EpochInfoUpdater = mock(MockMode.autoUnit)
        internal val qualifiedIdMapper = QualifiedIdMapper(TestUser.SELF.id)


        internal fun arrange() = this to OnRequestNewEpoch(
            epochInfoUpdater = epochInfoUpdater,
            qualifiedIdMapper = qualifiedIdMapper,
            callingScope = testScope
        )
    }

    companion object {
        val handle = Handle(42)
        private val conversationId = ConversationId("conversationId", "wire.com")
    }
}
