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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.cryptography.CrlRegistration
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandler
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckRevocationListUseCaseTest {

    @Test
    fun givenE2EIRepositoryReturnsFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositoryFailure()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldFail()
            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenCurrentClientIdProviderFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderFailure()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldFail()
            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenMlsClientProviderFailure_whenRunningUseCase_thenDoNotRegisterCrlAndReturnFailure() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderFailure()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldFail()
            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(once)

            verify(arrangement.mlsClientProvider)
                .suspendFunction(arrangement.mlsClientProvider::getMLSClient)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenMlsClientProviderSuccess_whenRunningUseCase_thenDoNotRegisterCrlAndReturnExpiration() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderSuccess()
                .withRegisterCrl()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldSucceed {
                assertEquals(EXPIRATION, it)
            }
            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(once)

            verify(arrangement.mlsClientProvider)
                .suspendFunction(arrangement.mlsClientProvider::getMLSClient)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasInvoked(once)
        }

    @Test
    fun givenCertificatesRegistrationReturnsFlagIsChanged_whenRunningUseCase_thenUpdateConversationStates() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIEnabledAndMLSEnabled(true)
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderSuccess()
                .withMlsClientProviderSuccess()
                .withRegisterCrl()
                .withRegisterCrlFlagChanged()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldSucceed {
                assertEquals(EXPIRATION, it)
            }

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.mLSConversationsVerificationStatusesHandler)
                .suspendFunction(arrangement.mLSConversationsVerificationStatusesHandler::invoke)
                .wasInvoked(once)
        }

    internal class Arrangement {

        @Mock
        val certificateRevocationListRepository = mock(classOf<CertificateRevocationListRepository>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val mLSConversationsVerificationStatusesHandler =
            mock(classOf<MLSConversationsVerificationStatusesHandler>())

        @Mock
        val currentClientIdProvider =
            mock(classOf<CurrentClientIdProvider>())

        @Mock
        val mlsClientProvider =
            mock(classOf<MLSClientProvider>())

        @Mock
        val isE2EIEnabledUseCase = mock(classOf<IsE2EIEnabledUseCase>())

        fun arrange() = this to CheckRevocationListUseCaseImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            currentClientIdProvider = currentClientIdProvider,
            mlsClientProvider = mlsClientProvider,
            mLSConversationsVerificationStatusesHandler = mLSConversationsVerificationStatusesHandler,
            isE2EIEnabledUseCase =  isE2EIEnabledUseCase
        )

        fun withE2EIRepositoryFailure() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getClientDomainCRL)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(E2EIFailure.Generic(Exception())))
        }

        fun withE2EIRepositorySuccess() = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getClientDomainCRL)
                .whenInvokedWith(any())
                .thenReturn(Either.Right("result".toByteArray()))
        }

        fun withCurrentClientIdProviderFailure() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        fun withCurrentClientIdProviderSuccess() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
        }

        fun withMlsClientProviderFailure() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        fun withMlsClientProviderSuccess() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(mlsClient))
        }

        fun withRegisterCrl() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::registerCrl)
                .whenInvokedWith(any(), any())
                .thenReturn(CrlRegistration(false, EXPIRATION))
        }

        fun withRegisterCrlFlagChanged() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::registerCrl)
                .whenInvokedWith(any())
                .thenReturn(CrlRegistration(true, EXPIRATION))
        }

        fun withE2EIEnabledAndMLSEnabled(result: Boolean) = apply {
            given(isE2EIEnabledUseCase)
                .function(isE2EIEnabledUseCase::invoke)
                .whenInvoked()
                .thenReturn(result)
        }
    }

    companion object {
        private const val DUMMY_URL = "https://dummy.url"
        private val EXPIRATION = 10.toULong()
    }
}
