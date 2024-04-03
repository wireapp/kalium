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
package com.wire.kalium.logic.feature.message.ephemeral

import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteEphemeralMessagesAfterEndDateUseCaseTest {

    @Test
    fun givenDeleteEphemeralMessagesUseCase_whenInvoking_ThenEphemeralHandlerIsCalled() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteSelfDeletionMessagesFromEndDateSuccess()
            .arrange()

        // when
        useCase.invoke()

        // then
        verify(arrangement.ephemeralMessageDeletionHandler)
            .suspendFunction(arrangement.ephemeralMessageDeletionHandler::deleteAlreadyEndedSelfDeletionMessages)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)

        fun withDeleteSelfDeletionMessagesFromEndDateSuccess() = apply {
            given(ephemeralMessageDeletionHandler)
                .suspendFunction(ephemeralMessageDeletionHandler::deleteAlreadyEndedSelfDeletionMessages)
                .whenInvoked()
                .thenReturn(Unit)
        }

        fun arrange() = this to DeleteEphemeralMessagesAfterEndDateUseCaseImpl(
            ephemeralMessageDeletionHandler = ephemeralMessageDeletionHandler
        )
    }
}
