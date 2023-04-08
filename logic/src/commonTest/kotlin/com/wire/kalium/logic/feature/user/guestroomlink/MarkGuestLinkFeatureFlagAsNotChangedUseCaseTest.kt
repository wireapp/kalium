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

package com.wire.kalium.logic.feature.user.guestroomlink

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test

class MarkGuestLinkFeatureFlagAsNotChangedUseCaseTest {

    @Test
    fun givenRepositoryReturnsFailure_whenInvokingUseCase_thenDoNotUpdateGuestStatus() {
        val (arrangement, markGuestLinkFeatureFlagAsNotChanged) = Arrangement()
            .withMarkGuestLinkFeatureFlagAsNotified(Either.Left(StorageFailure.Generic(RuntimeException())))
            .arrange()

        markGuestLinkFeatureFlagAsNotChanged()

        verify(arrangement.userConfigRepository).function(arrangement.userConfigRepository::markGuestRoomLinkFeatureFlagAsNotified)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryReturnsSuccess_whenInvokingUseCase_thenUpdateGuestStatus() {
        val (arrangement, markGuestLinkFeatureFlagAsNotChanged) = Arrangement()
            .withMarkGuestLinkFeatureFlagAsNotified(Either.Right(Unit))
            .arrange()

        markGuestLinkFeatureFlagAsNotChanged()

        verify(arrangement.userConfigRepository).function(arrangement.userConfigRepository::markGuestRoomLinkFeatureFlagAsNotified)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        private val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase =
            MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)

        fun withMarkGuestLinkFeatureFlagAsNotified(result: Either<StorageFailure, Unit>) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::markGuestRoomLinkFeatureFlagAsNotified)
                .whenInvoked()
                .then { result }
        }

        fun arrange() = this to markGuestLinkFeatureFlagAsNotChanged
    }
}
