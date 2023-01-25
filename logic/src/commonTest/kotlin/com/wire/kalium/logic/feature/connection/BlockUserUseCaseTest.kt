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
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlockUserUseCaseTest {

    @Test
    fun givenBlockingFailed_thenBlockResultIsError() = runTest {
        val (_, blockUser) = Arrangement()
            .withBlockResult(Either.Left(InvalidMappingFailure))
            .arrange()

        val result = blockUser(TestUser.USER_ID)

        assertTrue(result is BlockUserResult.Failure)
    }

    @Test
    fun givenBlockingSuccessful_thenBlockResultIsSuccess() = runTest {
        val (_, blockUser) = Arrangement()
            .withBlockResult(Either.Right(TestConnection.CONNECTION))
            .arrange()

        val result = blockUser(TestUser.USER_ID)

        assertTrue(result is BlockUserResult.Success)
    }

    private class Arrangement {
        @Mock
        val connectionRepository: ConnectionRepository = mock(classOf<ConnectionRepository>())

        val blockUser = BlockUserUseCaseImpl(connectionRepository)

        fun withBlockResult(result: Either<CoreFailure, Connection>) = apply {
            given(connectionRepository)
                .suspendFunction(connectionRepository::updateConnectionStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to blockUser
    }
}
