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

import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.data.call.TestVideoType
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
class SetTestVideoTypeUseCaseTest {

        private val callManager = mock(CallManager::class)

    private lateinit var setTestVideoType: SetTestVideoTypeUseCase

    @BeforeTest
    fun setup() = runBlocking {
        setTestVideoType = SetTestVideoTypeUseCase(lazy { callManager })

        coEvery {
            callManager.setTestVideoType(any())
        }.returns(Unit)
    }

    @Test
    fun givenWhenSetTestVideoType_thenUpdateTestVideoType() = runTest {
        setTestVideoType(TestVideoType.FAKE)

        coVerify {
            callManager.setTestVideoType(eq(TestVideoType.FAKE))
        }.wasInvoked(once)
    }
}
