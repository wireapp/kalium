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
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserE2eiCertificateStatusResult
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserE2eiCertificateStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.mls.IsE2EIEnabledUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.PemCertificateDecoderArrangement
import com.wire.kalium.logic.util.arrangement.mls.PemCertificateDecoderArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUserE2eiCertificateStatusUseCaseTest {

    @Test
    fun givenErrorOnGettingUserIdentity_whenGetUserE2eiCertificateStatus_thenNotActivatedResult() = runTest {
        val (_, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withUserIdentity(Either.Left(MLSFailure.WrongEpoch))
        }

        val result = getUserE2eiCertificateStatus(USER_ID)

        assertEquals(GetUserE2eiCertificateStatusResult.Failure.NotActivated, result)
    }

    @Test
    fun givenEmptyWireIdentityList_whenGetUserE2eiCertificateStatus_thenNotActivatedResult() = runTest {
        val (_, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withUserIdentity(Either.Right(listOf()))
        }

        val result = getUserE2eiCertificateStatus(USER_ID)

        assertEquals(GetUserE2eiCertificateStatusResult.Failure.NotActivated, result)
    }

    @Test
    fun givenOneWireIdentityExpired_whenGetUserE2eiCertificateStatus_thenResultIsExpired() = runTest {
        val (_, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withUserIdentity(Either.Right(listOf(WIRE_IDENTITY, WIRE_IDENTITY.copy(status = CryptoCertificateStatus.EXPIRED))))
        }

        val result = getUserE2eiCertificateStatus(USER_ID)

        assertTrue { result is GetUserE2eiCertificateStatusResult.Success }
        assertEquals(CertificateStatus.EXPIRED, (result as GetUserE2eiCertificateStatusResult.Success).status)
    }

    @Test
    fun givenOneWireIdentityRevoked_whenGetUserE2eiCertificateStatus_thenResultIsRevoked() = runTest {
        val (_, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
            withUserIdentity(Either.Right(listOf(WIRE_IDENTITY, WIRE_IDENTITY.copy(status = CryptoCertificateStatus.REVOKED))))
        }

        val result = getUserE2eiCertificateStatus(USER_ID)

        assertTrue { result is GetUserE2eiCertificateStatusResult.Success }
        assertEquals(CertificateStatus.REVOKED, (result as GetUserE2eiCertificateStatusResult.Success).status)
    }

    @Test
    fun givenOneWireIdentityRevoked_whenGetUserE2eiCertificateStatus_thenResultIsRevoked2() = runTest {
        val (_, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(true)
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

        assertTrue { result is GetUserE2eiCertificateStatusResult.Success }
        assertEquals(CertificateStatus.REVOKED, (result as GetUserE2eiCertificateStatusResult.Success).status)
    }

    @Test
    fun givenE2EIAndMLSIsDisabled_whenGettingUserE2EICertificateStatus_thenFailureNotActivatedIsReturned() = runTest {
        // given
        val (arrangement, getUserE2eiCertificateStatus) = arrange {
            withE2EIEnabledAndMLSEnabled(false)
        }

        // when
        val result = getUserE2eiCertificateStatus(USER_ID)

        // then
        assertEquals(
            GetUserE2eiCertificateStatusResult.Failure.NotActivated,
            result
        )
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::getUserIdentity)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        PemCertificateDecoderArrangement by PemCertificateDecoderArrangementImpl(),
        IsE2EIEnabledUseCaseArrangement by IsE2EIEnabledUseCaseArrangementImpl() {

        fun arrange() = run {
            withPemCertificateDecode(E2EI_CERTIFICATE, any(), eq(CryptoCertificateStatus.VALID))
            withPemCertificateDecode(E2EI_CERTIFICATE.copy(status = CertificateStatus.EXPIRED), any(), eq(CryptoCertificateStatus.EXPIRED))
            withPemCertificateDecode(E2EI_CERTIFICATE.copy(status = CertificateStatus.REVOKED), any(), eq(CryptoCertificateStatus.REVOKED))

            block()
            this@Arrangement to GetUserE2eiCertificateStatusUseCaseImpl(
                mlsConversationRepository = mlsConversationRepository,
                pemCertificateDecoder = pemCertificateDecoder,
                isE2EIEnabledUseCase = isE2EIEnabledUseCase
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        private val USER_ID = UserId("value", "domain")
        private val CRYPTO_QUALIFIED_CLIENT_ID = CryptoQualifiedClientId("clientId", USER_ID.toCrypto())
        private val WIRE_IDENTITY = WireIdentity(
            CRYPTO_QUALIFIED_CLIENT_ID,
            "user_handle",
            "User Test",
            "domain.com",
            "certificate",
            CryptoCertificateStatus.VALID,
            "thumbprint"
        )
        private val E2EI_CERTIFICATE =
            E2eiCertificate(issuer = "issue", status = CertificateStatus.VALID, serialNumber = "number", certificateDetail = "details")
    }
}
