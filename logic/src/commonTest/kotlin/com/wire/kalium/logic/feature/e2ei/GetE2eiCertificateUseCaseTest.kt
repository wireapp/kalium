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

import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityResult
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCaseImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetE2eiCertificateUseCaseTest {

    @Test
    fun givenRepositoryReturnsFailure_whenRunningUseCase_thenReturnGenericFailure() = runTest {
        val (arrangement, getE2eiCertificateUseCase) = Arrangement()
            .withRepositoryFailure()
            .arrange()

        val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

        coVerify {
            arrangement.mlsConversationRepository.getClientIdentity(any(), any())
        }.wasInvoked(once)

        assertIs<GetMLSClientIdentityResult.Failure.Generic>(result)
    }

    @Test
    fun givenRepositoryReturnsStorageFailure_whenRunningUseCase_thenReturnGenericFailure() = runTest {
        val (arrangement, getE2eiCertificateUseCase) = Arrangement()
            .withRepositoryFailure(StorageFailure.DataNotFound)
            .arrange()

        val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

        coVerify {
            arrangement.mlsConversationRepository.getClientIdentity(any(), any())
        }.wasInvoked(once)

        assertIs<GetMLSClientIdentityResult.Failure.Generic>(result)
    }

    @Test
    fun givenRepositoryReturnsValidCertificateString_whenRunningUseCase_thenReturnSuccessWithCertificate() =
        runTest {
            val (arrangement, getE2eiCertificateUseCase) = Arrangement()
                .withRepositoryValidCertificate(X509_VALID_IDENTITY)
                .arrange()

            val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

            assertIs<GetMLSClientIdentityResult.Success>(result)
            assertEquals(MLSClientE2EIStatus.VALID, result.identity.e2eiStatus)

            coVerify {
                arrangement.mlsConversationRepository.getClientIdentity(any(), any())
            }.wasInvoked(once)
        }

    @Test
    fun givenRepositoryReturnsBasicClient_whenRunningUseCase_thenReturnSuccessWithNotActivatedStatus() =
        runTest {
            val (arrangement, getE2eiCertificateUseCase) = Arrangement()
                .withRepositoryValidCertificate(BASIC_VALID_IDENTITY)
                .arrange()

            val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

            assertIs<GetMLSClientIdentityResult.Success>(result)
            assertEquals(MLSClientE2EIStatus.NOT_ACTIVATED, result.identity.e2eiStatus)

            coVerify {
                arrangement.mlsConversationRepository.getClientIdentity(any(), any())
            }.wasInvoked(once)
        }

    @Test
    fun givenRepositoryReturnsNullCertificate_whenRunningUseCase_thenReturnIdentityNotFound() =
        runTest {
            val (arrangement, getE2eiCertificateUseCase) = Arrangement()
                .withRepositoryValidCertificate(null)
                .arrange()

            val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

            assertIs<GetMLSClientIdentityResult.Failure.IdentityNotFound>(result)

            coVerify {
                arrangement.mlsConversationRepository.getClientIdentity(any(), any())
            }.wasInvoked(once)
        }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        suspend fun arrange() = this to GetMLSClientIdentityUseCaseImpl(
            mlsConversationRepository = mlsConversationRepository,
            transactionProvider = cryptoTransactionProvider
        )
            .also {
                withMLSTransactionReturning(Either.Right(Unit))
            }

        suspend fun withRepositoryFailure(failure: CoreFailure = E2EIFailure.Generic(Exception())) = apply {
            coEvery {
                mlsConversationRepository.getClientIdentity(any(), any())
            }.returns(Either.Left(failure))
        }

        suspend fun withRepositoryValidCertificate(identity: WireIdentity?) = apply {
            coEvery {
                mlsConversationRepository.getClientIdentity(any(), any())
            }.returns(Either.Right(identity))
        }
    }

    companion object {
        val CLIENT_ID = ClientId("client-id")
        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId("clientId", USER_ID.toCrypto())

        val BASIC_VALID_IDENTITY = WireIdentity(
            CRYPTO_QUALIFIED_CLIENT_ID,
            status = CryptoCertificateStatus.VALID,
            thumbprint = "thumbprint",
            credentialType = CredentialType.Basic,
            x509Identity = null
        )
        val X509_VALID_IDENTITY = WireIdentity(
            CRYPTO_QUALIFIED_CLIENT_ID,
            status = CryptoCertificateStatus.VALID,
            thumbprint = "thumbprint",
            credentialType = CredentialType.X509,
            x509Identity = WireIdentity.X509Identity(
                WireIdentity.Handle(
                    scheme = "wireapp",
                    handle = "userHandle",
                    domain = "domain1"
                ),
                displayName = "user displayName",
                domain = "domain.com",
                certificate = "cert1",
                serialNumber = "serial1",
                notBefore = Instant.DISTANT_PAST.epochSeconds,
                notAfter = Instant.DISTANT_FUTURE.epochSeconds
            )
        )
    }
}
