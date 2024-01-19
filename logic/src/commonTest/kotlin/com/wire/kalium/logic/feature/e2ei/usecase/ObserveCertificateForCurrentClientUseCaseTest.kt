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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ObserveCertificateForCurrentClientUseCaseTest {

    @Test
    fun givenE2EIRepositoryReturnsFailure_whenRunningUseCase_thenDoNotCheckRevocationList() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositoryFailure()
                .arrange()

            checkRevocationList.invoke()

            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getCurrentClientCrlUrl)
                .wasInvoked(once)

            verify(arrangement.checkRevocationList)
                .suspendFunction(arrangement.checkRevocationList::invoke)
                .with(any())
                .wasNotInvoked()
        }


    @Test
    fun givenUserConfigEmitsFailureAsExpirationTime_whenRunningUseCase_thenDoNotCheckRevocationList() =
        runTest {
            val (arrangement, checkRevocationList) = Arrangement()
                .withE2EIRepositorySuccess()
                .withConfigEmitsFailure()
                .arrange()

            checkRevocationList.invoke()

            verify(arrangement.e2EIRepository)
                .suspendFunction(arrangement.e2EIRepository::getCurrentClientCrlUrl)
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::observeCertificateExpirationTime)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.checkRevocationList)
                .suspendFunction(arrangement.checkRevocationList::invoke)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenNonExpiredTime_whenRunningUseCase_thenDoNotCheckRevocationList() = runTest {
        val (arrangement, checkRevocationList) = Arrangement()
            .withE2EIRepositorySuccess()
            .withNonExpiredTime()
            .arrange()

        checkRevocationList.invoke()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::observeCertificateExpirationTime)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnExpiredTime_whenRunningUseCase_thenCheckRevocationList() = runTest {
        val (arrangement, checkRevocationList) = Arrangement()
            .withE2EIRepositorySuccess()
            .withExpiredTime()
            .withCheckRevocationListSuccess()
            .arrange()

        checkRevocationList.invoke()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::observeCertificateExpirationTime)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenCheckUseCaseReturnsNull_whenRunningUseCase_thenDoNotUpdateExpirationTime() = runTest {
        val (arrangement, checkRevocationList) = Arrangement()
            .withE2EIRepositorySuccess()
            .withExpiredTime()
            .withCheckRevocationListNullResult()
            .arrange()

        checkRevocationList.invoke()

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setCRLExpirationTime)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenCheckUseCaseFailure_whenRunningUseCase_thenDoNotUpdateExpirationTime() = runTest {
        val (arrangement, checkRevocationList) = Arrangement()
            .withE2EIRepositorySuccess()
            .withExpiredTime()
            .withCheckRevocationListFailure()
            .arrange()

        checkRevocationList.invoke()

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(once)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setCRLExpirationTime)
            .with(any(), any())
            .wasNotInvoked()
    }

    internal class Arrangement {

        @Mock
        val e2EIRepository = mock(classOf<E2EIRepository>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val userConfigRepository =
            mock(classOf<UserConfigRepository>())

        @Mock
        val checkRevocationList =
            mock(classOf<CheckRevocationListUseCase>())

        fun arrange() = this to ObserveCertificateForCurrentClientUseCaseImpl(
            e2EIRepository = e2EIRepository,
            userConfigRepository = userConfigRepository,
            checkRevocationList = checkRevocationList
        )

        fun withE2EIRepositoryFailure() = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getCurrentClientCrlUrl)
                .whenInvoked()
                .thenReturn(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        fun withE2EIRepositorySuccess() = apply {
            given(e2EIRepository)
                .suspendFunction(e2EIRepository::getCurrentClientCrlUrl)
                .whenInvoked()
                .thenReturn(Either.Right(URL))
        }

        fun withConfigEmitsFailure() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeCertificateExpirationTime)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withExpiredTime() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeCertificateExpirationTime)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Right(632762669.toULong()))) // Fri Jan 19 1990 15:24:29 GMT+0000
        }

        fun withNonExpiredTime() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeCertificateExpirationTime)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Right(4104055469.toULong()))) // Tue Jan 19 2100 15:24:29 GMT+0000
        }

        fun withCheckRevocationListSuccess() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(4104055469.toULong()))
        }

        fun withCheckRevocationListFailure() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        fun withCheckRevocationListNullResult() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(null))
        }
    }

    companion object {
        const val URL = "https://test.com"
    }
}