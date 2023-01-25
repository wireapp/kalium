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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CancelConnectionRequestUseCaseTest {

    @Mock
    private val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    lateinit var cancelConnectionRequestUseCase: CancelConnectionRequestUseCase

    @BeforeTest
    fun setUp() {
        cancelConnectionRequestUseCase = CancelConnectionRequestUseCaseImpl(connectionRepository)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingCancelConnectionRequestAndOk_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.CANCELLED))
            .thenReturn(Either.Right(connection))

        // when
        val resultOk = cancelConnectionRequestUseCase(userId)

        // then
        assertEquals(CancelConnectionRequestUseCaseResult.Success, resultOk)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.CANCELLED))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingCancelConnectionRequestAndFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.CANCELLED))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

        // when
        val resultFailure = cancelConnectionRequestUseCase(userId)

        // then
        assertEquals(CancelConnectionRequestUseCaseResult.Failure::class, resultFailure::class)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.CANCELLED))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
        val connection = Connection(
            "someId",
            "from",
            "lastUpdate",
            ConversationId("someId", "someDomain"),
            ConversationId("someId", "someDomain"),
            ConnectionState.CANCELLED,
            "toId",
            null
        )
    }
}
