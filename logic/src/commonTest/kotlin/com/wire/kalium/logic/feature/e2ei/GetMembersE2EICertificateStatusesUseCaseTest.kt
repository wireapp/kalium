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
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCaseImpl
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.CertificateStatusMapperArrangement
import com.wire.kalium.logic.util.arrangement.mls.CertificateStatusMapperArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangementImpl
import io.mockative.eq
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetMembersE2EICertificateStatusesUseCaseTest {

    @Test
    fun givenErrorOnGettingMembersIdentities_whenRequestMembersStatuses_thenEmptyMapResult() =
        runTest {
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withMembersIdentities(Either.Left(MLSFailure.WrongEpoch))
            }

            val result = getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf())

            assertEquals(mapOf(), result)
        }

    @Test
    fun givenEmptyWireIdentityMap_whenRequestMembersStatuses_thenNotActivatedResult() = runTest {
        val (_, getMembersE2EICertificateStatuses) = arrange {
            withMembersIdentities(Either.Right(mapOf()))
            withClientsByConversationId(Either.Right(mapOf()))
        }

        val result = getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf())

        assertEquals(mapOf(), result)
    }

    @Test
    fun givenOneWireIdentityExpiredForSomeUser_whenRequestMembersStatuses_thenResultUsersStatusIsExpired() =
        runTest {
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withClientsByConversationId(Either.Right(mapOf()))
                withMembersIdentities(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                WIRE_IDENTITY,
                                WIRE_IDENTITY.copy(status = CryptoCertificateStatus.EXPIRED)
                            )
                        )
                    )
                )
            }

            val result = getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf(USER_ID))

            assertEquals(CertificateStatus.EXPIRED, result[USER_ID])
        }

    @Test
    fun givenOneWireIdentityRevokedForSomeUser_whenRequestMembersStatuses_thenResultUsersStatusIsRevoked() =
        runTest {
            val userId2 = USER_ID.copy(value = "value_2")
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withClientsByConversationId(Either.Right(mapOf()))
                withMembersIdentities(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                WIRE_IDENTITY,
                                WIRE_IDENTITY.copy(status = CryptoCertificateStatus.REVOKED)
                            ),
                            userId2 to listOf(WIRE_IDENTITY)
                        )
                    )
                )
            }

            val result =
                getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf(USER_ID, userId2))

            assertEquals(CertificateStatus.REVOKED, result[USER_ID])
            assertEquals(CertificateStatus.VALID, result[userId2])
        }

    @Test
    fun givenOneWireIdentityAndAllClientsHaveIdentity_whenRequestMembersStatuses_thenResultUsersStatusIsValid() =
        runTest {
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withClientsByConversationId(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                TestClient.CLIENT,
                                TestClient.CLIENT.copy(id = ClientId("test_1"))
                            )
                        )
                    )
                )
                withMembersIdentities(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                WIRE_IDENTITY,
                                WIRE_IDENTITY.copy(
                                    status = CryptoCertificateStatus.REVOKED,
                                    clientId = CRYPTO_QUALIFIED_CLIENT_ID.copy(value = "test_1")
                                )
                            )
                        )
                    )
                )
            }

            val result =
                getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf(USER_ID))

            assertEquals(CertificateStatus.REVOKED, result[USER_ID])
        }

    @Test
    fun givenOneWireIdentityValidForSomeUserButNoIdentityForSomeClient_whenRequestMembersStatuses_thenResultUsersStatusIsNull() =
        runTest {
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withClientsByConversationId(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                TestClient.CLIENT,
                                TestClient.CLIENT.copy(id = ClientId("test_1")),
                                TestClient.CLIENT.copy(id = ClientId("test_2"))
                            )
                        )
                    )
                )
                withMembersIdentities(
                    Either.Right(
                        mapOf(
                            USER_ID to listOf(
                                WIRE_IDENTITY,
                                WIRE_IDENTITY.copy(
                                    status = CryptoCertificateStatus.REVOKED,
                                    clientId = CRYPTO_QUALIFIED_CLIENT_ID.copy(value = "test_1")
                                )
                            )
                        )
                    )
                )
            }

            val result =
                getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf(USER_ID))

            assertEquals(null, result[USER_ID])
        }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        CertificateStatusMapperArrangement by CertificateStatusMapperArrangementImpl(),
        ClientRepositoryArrangement by ClientRepositoryArrangementImpl() {

        fun arrange() = run {
            withCertificateStatusMapperReturning(
                CertificateStatus.VALID,
                eq(CryptoCertificateStatus.VALID)
            )
            withCertificateStatusMapperReturning(
                CertificateStatus.EXPIRED,
                eq(CryptoCertificateStatus.EXPIRED)
            )
            withCertificateStatusMapperReturning(
                CertificateStatus.REVOKED,
                eq(CryptoCertificateStatus.REVOKED)
            )

            block()
            this@Arrangement to GetMembersE2EICertificateStatusesUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                certificateStatusMapper = certificateStatusMapper,
                clientRepository = clientRepository
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId(TestClient.CLIENT_ID.value, USER_ID.toCrypto())

        private val CONVERSATION_ID = ConversationId("conversation_value", "domain")
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
                endTimestampSeconds = 1899105093
            )
    }
}
