/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase.publiclink

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CreatePublicLinkPasswordUseCaseTest {

    @Test
    fun given_success_when_create_password_then_password_saved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCreatePasswordSuccess()
            .arrange()

        useCase("link", "password")

        coVerify {
            arrangement.repository.savePublicLinkPassword("link", "password")
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun given_failed_when_create_password_then_password_not_saved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCreatePasswordFailure()
            .arrange()

        useCase("link", "password")

        coVerify() {
            arrangement.repository.savePublicLinkPassword("link", "password")
        }.wasInvoked(exactly = 0)
    }

    private class Arrangement() {

        val repository = mock(CellsRepository::class)

        suspend fun withCreatePasswordSuccess() = apply {
            coEvery { repository.createPublicLinkPassword(any(), any()) } returns Unit.right()
        }

        suspend fun withCreatePasswordFailure() = apply {
            coEvery { repository.createPublicLinkPassword(any(), any()) } returns
                    NetworkFailure.ServerMiscommunication(IllegalStateException()).left()
        }

        fun arrange() = this to CreatePublicLinkPasswordUseCaseImpl(
            repository = repository
        )
    }
}
