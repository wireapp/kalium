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

import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.e2ei.E2eiCertificateRepository
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2eiCertificateUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.e2ei.usecase.GetE2EICertificateUseCaseResult

class GetE2eiCertificateUseCaseTest {

    @Test
    fun givenRepositoryReturnsFailure_whenRunningUseCase_thenReturnNotActivated() {
        val (arrangement, getE2eiCertificateUseCase) = Arrangement()
            .withRepositoryFailure()
            .arrange()

        val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

        verify(arrangement.e2eiCertificateRepository)
            .function(arrangement.e2eiCertificateRepository::getE2eiCertificate)
            .with(any())
            .wasInvoked(once)

        assertEquals(GetE2EICertificateUseCaseResult.Failure.NotActivated, result)
    }

    @Test
    fun givenRepositoryReturnsValidCertificateString_whenRunningUseCase_thenReturnCertificate() {
        val (arrangement, getE2eiCertificateUseCase) = Arrangement()
            .withRepositoryValidCertificate()
            .withDecodeSuccess()
            .arrange()

        val result = getE2eiCertificateUseCase.invoke(CLIENT_ID)

        verify(arrangement.e2eiCertificateRepository)
            .function(arrangement.e2eiCertificateRepository::getE2eiCertificate)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.pemCertificateDecoder)
            .function(arrangement.pemCertificateDecoder::decode)
            .with(any())
            .wasInvoked(once)

        assertEquals(true, result is GetE2EICertificateUseCaseResult.Success)
    }

    class Arrangement {

        @Mock
        val e2eiCertificateRepository = mock(classOf<E2eiCertificateRepository>())

        @Mock
        val pemCertificateDecoder = mock(classOf<PemCertificateDecoder>())

        fun arrange() = this to GetE2eiCertificateUseCaseImpl(
            e2eiCertificateRepository = e2eiCertificateRepository,
            pemCertificateDecoder = pemCertificateDecoder
        )

        fun withRepositoryFailure() = apply {
            given(e2eiCertificateRepository)
                .function(e2eiCertificateRepository::getE2eiCertificate)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(E2EIFailure(Exception())))
        }

        fun withRepositoryValidCertificate() = apply {
            given(e2eiCertificateRepository)
                .function(e2eiCertificateRepository::getE2eiCertificate)
                .whenInvokedWith(any())
                .thenReturn(Either.Right("certificate"))
        }

        fun withDecodeSuccess() = apply {
            given(pemCertificateDecoder)
                .function(pemCertificateDecoder::decode)
                .whenInvokedWith(any())
                .thenReturn(e2eiCertificate)
        }
    }

    companion object {
        val CLIENT_ID = ClientId("client-id")
        val e2eiCertificate = E2eiCertificate("certificate")
    }
}
