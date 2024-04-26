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
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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
        coVerify {
            arrangement.ephemeralMessageDeletionHandler.deleteAlreadyEndedSelfDeletionMessages()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val ephemeralMessageDeletionHandler = mock(EphemeralMessageDeletionHandler::class)

        suspend fun withDeleteSelfDeletionMessagesFromEndDateSuccess() = apply {
            coEvery {
                ephemeralMessageDeletionHandler.deleteAlreadyEndedSelfDeletionMessages()
            }.returns(Unit)
        }

        fun arrange() = this to DeleteEphemeralMessagesAfterEndDateUseCaseImpl(
            ephemeralMessageDeletionHandler = ephemeralMessageDeletionHandler
        )
    }
}
