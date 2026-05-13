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
package com.wire.kalium.cells.domain.usecase

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveNodeUseCaseTest {

    @Test
    fun given_repository_succeedsWhen_invoke_is_calledThen_return_Right_Unit() = runTest {
        val uuid = "abc-123"
        val path = "/old/path"
        val targetPath = "/new/path"
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Unit.right())
            .arrange()

        val result = useCase.invoke(uuid, path, targetPath)

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.moveNode(uuid, path, targetPath)
        }
    }


    @Test
    fun given_repository_failsWhen_invoke_is_calledThen_return_Left_failure() = runTest {
        val uuid = "xyz-789"
        val path = "/bad/path"
        val targetPath = "/invalid/target"
        val failure = NetworkFailure.FeatureNotSupported
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(failure.left())
            .arrange()

        val result = useCase.invoke(uuid, path, targetPath)

        assertEquals(failure.left(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.moveNode(uuid, path, targetPath)
        }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { cellsRepository.moveNode(any(), any(), any()) }.returns(result)
        }

        fun arrange() = this to MoveNodeUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }
}
