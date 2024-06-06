/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserE2eiCertificatesUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUserE2eiAllCertificateStatusesUseCaseTest {

    @Test
    fun givenErrorOnGettingUserIdentity_whenGetUserE2eiAllCertificateStatuses_thenEmptyMapResult() =
        runTest {
            val (_, getUserE2eiAllCertificateStatuses) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withUserIdentity(Either.Left(MLSFailure.WrongEpoch))
            }

            val result = getUserE2eiAllCertificateStatuses(USER_ID)

            assertTrue(result.isEmpty())
        }

    @Test
    fun givenEmptyWireIdentityList_whenGetUserE2eiAllCertificateStatuses_thenEmptyMapResult() =
        runTest {
            val (_, getUserE2eiAllCertificateStatuses) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withUserIdentity(Either.Right(listOf()))
            }

            val result = getUserE2eiAllCertificateStatuses(USER_ID)

            assertTrue(result.isEmpty())
        }

    @Test
    fun givenOneWireIdentityExpired_whenGetUserE2eiAllCertificateStatuses_thenResultCorrectMap() =
        runTest {
            val identity1 = WIRE_IDENTITY
            val identity2 = WIRE_IDENTITY.copy(
                clientId = CRYPTO_QUALIFIED_CLIENT_ID.copy("id_2"),
                status = CryptoCertificateStatus.EXPIRED
            )
            val identity3 = WIRE_IDENTITY.copy(
                clientId = CRYPTO_QUALIFIED_CLIENT_ID.copy("id_3"),
                status = CryptoCertificateStatus.REVOKED
            )
            val (_, getUserE2eiAllCertificateStatuses) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withUserIdentity(Either.Right(listOf(identity1, identity2, identity3)))
            }

            val result = getUserE2eiAllCertificateStatuses(USER_ID)

            assertEquals(3, result.size)
            assertEquals(CertificateStatus.VALID, result[identity1.clientId.value]?.status)
            assertEquals(CertificateStatus.EXPIRED, result[identity2.clientId.value]?.status)
            assertEquals(CertificateStatus.REVOKED, result[identity3.clientId.value]?.status)
        }

    @Test
    fun givenE2EIAndMLSIsDisabled_whenGettingUserE2EICertificate_thenEmptyMapIsReturned() =
        runTest {
            // given
            val (arrangement, getUserE2eiAllCertificateStatuses) = arrange {
                withE2EIEnabledAndMLSEnabled(false)
            }

            // when
            val result = getUserE2eiAllCertificateStatuses(USER_ID)

            // then
            assertEquals(
                mapOf(),
                result
            )
            coVerify {
                arrangement.mlsConversationRepository.getUserIdentity(any())
            }.wasNotInvoked()
        }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        IsE2EIEnabledUseCaseArrangement by IsE2EIEnabledUseCaseArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to GetUserE2eiCertificatesUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase
            )
        }
    }

    private companion object {
        fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId("clientId", USER_ID.toCrypto())
        private val WIRE_IDENTITY =
            WireIdentity(
                CRYPTO_QUALIFIED_CLIENT_ID,
                "user_handle",
                "User Test",
                "domain.com",
                "certificate",
                CryptoCertificateStatus.VALID,
                "thumbprint",
                "serialNumber",
                1899105093
            )
    }
}
