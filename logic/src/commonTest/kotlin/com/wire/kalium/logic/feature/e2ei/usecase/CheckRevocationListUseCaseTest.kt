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
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandler
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.feature.e2ei.E2eiCertificate
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
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

class CheckRevocationListUseCaseTest {

    @Test
    fun givenE2EIRepositoryReturnsFailure_whenRunningUseCase_thenDoNotRegisterCrl() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositoryFailure()
                .arrange()

            checkRevocationList.invoke(DUMMY_URL)

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
    fun givenE2EIRepositoryReturnsSuccess_whenRunningUseCase_thenRegisterCrl() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositorySuccess()
                .withRegisterCrlResult()
                .withCurrentClientId()
                .withValidCertificate()
                .arrange()

            checkRevocationList.invoke(DUMMY_URL)

            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setCRLExpirationTime)
                .with(any(), any())
                .wasInvoked(once)

            verify(arrangement.mLSConversationsVerificationStatusesHandler)
                .suspendFunction(arrangement.mLSConversationsVerificationStatusesHandler::invoke)
                .wasNotInvoked()
        }

    @Test
    fun givenCertificatesRegistrationReturnsFlagIsChanged_whenRunningUseCase_thenUpdateConversationStates() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositorySuccess()
                .withRegisterCrlResultDirty()
                .withCurrentClientId()
                .withValidCertificate()
                .arrange()

            checkRevocationList.invoke(DUMMY_URL)

            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setCRLExpirationTime)
                .with(any(), any())
                .wasInvoked(once)

            verify(arrangement.mLSConversationsVerificationStatusesHandler)
                .suspendFunction(arrangement.mLSConversationsVerificationStatusesHandler::invoke)
                .wasInvoked(once)


            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(once)

            verify(arrangement.getE2eiCertificate)
                .suspendFunction(arrangement.getE2eiCertificate::invoke)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setShouldNotifyForRevokedCertificate)
                .with(eq(true))
                .wasNotInvoked()
        }

    @Test
    fun givenCertificateRevokedForCurrentClient_whenRunningUseCase_thenUpdateShouldNotifyFlagToTrue() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositorySuccess()
                .withRegisterCrlResultDirty()
                .withCurrentClientId()
                .withRevokedCertificate()
                .arrange()

            checkRevocationList.invoke(DUMMY_URL)

            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getClientDomainCRL)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.mlsClient)
                .suspendFunction(arrangement.mlsClient::registerCrl)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setCRLExpirationTime)
                .with(any(), any())
                .wasInvoked(once)

            verify(arrangement.mLSConversationsVerificationStatusesHandler)
                .suspendFunction(arrangement.mLSConversationsVerificationStatusesHandler::invoke)
                .wasInvoked(once)

            verify(arrangement.currentClientIdProvider)
                .suspendFunction(arrangement.currentClientIdProvider::invoke)
                .wasInvoked(once)

            verify(arrangement.getE2eiCertificate)
                .suspendFunction(arrangement.getE2eiCertificate::invoke)
                .with(eq(TestClient.CLIENT_ID))
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setShouldNotifyForRevokedCertificate)
                .with(eq(true))
                .wasInvoked(once)
        }

    internal class Arrangement {

        @Mock
        val e2EIRepository = mock(classOf<E2EIRepository>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        val mLSConversationsVerificationStatusesHandler =
            mock(classOf<MLSConversationsVerificationStatusesHandler>())

        @Mock
        val currentClientIdProvider =
            mock(classOf<CurrentClientIdProvider>())

        @Mock
        val getE2eiCertificate =
            mock(classOf<GetE2eiCertificateUseCase>())

        fun arrange() = this to CheckRevocationListUseCaseImpl(
            selfUserId = TestUser.USER_ID,
            e2EIRepository = e2EIRepository,
            mlsClient = mlsClient,
            userConfigRepository = userConfigRepository,
            mLSConversationsVerificationStatusesHandler = mLSConversationsVerificationStatusesHandler,
            currentClientIdProvider = currentClientIdProvider,
            getE2eiCertificate = getE2eiCertificate
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

        fun withRegisterCrlResult() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::registerCrl)
                .whenInvokedWith(any(), any())
                .thenReturn(CrlRegistration(false, 10.toULong()))
        }

        fun withRegisterCrlResultDirty() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::registerCrl)
                .whenInvokedWith(any())
                .thenReturn(CrlRegistration(true, 10.toULong()))
        }

        fun withCurrentClientId() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
        }

        fun withValidCertificate() = apply {
            given(getE2eiCertificate)
                .suspendFunction(getE2eiCertificate::invoke)
                .whenInvokedWith(eq(TestClient.CLIENT_ID))
                .thenReturn(
                    GetE2EICertificateUseCaseResult.Success(
                        E2eiCertificate(status = CertificateStatus.VALID)
                    )
                )
        }

        fun withRevokedCertificate() = apply {
            given(getE2eiCertificate)
                .suspendFunction(getE2eiCertificate::invoke)
                .whenInvokedWith(eq(TestClient.CLIENT_ID))
                .thenReturn(
                    GetE2EICertificateUseCaseResult.Success(
                        E2eiCertificate(status = CertificateStatus.REVOKED)
                    )
                )
        }
    }

    companion object {
        const val DUMMY_URL = "https://dummy.url"
    }
}
