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
package com.wire.kalium.logic.feature.applock

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test

class MarkTeamAppLockStatusAsNotifiedUseCaseTest {

    @Test
    fun givenAppLockStatusChanged_whenMarkingAsNotified_thenSetAppLockAsNotified() {
        val (arrangement, useCase) = Arrangement()
            .withSuccess()
            .arrange()

        useCase.invoke()

        verify {
            arrangement.userConfigRepository.setTeamAppLockAsNotified()
        }.wasInvoked(once)
    }

    class Arrangement {

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun arrange() = this to MarkTeamAppLockStatusAsNotifiedUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

        fun withSuccess() = apply {
            every {
                userConfigRepository.setTeamAppLockAsNotified()
            }.returns(Either.Right(Unit))
        }
    }
}
