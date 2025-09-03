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

import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CrlRegistration
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RevocationListCheckerTest {

    @Test
    fun givenE2EIRepositoryReturnsFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositoryFailure()
                .arrange()

            val result = revocationListChecker.check(DUMMY_URL)

            result.shouldFail()
            coVerify {
                arrangement.certificateRevocationListRepository.getClientDomainCRL(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.coreCrypto.registerCrl(any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenCurrentClientIdProviderFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderFailure()
                .arrange()

            val result = revocationListChecker.check(DUMMY_URL)

            result.shouldFail()
            coVerify {
                arrangement.certificateRevocationListRepository.getClientDomainCRL(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.currentClientIdProvider.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.coreCrypto.registerCrl(any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMlsClientProviderFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderFailure()
                .arrange()

            val result = revocationListChecker.check(DUMMY_URL)

            result.shouldFail()
            coVerify {
                arrangement.currentClientIdProvider.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.mlsClientProvider.getCoreCrypto(eq(TestClient.CLIENT_ID))
            }.wasInvoked(once)

            coVerify {
                arrangement.coreCrypto.registerCrl(any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMlsClientProviderSuccess_whenRunningUseCase_thenDoNotRegisterCrlAndReturnExpiration() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderSuccess()
                .withRegisterCrl()
                .arrange()

            val result = revocationListChecker.check(DUMMY_URL)

            result.shouldSucceed {
                assertEquals(EXPIRATION, it)
            }
            coVerify {
                arrangement.currentClientIdProvider.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.mlsClientProvider.getCoreCrypto(eq(TestClient.CLIENT_ID))
            }.wasInvoked(once)

            coVerify {
                arrangement.coreCrypto.registerCrl(any(), any())
            }.wasInvoked(once)
        }

    @Test
    fun givenCertificatesRegistrationReturnsFlagIsChanged_whenRunningUseCase_thenUpdateConversationStates() =
        runTest {
            val (arrangement, revocationListChecker) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderSuccess()
                .withRegisterCrl()
                .withRegisterCrlFlagChanged()
                .arrange()

            val result = revocationListChecker.check(DUMMY_URL)

            result.shouldSucceed {
                assertEquals(EXPIRATION, it)
            }

            coVerify {
                arrangement.coreCrypto.registerCrl(any(), any())
            }.wasInvoked(once)
        }

    @Test
    fun givenE2EIAndMLSAreDisabled_whenRunningUseCase_thenE2EIFailureDisabledIsReturned() = runTest {
        // given
        val (arrangement, revocationListChecker) = Arrangement()
            .withE2EIEnabledAndMLSEnabled(false)
            .arrange()

        // when
        val result = revocationListChecker.check(DUMMY_URL)

        // then
        result.shouldFail {
            assertEquals(E2EIFailure.Disabled, it)
        }

        coVerify {
            arrangement.coreCrypto.registerCrl(any(), any())
        }.wasNotInvoked()
    }

    internal class Arrangement {

        @Mock
        val certificateRevocationListRepository = mock(CertificateRevocationListRepository::class)

        @Mock
        val coreCrypto = mock(CoreCryptoCentral::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun arrange() = this to RevocationListCheckerImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            currentClientIdProvider = currentClientIdProvider,
            mlsClientProvider = mlsClientProvider,
            featureSupport = featureSupport,
            userConfigRepository = userConfigRepository
        )

        suspend fun withE2EIRepositoryFailure() = apply {
            coEvery {
                certificateRevocationListRepository.getClientDomainCRL(any())
            }.returns(Either.Left(E2EIFailure.Generic(Exception())))
        }

        suspend fun withE2EIRepositorySuccess() = apply {
            coEvery {
                certificateRevocationListRepository.getClientDomainCRL(any())
            }.returns(Either.Right("result".toByteArray()))
        }

        suspend fun withCurrentClientIdProviderFailure() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        suspend fun withCurrentClientIdProviderSuccess() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withMlsClientProviderFailure() = apply {
            coEvery {
                mlsClientProvider.getCoreCrypto(any())
            }.returns(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        suspend fun withMlsClientProviderSuccess() = apply {
            coEvery {
                mlsClientProvider.getCoreCrypto(any())
            }.returns(Either.Right(coreCrypto))
        }

        suspend fun withRegisterCrl() = apply {
            coEvery {
                coreCrypto.registerCrl(any(), any())
            }.returns(CrlRegistration(false, EXPIRATION))
        }

        suspend fun withRegisterCrlFlagChanged() = apply {
            coEvery {
                coreCrypto.registerCrl(any(), any())
            }.returns(CrlRegistration(true, EXPIRATION))
        }

        suspend fun withE2EIEnabledAndMLSEnabled(result: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(result)

            coEvery {
                userConfigRepository.isMLSEnabled()
            }.returns(result.right())

            every {
                userConfigRepository.getE2EISettings()
            }.returns(E2EISettings(true, DUMMY_URL, DateTimeUtil.currentInstant(), false, null).right())
        }
    }

    companion object {
        private const val DUMMY_URL = "https://dummy.url"
        private val EXPIRATION = 10.toULong()
    }
}
