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
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.SelfDeletingMessagesStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.selfdeletingMessages.ObserveSelfDeletingMessagesUseCase
import com.wire.kalium.logic.feature.selfdeletingMessages.ObserveSelfDeletingMessagesUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ObserveSelfDeletingMessagesUseCaseTest {

    @Mock
    val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

    lateinit var observeSelfDeletingMessagesFlag: ObserveSelfDeletingMessagesUseCase

    @BeforeTest
    fun setUp() {
        observeSelfDeletingMessagesFlag = ObserveSelfDeletingMessagesUseCaseImpl(userConfigRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryEmitsFailure_whenObservingSelfDeletingFlag_thenEmitStatusWithNullValues() = runTest {
        given(userConfigRepository).invocation { observeSelfDeletingMessagesStatus() }
            .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))

        val result = observeSelfDeletingMessagesFlag()

        assertFalse(result.first().isEnabled)
        assertNull(result.first().isStatusChanged)
        assertNull(result.first().enforcedTimeoutInSeconds)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRepositoryEmitsValidValues_whenRunningUseCase_thenEmitThoseValidValues() = runTest {
        val expectedStatus = SelfDeletingMessagesStatus(isEnabled = true, isStatusChanged = false, enforcedTimeoutInSeconds = null)

        given(userConfigRepository).invocation { observeSelfDeletingMessagesStatus() }.thenReturn(flowOf(Either.Right(expectedStatus)))

        val result = observeSelfDeletingMessagesFlag()

        assertEquals(expectedStatus, result.first())
    }
}
