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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClearClientDataUseCaseTest {

    @Test
    fun givenBothProvidersSucceed_whenInvoked_thenBothLocalFilesAreCleared() = runTest {
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            .withProteusClientClearSuccess()
            .withMLSClientClearSuccess()
            .arrange()

        useCase()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.proteusClientProvider.clearLocalFiles() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsClientProvider.clearLocalFiles() }
    }

    @Test
    fun givenProteusClearFails_whenInvoked_thenFunctionCompletesWithoutThrowing() = runTest {
        val (_, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            .withProteusClientClearThrows()
            .arrange()
        useCase()
    }

    @Test
    fun givenMLSClearFails_whenInvoked_thenFunctionCompletesWithoutThrowing() = runTest {
        val (_, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            .withProteusClientClearSuccess()
            .withMLSClientClearThrows()
            .arrange()
        useCase()
    }

    private class Arrangement(private val dispatcher: KaliumDispatcher) {
        val proteusClientProvider: ProteusClientProvider = mock<ProteusClientProvider>(mode = MockMode.autoUnit)
        val mlsClientProvider: MLSClientProvider = mock<MLSClientProvider>(mode = MockMode.autoUnit)

        suspend fun withProteusClientClearSuccess() = apply {
            everySuspend { proteusClientProvider.clearLocalFiles() } returns (Unit)
        }

        suspend fun withMLSClientClearSuccess() = apply {
            everySuspend { mlsClientProvider.clearLocalFiles() } returns (Unit)
        }

        suspend fun withProteusClientClearThrows() = apply {
            everySuspend { proteusClientProvider.clearLocalFiles() } throws RuntimeException("proteus clear failed")
        }

        suspend fun withMLSClientClearThrows() = apply {
            everySuspend { mlsClientProvider.clearLocalFiles() } throws RuntimeException("mls clear failed")
        }

        fun arrange() = this to ClearClientDataUseCaseImpl(
            mlsClientProvider = mlsClientProvider,
            proteusClientProvider = proteusClientProvider,
            dispatchers = dispatcher
        )
    }
}
