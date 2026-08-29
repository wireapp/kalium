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
package com.wire.kalium.logic.sync.receiver.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SessionRefreshSuggestedEventHandlerTest {
    @Test
    fun givenSessionRefreshSuggestedEvent_thenCurrentSessionIsRefreshed() = runTest {
        var refreshCount = 0
        val handler = SessionRefreshSuggestedEventHandlerImpl {
            refreshCount++
            Either.Right(Unit)
        }

        val result = handler.handle(sessionRefreshSuggestedEvent(), LIVE_DELIVERY_INFO)

        assertIs<Either.Right<Unit>>(result)
        kotlin.test.assertEquals(1, refreshCount)
    }

    @Test
    fun givenSessionRefreshSuggestedEvent_whenRefreshFails_thenFailureIsPropagated() = runTest {
        val failure: CoreFailure = StorageFailure.Generic(Throwable("refresh failed"))
        val handler = SessionRefreshSuggestedEventHandlerImpl { Either.Left(failure) }

        val result = handler.handle(sessionRefreshSuggestedEvent(), LIVE_DELIVERY_INFO)

        assertIs<Either.Left<StorageFailure.Generic>>(result)
    }

    @Test
    fun givenPendingSessionRefreshSuggestedEvent_whenRefreshFails_thenEventIsSkipped() = runTest {
        val failure: CoreFailure = StorageFailure.Generic(Throwable("refresh failed"))
        val handler = SessionRefreshSuggestedEventHandlerImpl { Either.Left(failure) }

        val result = handler.handle(sessionRefreshSuggestedEvent(), PENDING_DELIVERY_INFO)

        assertIs<Either.Right<Unit>>(result)
    }
}
