/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IncrementalSyncRecoveryHandlerTest {

    @Test
    fun givenClientOrEventNotFoundFailure_whenRecovering_thenRestartSlowSyncProcess() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(NetworkFailure.ServerMiscommunication(TestNetworkException.notFound))

        // then
        with(arrangement) {
            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasInvoked(exactly = once)

            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenUnknownFailure_whenRecovering_thenRetryIncrementalSync() = runTest {
        // given
        val arrangement = Arrangement().arrange()

        // when
        arrangement.recoverWithFailure(
            CoreFailure.Unknown(IllegalStateException("Some illegal state exception"))
        )

        // then
        with(arrangement) {
            verify(onIncrementalSyncRetryCallback)
                .function(onIncrementalSyncRetryCallback::retry)
                .with()
                .wasInvoked(exactly = once)

            verify(restartSlowSyncProcessForRecoveryUseCase)
                .function(restartSlowSyncProcessForRecoveryUseCase::invoke)
                .with()
                .wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val onIncrementalSyncRetryCallback = mock(classOf<OnIncrementalSyncRetryCallback>())

        @Mock
        val restartSlowSyncProcessForRecoveryUseCase = mock(classOf<RestartSlowSyncProcessForRecoveryUseCase>())

        private val incrementalSyncRecoveryHandler by lazy {
            IncrementalSyncRecoveryHandlerImpl(restartSlowSyncProcessForRecoveryUseCase)
        }

        suspend fun recoverWithFailure(failure: CoreFailure) {
            incrementalSyncRecoveryHandler.recover(failure, onIncrementalSyncRetryCallback)
        }

        fun arrange() = this
    }

}
