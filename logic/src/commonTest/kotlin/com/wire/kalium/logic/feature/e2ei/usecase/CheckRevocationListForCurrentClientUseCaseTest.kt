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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.functional.Either
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

class CheckRevocationListForCurrentClientUseCaseTest {

    @Test
    fun givenShouldCheckCrlIsFalse_whenRunningUseCase_thenDoNothing() = runTest {
        val (arrangement, checkRevocationListForCurrentClient) = Arrangement()
            .withShouldCheckCrlForCurrentClientReturning(false)
            .arrange()

        checkRevocationListForCurrentClient()

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::getCurrentClientCrlUrl)
            .wasNotInvoked()
    }

    @Test
    fun givenFailureWhenGettingCurrentClientCrlUrl_whenRunningUseCase_thenDoNotCheckCrl() =
        runTest {
            val (arrangement, checkRevocationListForCurrentClient) = Arrangement()
                .withShouldCheckCrlForCurrentClientReturning(true)
                .withGetCurrentClientCrlUrlReturning(Either.Left(CoreFailure.InvalidEventSenderID))
                .arrange()

            checkRevocationListForCurrentClient()

            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::getCurrentClientCrlUrl)
                .wasInvoked(once)

            verify(arrangement.checkRevocationList)
                .suspendFunction(arrangement.checkRevocationList::invoke)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenCheckRevocationListUseCaseReturnsFailure_whenRunningUseCase_thenDoNothing() = runTest {
        val (arrangement, checkRevocationListForCurrentClient) = Arrangement()
            .withShouldCheckCrlForCurrentClientReturning(true)
            .withGetCurrentClientCrlUrlReturning(Either.Right(URL))
            .withCheckRevocationListUseCaseReturning(Either.Left(CoreFailure.InvalidEventSenderID))
            .arrange()

        checkRevocationListForCurrentClient()

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenCheckRevocationListUseCaseReturnsSuccess_whenRunningUseCase_thenInvokeAddOrUpdateCRLAndSetShouldCheckCrlForCurrentClientOnce() =
        runTest {
            val (arrangement, checkRevocationListForCurrentClient) = Arrangement()
                .withShouldCheckCrlForCurrentClientReturning(true)
                .withGetCurrentClientCrlUrlReturning(Either.Right(URL))
                .withCheckRevocationListUseCaseReturning(Either.Right(EXPIRATION))
                .withSetShouldCheckCrlForCurrentClient()
                .arrange()

            checkRevocationListForCurrentClient()

            verify(arrangement.checkRevocationList)
                .suspendFunction(arrangement.checkRevocationList::invoke)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.certificateRevocationListRepository)
                .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
                .with(any(), any())
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setShouldCheckCrlForCurrentClient)
                .with(eq(false))
                .wasInvoked(once)
        }

    internal class Arrangement {

        @Mock
        val certificateRevocationListRepository =
            mock(classOf<CertificateRevocationListRepository>())

        @Mock
        val checkRevocationList = mock(classOf<CheckRevocationListUseCase>())

        @Mock
        val userConfigRepository =
            mock(classOf<UserConfigRepository>())

        fun arrange() = this to CheckRevocationListForCurrentClientUseCaseImpl(
            checkRevocationList = checkRevocationList,
            certificateRevocationListRepository = certificateRevocationListRepository,
            userConfigRepository = userConfigRepository
        )

        fun withShouldCheckCrlForCurrentClientReturning(value: Boolean) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::shouldCheckCrlForCurrentClient)
                .whenInvoked()
                .thenReturn(value)
        }

        fun withGetCurrentClientCrlUrlReturning(result: Either<CoreFailure, String>) = apply {
            given(certificateRevocationListRepository)
                .suspendFunction(certificateRevocationListRepository::getCurrentClientCrlUrl)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withCheckRevocationListUseCaseReturning(result: Either<CoreFailure, ULong?>) = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }
        fun withSetShouldCheckCrlForCurrentClient() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setShouldCheckCrlForCurrentClient)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
    }

    companion object {
        private const val URL = "https://example.com"
        private const val EXPIRATION = 12324UL
    }
}