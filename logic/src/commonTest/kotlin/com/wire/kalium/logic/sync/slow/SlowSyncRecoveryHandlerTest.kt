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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SlowSyncRecoveryHandlerTest {

    @Test
    fun givenSelfUserDeletedFailure_whenRecovering_thenLogoutTheUser() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(SelfUserDeleted)

        with(arrangement) {
            verify(logoutUseCase)
                .suspendFunction(logoutUseCase::invoke)
                .with(matching { LogoutReason.DELETED_ACCOUNT == it })
                .wasInvoked(once)

            verify(onSlowSyncRetryCallback)
                .function(onSlowSyncRetryCallback::retry)
                .with()
                .wasNotInvoked()
        }
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetrySlowSync() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(
            CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))
        )

        with(arrangement) {
            verify(logoutUseCase)
                .suspendFunction(logoutUseCase::invoke)
                .with(any())
                .wasNotInvoked()

            verify(onSlowSyncRetryCallback)
                .function(onSlowSyncRetryCallback::retry)
                .with()
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {

        @Mock
        val onSlowSyncRetryCallback: OnSlowSyncRetryCallback = mock(classOf<OnSlowSyncRetryCallback>())

        @Mock
        val logoutUseCase = configure(mock(classOf<LogoutUseCase>())) { stubsUnitByDefault = true }

        private val slowSyncRecoveryHandler by lazy {
            SlowSyncRecoveryHandlerImpl(logoutUseCase)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            slowSyncRecoveryHandler.recover(failure, onSlowSyncRetryCallback)
        }

        fun arrange() = this
    }

}
