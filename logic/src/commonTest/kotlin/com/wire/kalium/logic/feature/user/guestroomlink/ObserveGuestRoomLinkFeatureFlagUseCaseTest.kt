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
import com.wire.kalium.logic.configuration.GuestRoomLinkStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveGuestRoomLinkFeatureFlagUseCaseTest {

    @Mock
    val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

    lateinit var observeGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase

    @BeforeTest
    fun setUp() {
        observeGuestRoomLinkFeatureFlag = ObserveGuestRoomLinkFeatureFlagUseCaseImpl(userConfigRepository)
    }

    @Test
    fun givenRepositoryEmitsFailure_whenRunningUseCase_thenEmitStatusWithNullValues() = runTest {
        every {
            userConfigRepository.observeGuestRoomLinkFeatureFlag()
        }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))

        val result = observeGuestRoomLinkFeatureFlag()

        assertNull(result.first().isGuestRoomLinkEnabled)
        assertNull(result.first().isStatusChanged)
    }

    @Test
    fun givenRepositoryEmitsValidValues_whenRunningUseCase_thenEmitThoseValidValues() = runTest {
        val expectedStatus = GuestRoomLinkStatus(isGuestRoomLinkEnabled = true, isStatusChanged = false)

        every {
            userConfigRepository.observeGuestRoomLinkFeatureFlag()
        }.returns(flowOf(Either.Right(expectedStatus)))

        val result = observeGuestRoomLinkFeatureFlag()

        assertEquals(expectedStatus, result.first())
    }
}
