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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SendConnectionRequestUseCaseTest {

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequest_thenShouldReturnsASuccessResult() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withCreateConnectionResult(Either.Right(Unit))
            .arrange()

        // when
        val resultOk = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Success, resultOk)
        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::sendUserConnection)
            .with(eq(userId))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequestFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withCreateConnectionResult(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))
            .arrange()

        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.GenericFailure::class, resultFailure::class)
        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::sendUserConnection)
            .with(eq(userId))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingAndFailsByFederationDenied_thenShouldReturnsAFederationDenied() = runTest {
        // given
        val (arrangement, sendConnectionRequestUseCase) = Arrangement()
            .withCreateConnectionResult(
                Either.Left(NetworkFailure.FederatedBackendFailure.FederationDenied("federation-denied"))
            )
            .arrange()
        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure.FederationDenied::class, resultFailure::class)
        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::sendUserConnection)
            .with(eq(userId))
            .wasInvoked(once)
    }

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        fun withCreateConnectionResult(result: Either<CoreFailure, Unit>) = apply {
            given(connectionRepository)
                .suspendFunction(connectionRepository::sendUserConnection)
                .whenInvokedWith(eq(userId))
                .thenReturn(result)
        }

        fun arrange() = this to SendConnectionRequestUseCaseImpl(connectionRepository)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}
