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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SyncCertificateRevocationListUseCaseTest {

    @Test
    fun givenSyncIsLiveAndE2EIIsEnabled_whenInvoked_thenCheckCredentialsOnce() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withE2EIEnabled(true)
            .withCheckCredentialsResult()
            .arrange()

        checkCrlWorker()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.checkCredentials()
        }
    }

    @Test
    fun givenSyncIsLiveAndE2EIIsDisabled_whenInvoked_thenDoNotCheckCredentials() = runTest {
        val (arrangement, checkCrlWorker) = Arrangement()
            .withIncrementalSyncState(flowOf(IncrementalSyncStatus.Live))
            .withE2EIEnabled(false)
            .arrange()

        checkCrlWorker()

        verifySuspend(VerifyMode.not) { arrangement.e2eiRepository.checkCredentials() }
    }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = mock(mode = MockMode.autoUnit)
        val e2eiRepository: E2EIRepository = mock(mode = MockMode.autoUnit)
        val isE2EIEnabledUseCase: IsE2EIEnabledUseCase = mock(mode = MockMode.autoUnit)

        fun arrange() = this to SyncCertificateRevocationListUseCaseImpl(
            incrementalSyncRepository,
            e2eiRepository,
            isE2EIEnabledUseCase,
            kaliumLogger
        )

        suspend fun withE2EIEnabled(enabled: Boolean) = apply {
            everySuspend { isE2EIEnabledUseCase() } returns enabled
        }

        suspend fun withCheckCredentialsResult() = apply {
            everySuspend { e2eiRepository.checkCredentials() } returns Either.Right(Unit)
        }

        fun withIncrementalSyncState(flow: Flow<IncrementalSyncStatus>) = apply {
            every { incrementalSyncRepository.incrementalSyncState }
                .returns(flow)
        }
    }
}
