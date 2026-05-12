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
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SetNewUserTrackingIdentifierUseCaseTest {

    @Test
    fun givenAnIdentifier_whenSettingANewOne_thenRepositoryShouldBeCalled() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val newIdentifier = "newIdentifier"
        useCase(newIdentifier)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setCurrentTrackingIdentifier(newIdentifier)
        }
    }

    private class Arrangement {

        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to SetNewUserTrackingIdentifierUseCase(
            userConfigRepository = userConfigRepository
        )
    }
}
