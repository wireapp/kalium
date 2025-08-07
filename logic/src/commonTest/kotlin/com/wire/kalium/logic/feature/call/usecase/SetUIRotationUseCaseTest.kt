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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.util.mockPlatformRotation
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SetUIRotationUseCaseTest {

    @Test
    fun givenFlowManagerService_whenUseCaseIsInvoked_thenInvokeSetUIRotationOnce() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange()
        val rotation = mockPlatformRotation()

        // when
        useCase(rotation)

        // then
        coVerify {
            arrangement.flowManagerService.setUIRotation(rotation)
        }.wasInvoked(once)
    }

    private class Arrangement {
        val flowManagerService = mock(FlowManagerService::class)
        fun arrange() = this to SetUIRotationUseCase(flowManagerService = flowManagerService)
    }
}
