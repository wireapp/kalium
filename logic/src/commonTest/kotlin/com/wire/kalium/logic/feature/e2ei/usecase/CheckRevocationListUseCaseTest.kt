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
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandler
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
                .withE2EIRepositoryFailure()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldFail()
            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getClientDomainCRL)
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
                .withE2EIRepositorySuccess()
                .withCurrentClientIdProviderFailure()
                .arrange()

            val result = checkRevocationList.invoke(DUMMY_URL)

            result.shouldFail()
            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getClientDomainCRL)
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
        val e2EIRepository = mock(classOf<E2EIRepository>())

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

        fun arrange() = this to CheckRevocationListUseCaseImpl(
            e2EIRepository = e2EIRepository,
            currentClientIdProvider = currentClientIdProvider,
            mlsClientProvider = mlsClientProvider,
            mLSConversationsVerificationStatusesHandler = mLSConversationsVerificationStatusesHandler
        )

        fun withE2EIRepositoryFailure() = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getClientDomainCRL)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(E2EIFailure.Generic(Exception())))
        }

        fun withE2EIRepositorySuccess() = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getClientDomainCRL)
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
    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
        val EXPIRATION = 10.toULong()
    }
}
