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
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
class SetTestVideoTypeUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var setTestVideoType: SetTestVideoTypeUseCase

    @BeforeTest
    fun setup() {
        setTestVideoType = SetTestVideoTypeUseCase(lazy { callManager })

        given(callManager)
            .suspendFunction(callManager::setTestVideoType)
            .whenInvokedWith(any())
            .thenDoNothing()
    }

    @Test
    fun givenWhenSetTestVideoType_thenUpdateTestVideoType() = runTest {
        setTestVideoType(TestVideoType.FAKE)

        verify(callManager)
            .suspendFunction(callManager::setTestVideoType)
            .with(eq(TestVideoType.FAKE))
            .wasInvoked(once)
    }
}

