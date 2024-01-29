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
package com.wire.kalium.logic.feature.user.e2ei

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveShouldNotifyForRevokedCertificateUseCaseTest {

    @Test
    fun givenUserConfigRepositoryFailure_whenRunningUseCase_thenEmitFalse() = runTest {
        val (_, observeShouldNotifyForRevokedCertificate) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        val result = observeShouldNotifyForRevokedCertificate.invoke()

        assertEquals(false, result.first())
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenRunningUseCase_thenEmitSameValueOfRepository() =
        runTest {
            val (_, observeShouldNotifyForRevokedCertificate) = Arrangement()
                .withUserConfigRepositorySuccess()
                .arrange()

            val result = observeShouldNotifyForRevokedCertificate.invoke()

            assertEquals(true, result.first())
        }

    internal class Arrangement {

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun arrange() = this to ObserveShouldNotifyForRevokedCertificateUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

        fun withUserConfigRepositoryFailure() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeShouldNotifyForRevokedCertificate)
                .whenInvoked()
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withUserConfigRepositorySuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeShouldNotifyForRevokedCertificate)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(true)))
        }
    }
}
