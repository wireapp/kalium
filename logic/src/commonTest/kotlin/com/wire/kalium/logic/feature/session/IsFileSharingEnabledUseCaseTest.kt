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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsFileSharingEnabledUseCaseTest {

    @Test
    fun givenATrueValue_thenISFileSharingIsEnabled() = runTest {
        val expectedValue = FileSharingStatus(FileSharingStatus.Value.EnabledAll, false)

        val (arrangement, isFileSharingEnabledUseCase) = Arrangement()
            .withSuccessfulResponse(expectedValue)
            .arrange()

        val actual = isFileSharingEnabledUseCase.invoke()
        assertEquals(expectedValue, actual)

        verify {
            arrangement.userConfigRepository.isFileSharingEnabled()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val dataNotFound = StorageFailure.DataNotFound
        val (arrangement, isFileSharingEnabledUseCase) = Arrangement()
            .withIsFileSharingEnabledErrorResponse(dataNotFound)
            .arrange()

        // When
        isFileSharingEnabledUseCase.invoke()

        verify {
            arrangement.userConfigRepository.isFileSharingEnabled()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        val isFileSharingEnabledUseCase = IsFileSharingEnabledUseCaseImpl(userConfigRepository)

        fun withSuccessfulResponse(expectedValue: FileSharingStatus): Arrangement {
            every {
                userConfigRepository.isFileSharingEnabled()
            }.returns(Either.Right(expectedValue))

            return this
        }

        fun withIsFileSharingEnabledErrorResponse(storageFailure: StorageFailure): Arrangement {
            every {
                userConfigRepository.isFileSharingEnabled()
            }.returns(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to isFileSharingEnabledUseCase
    }
}
