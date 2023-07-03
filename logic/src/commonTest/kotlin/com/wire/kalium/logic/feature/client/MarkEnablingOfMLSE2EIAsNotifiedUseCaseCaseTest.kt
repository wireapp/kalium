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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.MarkEnablingOfMLSE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkEnablingOfMLSE2EIAsNotifiedUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

class MarkEnablingOfMLSE2EIAsNotifiedUseCaseCaseTest {

    @Test
    fun whenMarkAsNotifiedIsCalled_thenSnoozeIsCalled() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase()

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::snoozeE2EINotification)
            .with(eq(MarkEnablingOfMLSE2EIAsNotifiedUseCaseImpl.SNOOZE_MLS_ENABLE_CHANGE_MS))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        init {
            given(userConfigRepository)
                .function(userConfigRepository::snoozeE2EINotification)
                .whenInvokedWith(any<Duration>())
                .thenReturn(Either.Right(Unit))
        }

        private var markMLSE2EIEnableChangeAsNotified: MarkEnablingOfMLSE2EIAsNotifiedUseCase =
            MarkEnablingOfMLSE2EIAsNotifiedUseCaseImpl(userConfigRepository)

        fun arrange() = this to markMLSE2EIEnableChangeAsNotified
    }
}
