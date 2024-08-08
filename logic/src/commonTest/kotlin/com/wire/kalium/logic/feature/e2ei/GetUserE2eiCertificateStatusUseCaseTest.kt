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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.IsOtherUserE2EIVerifiedUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.any
<<<<<<< HEAD
import io.mockative.coVerify
import kotlinx.coroutines.runBlocking
=======
import io.mockative.verify
>>>>>>> ddddd549fa (fix: Validate other members UserName and DisplayName in E2EI [WPB-10402] (#2932))
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetUserE2eiCertificateStatusUseCaseTest {

    @Test
    fun givenErrorOnGettingUserIdentity_whenGetUserE2eiCertificateStatus_thenNotActivatedResult() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withUserIdentity(Either.Left(MLSFailure.WrongEpoch))
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenEmptyWireIdentityList_whenGetUserE2eiCertificateStatus_thenNotActivatedResult() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withUserIdentity(Either.Right(listOf()))
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityExpired_whenGetUserE2eiCertificateStatus_thenResultIsExpired() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
                withUserIdentity(
                    Either.Right(
                        listOf(
                            WIRE_IDENTITY,
                            WIRE_IDENTITY.copy(status = CryptoCertificateStatus.EXPIRED)
                        )
                    )
                )
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityRevoked_whenGetUserE2eiCertificateStatus_thenResultIsRevoked() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
                withUserIdentity(
                    Either.Right(
                        listOf(
                            WIRE_IDENTITY,
                            WIRE_IDENTITY.copy(status = CryptoCertificateStatus.REVOKED)
                        )
                    )
                )
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityRevoked_whenGetUserE2eiCertificateStatus_thenResultIsRevoked2() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
                withUserIdentity(
                    Either.Right(
                        listOf(
                            WIRE_IDENTITY.copy(status = CryptoCertificateStatus.EXPIRED),
                            WIRE_IDENTITY.copy(status = CryptoCertificateStatus.REVOKED)
                        )
                    )
                )
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenE2EIAndMLSIsDisabled_whenGettingUserE2EICertificateStatus_thenFailureNotActivatedIsReturned() =
        runTest {
            // given
            val (arrangement, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(false)
            }

            // when
            val result = getUserE2eiCertificateStatus(USER_ID)

            // then
            assertFalse(result)

            coVerify {
                arrangement.mlsConversationRepository.getUserIdentity(any())
            }.wasNotInvoked()
        }

<<<<<<< HEAD
    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
=======
    @Test
    fun givenOneWireIdentityIsOk_whenUserNameDiffFromIdentityName_thenResultIsExpired() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("another user displayName", "userHandle")), any())
                withUserIdentity(Either.Right(listOf(WIRE_IDENTITY)))
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityIsOk_whenUserHandleDiffFromIdentityName_thenResultIsExpired() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "anotherUserHandle")), any())
                withUserIdentity(Either.Right(listOf(WIRE_IDENTITY)))
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityIsOk_whenUserNameAndHandleAbsent_thenResultIsExpired() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Left(StorageFailure.DataNotFound), any())
                withUserIdentity(Either.Right(listOf(WIRE_IDENTITY)))
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertFalse(result)
        }

    @Test
    fun givenOneWireIdentityIsOk_whenUserNameAndHandleSameAsInIdentity_thenResultIsTrue() =
        runTest {
            val (_, getUserE2eiCertificateStatus) = arrange {
                withE2EIEnabledAndMLSEnabled(true)
                withNameAndHandle(Either.Right(NameAndHandle("user displayName", "userHandle")), any())
                withUserIdentity(Either.Right(listOf(WIRE_IDENTITY)))
            }

            val result = getUserE2eiCertificateStatus(USER_ID)

            assertTrue(result)
        }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
>>>>>>> ddddd549fa (fix: Validate other members UserName and DisplayName in E2EI [WPB-10402] (#2932))
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        IsE2EIEnabledUseCaseArrangement by IsE2EIEnabledUseCaseArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to IsOtherUserE2EIVerifiedUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase,
                userRepository = userRepository
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
