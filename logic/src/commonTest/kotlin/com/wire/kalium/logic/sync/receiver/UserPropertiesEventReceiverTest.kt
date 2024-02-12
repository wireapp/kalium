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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UserPropertiesEventReceiverTest {

    @Test
    fun givenReadReceiptsUpdateEvent_repositoryIsInvoked() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .arrange()

        eventReceiver.onEvent(event, TestEvent.liveDeliveryInfo)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setReadReceiptsStatus)
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val userPropertiesEventReceiver: UserPropertiesEventReceiver = UserPropertiesEventReceiverImpl(
            userConfigRepository = userConfigRepository
        )

        fun withUpdateReadReceiptsSuccess() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::setReadReceiptsStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to userPropertiesEventReceiver
    }
}
