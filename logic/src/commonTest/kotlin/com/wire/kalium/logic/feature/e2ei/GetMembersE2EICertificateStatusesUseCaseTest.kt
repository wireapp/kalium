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
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
            withMembersNameAndHandle(Either.Right(mapOf()))
        }

        val result = getMembersE2EICertificateStatuses(CONVERSATION_ID, listOf())

        assertEquals(mapOf(), result)
    }

    @Test
    fun givenOneWireIdentityExpiredForSomeUser_whenRequestMembersStatuses_thenResultUsersStatusIsExpired() =
        runTest {
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withMembersNameAndHandle(Either.Right(mapOf(USER_ID to NAME_AND_HANDLE)))
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

            assertEquals(false, result[USER_ID])
        }

    @Test
    fun givenOneWireIdentityRevokedForSomeUser_whenRequestMembersStatuses_thenResultUsersStatusIsRevoked() =
        runTest {
            val userId2 = USER_ID.copy(value = "value_2")
            val (_, getMembersE2EICertificateStatuses) = arrange {
                withMembersNameAndHandle(Either.Right(mapOf(userId2 to NAME_AND_HANDLE)))
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

            assertEquals(false, result[USER_ID])
            assertEquals(true, result[userId2])
        }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        fun arrange() = run {

            block()
            this@Arrangement to GetMembersE2EICertificateStatusesUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                conversationRepository = conversationRepository
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID =
            CryptoQualifiedClientId("clientId", USER_ID.toCrypto())

        private val CONVERSATION_ID = ConversationId("conversation_value", "domain")
        private val WIRE_IDENTITY = WireIdentity(
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

        private val NAME_AND_HANDLE = NameAndHandle(name = "user displayName", handle = "userHandle")
    }
}
