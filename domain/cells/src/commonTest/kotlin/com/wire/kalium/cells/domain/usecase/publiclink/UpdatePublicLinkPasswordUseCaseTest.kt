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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UpdatePublicLinkPasswordUseCaseTest {

    @Test
    fun given_null_password_when_update_then_password_remove_called() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemovePasswordSuccess()
            .arrange()

        useCase("link", null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.removePublicLinkPassword("link")
        }
    }

    @Test
    fun given_empty_password_when_update_then_password_remove_called() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemovePasswordSuccess()
            .arrange()

        useCase("link", "")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.removePublicLinkPassword("link")
        }
    }

    @Test
    fun given_null_password_when_update_success_then_password_is_cleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemovePasswordSuccess()
            .arrange()

        useCase("link", null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.clearPublicLinkPassword("link")
        }
    }

    @Test
    fun given_null_password_when_update_failed_then_password_is_not_cleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemovePasswordFailure()
            .arrange()

        useCase("link", null)

        verifySuspend(VerifyMode.not) {
            arrangement.repository.clearPublicLinkPassword("link")
        }
    }

    @Test
    fun given_not_empty_password_when_update_then_password_update_called() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdatePasswordSuccess()
            .arrange()

        useCase("link", "password")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.updatePublicLinkPassword("link", "password")
        }
    }

    @Test
    fun given_not_empty_password_when_update_success_then_password_saved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdatePasswordSuccess()
            .arrange()

        useCase("link", "password")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.savePublicLinkPassword("link", "password")
        }
    }

    @Test
    fun given_not_empty_password_when_update_fails_then_password_not_saved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdatePasswordFailure()
            .arrange()

        useCase("link", "password")

        verifySuspend(VerifyMode.not) {
            arrangement.repository.savePublicLinkPassword("link", "password")
        }
    }

    private class Arrangement() {

        val repository = mock<CellsRepository>(mode = MockMode.autoUnit)

        suspend fun withUpdatePasswordSuccess() = apply {
            everySuspend { repository.updatePublicLinkPassword(any(), any()) } returns Unit.right()
        }

        suspend fun withUpdatePasswordFailure() = apply {
            everySuspend { repository.updatePublicLinkPassword(any(), any()) } returns
                    NetworkFailure.ServerMiscommunication(IllegalStateException()).left()
        }

        suspend fun withRemovePasswordSuccess() = apply {
            everySuspend { repository.removePublicLinkPassword(any()) } returns Unit.right()
        }

        suspend fun withRemovePasswordFailure() = apply {
            everySuspend { repository.removePublicLinkPassword(any()) } returns
                    NetworkFailure.ServerMiscommunication(IllegalStateException()).left()
        }

        fun arrange() = this to UpdatePublicLinkPasswordUseCaseImpl(
            repository = repository
        )
    }
}
