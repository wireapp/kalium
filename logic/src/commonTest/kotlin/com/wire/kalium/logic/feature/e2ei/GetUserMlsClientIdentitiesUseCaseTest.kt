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

import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserMlsClientIdentitiesUseCaseImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.IsMlsEnabledUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.mls.IsMlsEnabledUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUserMlsClientIdentitiesUseCaseTest {

    @Test
    fun givenErrorOnGettingUserIdentity_whenGetUserMlsClientIdentities_thenEmptyMapResult() =
        runTest {
            val (_, getUserMlsClientIdentities) = arrange {
                withMLSEnabled(true)
                withUserIdentity(Either.Left(MLSFailure.WrongEpoch))
            }

            val result = getUserMlsClientIdentities(USER_ID)

            assertTrue(result.isEmpty())
        }

    @Test
    fun givenEmptyWireIdentityList_whenGetUserMlsClientIdentities_thenEmptyMapResult() =
        runTest {
            val (_, getUserMlsClientIdentities) = arrange {
                withMLSEnabled(true)
                withUserIdentity(Either.Right(listOf()))
            }

            val result = getUserMlsClientIdentities(USER_ID)

            assertTrue(result.isEmpty())
        }

    @Test
    fun givenOneWireIdentityExpired_whenGetUserMlsClientIdentities_thenResultCorrectMap() =
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
            val (_, getUserMlsClientIdentities) = arrange {
                withMLSEnabled(true)
                withUserIdentity(Either.Right(listOf(identity1, identity2, identity3)))
            }

            val result = getUserMlsClientIdentities(USER_ID)

            assertEquals(3, result.size)
            assertEquals(MLSClientE2EIStatus.VALID, result[identity1.clientId.value]?.e2eiStatus)
            assertEquals(MLSClientE2EIStatus.EXPIRED, result[identity2.clientId.value]?.e2eiStatus)
            assertEquals(MLSClientE2EIStatus.REVOKED, result[identity3.clientId.value]?.e2eiStatus)
        }

    @Test
    fun givenE2EIAndMLSIsDisabled_whenGettingUserMlsClientIdentities_thenEmptyMapIsReturned() =
        runTest {
            // given
            val (arrangement, getUserMlsClientIdentities) = arrange {
                withMLSEnabled(false)
            }

            // when
            val result = getUserMlsClientIdentities(USER_ID)

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
        IsMlsEnabledUseCaseArrangement by IsMlsEnabledUseCaseArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to GetUserMlsClientIdentitiesUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                isMlsEnabledUseCase = isMlsEnabledUseCase,
            )
        }
    }

    private companion object {
        fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId("clientId", USER_ID.toCrypto())
        private val WIRE_IDENTITY = WireIdentity(
            CRYPTO_QUALIFIED_CLIENT_ID,
            status = CryptoCertificateStatus.VALID,
            thumbprint = "thumbprint",
            credentialType = CredentialType.X509,
            x509Identity = WireIdentity.X509Identity(
                WireIdentity.Handle(
                    scheme = "wireapp", handle = "userHandle", domain = "domain1"
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
