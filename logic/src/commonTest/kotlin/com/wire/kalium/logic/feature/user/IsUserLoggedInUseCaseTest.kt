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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.AccessRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsUserLoggedInUseCaseTest {

    @Test
    fun givenFailure_whenRunningUseCase_thenReturnFalse() = runTest {
        val (_, isUserLoggedIn) = Arrangement()
            .withFailure()
            .arrange()

        val result = isUserLoggedIn.invoke()

        assertEquals(false, result)
    }

    @Test
    fun givenNoUserLoggedIn_whenRunningUseCase_thenReturnFalse() = runTest {
        val (_, isUserLoggedIn) = Arrangement()
            .withNoUserLoggedIn()
            .arrange()

        val result = isUserLoggedIn.invoke()

        assertEquals(false, result)
    }

    @Test
    fun givenRepositoryReturnsNull_whenRunningUseCase_thenReturnFalse() = runTest {
        val (_, isUserLoggedIn) = Arrangement()
            .withNullValue()
            .arrange()

        val result = isUserLoggedIn.invoke()

        assertEquals(false, result)
    }

    @Test
    fun givenWithUsersLoggedIn_whenRunningUseCase_thenReturnTrue() = runTest {
        val (_, isUserLoggedIn) = Arrangement()
            .withUsersLoggedIn()
            .arrange()

        val result = isUserLoggedIn.invoke()

        assertEquals(true, result)
    }

    private class Arrangement {

        @Mock
        val accessRepository: AccessRepository = mock(AccessRepository::class)

        fun withFailure() = apply {
            given(accessRepository)
                .suspendFunction(accessRepository::loggedInUsers)
                .whenInvoked()
                .then { Either.Left(StorageFailure.DataNotFound) }
        }

        fun withNoUserLoggedIn() = apply {
            given(accessRepository)
                .suspendFunction(accessRepository::loggedInUsers)
                .whenInvoked()
                .then { Either.Right(0) }
        }

        fun withUsersLoggedIn() = apply {
            given(accessRepository)
                .suspendFunction(accessRepository::loggedInUsers)
                .whenInvoked()
                .then { Either.Right(3) }
        }

        fun withNullValue() = apply {
            given(accessRepository)
                .suspendFunction(accessRepository::loggedInUsers)
                .whenInvoked()
                .then { Either.Right(null) }
        }

        fun arrange() = this to IsUserLoggedInUseCaseImpl(accessRepository)
    }
}