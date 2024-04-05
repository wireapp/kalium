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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.FlowManagerService
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FlipToFrontCameraUseCaseTest {

    @Mock
    private val flowManagerService = mock(classOf<FlowManagerService>())

    private lateinit var flipToFrontCamera: FlipToFrontCameraUseCase

    @BeforeTest
    fun setup() {
        flipToFrontCamera = FlipToFrontCameraUseCase(flowManagerService)
    }

    @Test
    fun givenFlowManagerService_whenUseCaseCaseIsInvoked_thenInvokeFlipToFrontCameraOnce() = runTest {
        coEvery {
            flowManagerService.flipToFrontCamera(eq(conversationId))
        }.returns(Unit)

        flipToFrontCamera(conversationId)

        coVerify {
            flowManagerService.flipToFrontCamera(eq(conversationId))
        }.wasInvoked(once)
    }

    companion object {
        val conversationId = ConversationId("someone", "wire.com")
    }
}
