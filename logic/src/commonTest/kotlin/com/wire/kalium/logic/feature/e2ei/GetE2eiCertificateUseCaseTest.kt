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

import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2EICertificateUseCaseResult
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2eiCertificateUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetE2eiCertificateUseCaseTest {

    @Test
    fun givenRepositoryReturnsFailure_whenRunningUseCase_thenReturnFailure() = runTest {
        val (arrangement, getE2eiCertificateUseCase) = Arrangement()
            .withRepositoryFailure()
            .arrange()

        val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::getClientIdentity)
            .with(any())
            .wasInvoked(once)

        assertEquals(GetE2EICertificateUseCaseResult.Failure, result)
    }

    @Test
    fun givenRepositoryReturnsValidCertificateString_whenRunningUseCase_thenReturnCertificate() =
        runTest {
            val (arrangement, getE2eiCertificateUseCase) = Arrangement()
                .withRepositoryValidCertificate(IDENTITY)
                .withMapperReturning(CertificateStatus.EXPIRED)
                .arrange()

            val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::getClientIdentity)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.certificateStatusMapper)
                .function(arrangement.certificateStatusMapper::toCertificateStatus)
                .with(any())
                .wasInvoked(once)

            assertEquals(true, result is GetE2EICertificateUseCaseResult.Success)
        }

    @Test
    fun givenRepositoryReturnsNullCertificate_whenRunningUseCase_thenReturnNotActivated() =
        runTest {
            val (arrangement, getE2eiCertificateUseCase) = Arrangement()
                .withRepositoryValidCertificate(null)
                .arrange()

            val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::getClientIdentity)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.certificateStatusMapper)
                .function(arrangement.certificateStatusMapper::toCertificateStatus)
                .with(any())
                .wasNotInvoked()

            assertEquals(true, result is GetE2EICertificateUseCaseResult.NotActivated)
        }

    class Arrangement {

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val certificateStatusMapper = mock(classOf<CertificateStatusMapper>())

        fun arrange() = this to GetE2eiCertificateUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            certificateStatusMapper = certificateStatusMapper
        )

        fun withRepositoryFailure() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::getClientIdentity)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(E2EIFailure.Generic(Exception())))
        }

        fun withRepositoryValidCertificate(identity: WireIdentity?) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::getClientIdentity)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(identity))
        }

        fun withMapperReturning(status: CertificateStatus) = apply {
            given(certificateStatusMapper)
                .function(certificateStatusMapper::toCertificateStatus)
                .whenInvokedWith(any())
                .thenReturn(status)
        }
    }

    companion object {
        val CLIENT_ID = ClientId("client-id")
        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId("clientId", USER_ID.toCrypto())

        val IDENTITY = WireIdentity(
            CRYPTO_QUALIFIED_CLIENT_ID,
            handle = "alic_test",
            displayName = "Alice Test",
            domain = "test.com",
            certificate = "certificate",
            status = CryptoCertificateStatus.EXPIRED,
            thumbprint = "thumbprint",
            serialNumber = "serialNumber",
            endTimestamp = 1899105093
        )
    }
}
