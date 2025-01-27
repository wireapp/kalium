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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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
            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(exactly = once)

            verify(arrangement.coreCrypto)
                .suspendFunction(arrangement.coreCrypto::registerCrl)
                .with(any(), any())
                .wasNotInvoked()
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

            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(exactly = once)

            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

            verify(arrangement.coreCrypto)
                .suspendFunction(arrangement.coreCrypto::registerCrl)
                .with(any(), any())
                .wasNotInvoked()
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
            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

            verify(arrangement.mlsClientProvider)
                .suspendFunction(arrangement.mlsClientProvider::getCoreCrypto)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(exactly = once)

            verify(arrangement.coreCrypto)
                .suspendFunction(arrangement.coreCrypto::registerCrl)
                .with(any(), any())
                .wasNotInvoked()

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

            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(exactly = once)

            verify(arrangement.mlsClientProvider)
                .suspendFunction(arrangement.mlsClientProvider::getCoreCrypto)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(exactly = once)

            verify(arrangement.coreCrypto)
                .suspendFunction(arrangement.coreCrypto::registerCrl)
                .with(any(), any())
                .wasInvoked(exactly = once)
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

            verify(arrangement.coreCrypto)
                .suspendFunction(arrangement.coreCrypto::registerCrl)
                .with(any(), any())
                .wasInvoked(exactly = once)
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

        verify(arrangement.coreCrypto)
            .suspendFunction(arrangement.coreCrypto::registerCrl)
            .with(any(), any())
            .wasNotInvoked()
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

        fun withE2EIRepositoryFailure() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getClientDomainCRL)
                .whenInvokedWith(any())
                .then { Either.Left(E2EIFailure.Generic(Exception())) }
        }

        fun withE2EIRepositorySuccess() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getClientDomainCRL)
                .whenInvokedWith(any())
                .then { Either.Right("result".toByteArray()) }
        }

        fun withCurrentClientIdProviderFailure() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .then { Either.Left(CoreFailure.SyncEventOrClientNotFound) }
        }

        fun withCurrentClientIdProviderSuccess() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .then { Either.Right(TestClient.CLIENT_ID) }
        }

        fun withMlsClientProviderFailure() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getCoreCrypto)
                .whenInvokedWith(any())
                .then { Either.Left(CoreFailure.SyncEventOrClientNotFound) }
        }

        fun withMlsClientProviderSuccess() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getCoreCrypto)
                .whenInvokedWith(any())
                .then { Either.Right(coreCrypto) }
        }

        fun withRegisterCrl() = apply {
            given(coreCrypto)
                .suspendFunction(coreCrypto::registerCrl)
                .whenInvokedWith(any(), any())
                .thenReturn(CrlRegistration(true, EXPIRATION))
        }

        fun withRegisterCrlFlagChanged() = apply {
            given(coreCrypto)
                .suspendFunction(coreCrypto::registerCrl)
                .whenInvokedWith(any(), any())
                .thenReturn(CrlRegistration(false, EXPIRATION))
        }

        fun withE2EIEnabledAndMLSEnabled(result: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(result)
            given(userConfigRepository)
                .function(userConfigRepository::isMLSEnabled)
                .whenInvoked()
                .thenReturn(result.right())
            given(userConfigRepository)
                .function(userConfigRepository::getE2EISettings)
                .whenInvoked()
                .thenReturn(E2EISettings(result, DUMMY_URL, DateTimeUtil.currentInstant(), false, null).right())
        }
    }

    companion object {
        private const val DUMMY_URL = "https://dummy.url"
        private val EXPIRATION = 10.toULong()
    }
}
