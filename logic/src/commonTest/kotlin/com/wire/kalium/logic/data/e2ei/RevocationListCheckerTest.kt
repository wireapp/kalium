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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RevocationListCheckerTest {

    @Test
    fun givenCoreCryptoCredentialCheckFails_whenRunningUseCase_thenReturnFailure() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withCheckCredentialsFailure()
                .arrange()

            val result = revocationListChecker.check(arrangement.mlsContext, DUMMY_URL)

            result.shouldFail()
        }

    @Test
    fun givenCoreCryptoCredentialCheckSucceeds_whenRunningUseCase_thenReturnNoLegacyExpiration() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withCheckCredentialsSuccess()
                .arrange()

            val result = revocationListChecker.check(arrangement.mlsContext, DUMMY_URL)

            result.shouldSucceed {
                assertEquals(null, it)
            }

            verifySuspend {
                arrangement.mlsContext.checkCredentials()
            }
        }

    @Test
    fun givenE2EIAndMLSAreDisabled_whenRunningUseCase_thenE2EIFailureDisabledIsReturned() = runTest {
        // given
        val (arrangement, revocationListChecker) = Arrangement()
            .withE2EIEnabledAndMLSEnabled(false)
            .arrange()

        // when
        val result = revocationListChecker.check(arrangement.mlsContext, DUMMY_URL)

        // then
        result.shouldFail {
            assertEquals(E2EIFailure.Disabled, it)
        }

        verifySuspend(mode = VerifyMode.not) {
            arrangement.mlsContext.checkCredentials()
        }
    }

    internal class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val certificateRevocationListRepository = mock<CertificateRevocationListRepository>()
        val featureSupport = mock<FeatureSupport>()
        val userConfigRepository = mock<UserConfigRepository>()

        fun arrange() = this to RevocationListCheckerImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            featureSupport = featureSupport,
            userConfigRepository = userConfigRepository
        )

        suspend fun withCheckCredentialsSuccess() = apply {
            everySuspend { mlsContext.checkCredentials() } returns Unit
        }

        suspend fun withCheckCredentialsFailure() = apply {
            everySuspend { mlsContext.checkCredentials() } throws RuntimeException("check failed")
        }

        suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(result)

            everySuspend {
                userConfigRepository.isMLSEnabled()
            }.returns(result.right())

            everySuspend {
                userConfigRepository.getE2EISettings()
            }.returns(E2EISettings(true, DUMMY_URL, DateTimeUtil.currentInstant(), false, null).right())
        }
    }

    companion object {
        private const val DUMMY_URL = "https://dummy.url"
    }
}
