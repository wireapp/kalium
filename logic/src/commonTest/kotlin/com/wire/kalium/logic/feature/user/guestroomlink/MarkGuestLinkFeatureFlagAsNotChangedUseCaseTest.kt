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

package com.wire.kalium.logic.feature.user.guestroomlink

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.GuestRoomLinkStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class MarkGuestLinkFeatureFlagAsNotChangedUseCaseTest {

    val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

    lateinit var markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase

    @BeforeTest
    fun setUp() {
        markGuestLinkFeatureFlagAsNotChanged = MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)
    }

    @Test
    fun givenRepositoryReturnsFailure_whenInvokingUseCase_thenDoNotUpdateGuestStatus() = runTest {
        coEvery {
            userConfigRepository.getGuestRoomLinkStatus()
        }.returns(Either.Left(StorageFailure.DataNotFound))

        markGuestLinkFeatureFlagAsNotChanged()

        coVerify {
            userConfigRepository.getGuestRoomLinkStatus()
        }.wasInvoked(exactly = once)

        coVerify {
            userConfigRepository.setGuestRoomStatus(any(), eq(false))
        }.wasNotInvoked()
    }

    @Test
    fun givenRepositoryReturnsSuccess_whenInvokingUseCase_thenUpdateGuestStatus() = runTest {
        coEvery {
            userConfigRepository.getGuestRoomLinkStatus()
        }.returns(Either.Right(GuestRoomLinkStatus(isGuestRoomLinkEnabled = true, isStatusChanged = false)))
        coEvery {
            userConfigRepository.setGuestRoomStatus(status = false, isStatusChanged = false)
        }.returns(Either.Right(Unit))

        markGuestLinkFeatureFlagAsNotChanged()

        coVerify {
            userConfigRepository.getGuestRoomLinkStatus()
        }.wasInvoked(exactly = once)
        coVerify {
            userConfigRepository.setGuestRoomStatus(any(), eq(false))
        }.wasInvoked(once)

    }
}
