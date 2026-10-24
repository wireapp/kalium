/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.NodeLocator
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveNodesUseCaseTest {

    @Test
    fun givenRepositorySucceeds_whenInvoked_thenMoveAllNodesInOneRequestAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Unit.right())
            .arrange()

        val result = useCase(NODES, TARGET_PATH)

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.moveNodes(NODES, TARGET_PATH)
        }
    }

    @Test
    fun givenRepositoryFails_whenInvoked_thenReturnLeftFailure() = runTest {
        val failure = NetworkFailure.FeatureNotSupported
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(failure.left())
            .arrange()

        val result = useCase(NODES, TARGET_PATH)

        assertEquals(failure.left(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.moveNodes(NODES, TARGET_PATH)
        }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)

        suspend fun withRepositoryReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { cellsRepository.moveNodes(any(), any()) }.returns(result)
        }

        fun arrange() = this to MoveNodesUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }

    private companion object {
        val NODES = listOf(
            NodeLocator(uuid = "uuid-1", path = "conversation/folder/file.txt"),
            NodeLocator(uuid = "uuid-2", path = "conversation/folder/subfolder"),
        )
        const val TARGET_PATH = "conversation/destination"
    }
}
