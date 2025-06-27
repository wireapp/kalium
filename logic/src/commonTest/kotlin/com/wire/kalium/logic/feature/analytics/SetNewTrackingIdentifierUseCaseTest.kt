/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.logic.configuration.UserConfigRepository
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SetNewTrackingIdentifierUseCaseTest {

    @Test
    fun givenAnIdentifier_whenSettingANewOne_thenRepositoryShouldBeCalled() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val newIdentifier = "newIdentifier"
        useCase(newIdentifier)

        coVerify { arrangement.userConfigRepository.setCurrentTrackingIdentifier(eq(newIdentifier)) }
    }

    private class Arrangement {

        val userConfigRepository = mock(UserConfigRepository::class)

        init {
            runBlocking {
                coEvery { userConfigRepository.setCurrentTrackingIdentifier(any()) } returns Unit
            }
        }

        fun arrange() = this to SetNewTrackingIdentifierUseCase(
            userConfigRepository = userConfigRepository
        )
    }
}
