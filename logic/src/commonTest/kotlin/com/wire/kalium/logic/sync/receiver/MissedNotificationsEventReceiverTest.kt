/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissedNotificationsEventReceiverTest {

    @Test
    fun givenAMissedNotificationsEventsReceived_thenShouldTriggerFullSync() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, receiver) = Arrangement().arrange()

        receiver.onEvent(event = TestEvent.notificationsMissed(), deliveryInfo = TestEvent.liveDeliveryInfo)

        coVerify {
            advanceUntilIdle()
            arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            arrangement.slowSyncExecutorProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val slowSyncRepository = mock(SlowSyncRepository::class)
        val slowSyncExecutorProvider: suspend () -> Either.Right<Unit> = {
            Unit.right()
        }

        fun arrange() = this to MissedNotificationsEventReceiverImpl(
            slowSyncRequester = slowSyncExecutorProvider,
            slowSyncRepository = slowSyncRepository
        )
    }
}
