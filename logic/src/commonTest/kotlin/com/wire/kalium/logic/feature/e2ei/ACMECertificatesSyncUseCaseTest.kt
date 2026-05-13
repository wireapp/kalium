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
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ACMECertificatesSyncUseCaseTest {

    @Test
    fun givenWorkerExecuted_whenE2EIAndMLSAreEnabled_thenSyncIsCalled() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withFetchACMECertificates()
        }

        // when
        useCase()

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }
    }

    @Test
    fun givenWorkerExecuted_whenE2EIAndMLSAreDisabled_thenSyncIsNotCalled() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withE2EIEnabledAndMLSEnabled(false)
        }

        // when
        useCase()

        // then
        verifySuspend(VerifyMode.not) {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) {
        val e2eiRepository = mock<E2EIRepository>(mode = MockMode.autoUnit)
        val isE2EIEnabledUseCase = mock<IsE2EIEnabledUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange(): Pair<Arrangement, ACMECertificatesSyncUseCase> = run {
            configure()
            this@Arrangement to ACMECertificatesSyncUseCaseImpl(
                e2eiRepository = e2eiRepository,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase,
                kaliumLogger = kaliumLogger
            )
        }

        suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean) {
            everySuspend { isE2EIEnabledUseCase.invoke() } returns result
        }

        suspend fun withFetchACMECertificates() {
            everySuspend { e2eiRepository.fetchFederationCertificates() } returns Either.Right(Unit)
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
