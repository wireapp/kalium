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

class DeletePublicLinkUseCaseTest {

    @Test
    fun given_success_when_delete_link_then_password_cleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteLinkSuccess()
            .arrange()

        useCase("link")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.clearPublicLinkPassword("link")
        }
    }

    @Test
    fun given_failed_when_delete_link_then_password_not_cleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteLinkFailure()
            .arrange()

        useCase("link")

        verifySuspend(VerifyMode.not) {
            arrangement.repository.clearPublicLinkPassword("link")
        }
    }

    private class Arrangement() {

        val repository = mock<CellsRepository>(mode = MockMode.autoUnit)

        suspend fun withDeleteLinkSuccess() = apply {
            everySuspend { repository.deletePublicLink(any()) } returns Unit.right()
        }

        suspend fun withDeleteLinkFailure() = apply {
            everySuspend { repository.deletePublicLink(any()) } returns
                    NetworkFailure.ServerMiscommunication(IllegalStateException()).left()
        }

        fun arrange() = this to DeletePublicLinkUseCaseImpl(
            cellsRepository = repository
        )
    }
}
