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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.E2EIRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.E2EIRepositoryArrangementImpl
import io.mockative.coVerify
import io.mockative.once
import kotlinx.coroutines.runBlocking
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
        coVerify {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.e2eiRepository.fetchFederationCertificates()
        }.wasNotInvoked()
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) : E2EIRepositoryArrangement by E2EIRepositoryArrangementImpl(),
        IsE2EIEnabledUseCaseArrangement by IsE2EIEnabledUseCaseArrangementImpl() {

        fun arrange(): Pair<Arrangement, ACMECertificatesSyncUseCase> = run {
            runBlocking { configure() }
            this@Arrangement to ACMECertificatesSyncUseCaseImpl(
                e2eiRepository = e2eiRepository,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase,
                kaliumLogger = kaliumLogger
            )
        }
    }

    private companion object {
        fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
