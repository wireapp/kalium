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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class MarkTeamAppLockStatusAsNotifiedUseCaseTest {

    @Test
    fun givenAppLockStatusChanged_whenMarkingAsNotified_thenSetAppLockAsNotified() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccess()
            .arrange()

        useCase.invoke()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setTeamAppLockAsNotified()
        }
    }

    class Arrangement {

        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to MarkTeamAppLockStatusAsNotifiedUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

        suspend fun withSuccess() = apply {
            everySuspend {
                userConfigRepository.setTeamAppLockAsNotified()
            } returns Either.Right(Unit)
        }
    }
}
