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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.functional.right
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RenameNodeUseCaseTest {

    @Test
    fun givenRepository_whenUseCaseIsCalled_thenInvokeMoveNodeWithSpecificParamsForRenamingOnce() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessRename()
            .arrange()

        useCase.invoke(
            uuid = "uuid",
            path = "somePath/someFile.txt",
            newName = "newName.jpg"
        )

        coVerify {
            arrangement.cellsRepository.renameNode(
                uuid = ("uuid"),
                path = ("somePath/someFile.txt"),
                targetPath = ("somePath/newName.jpg")
            )
        }.wasInvoked(once)
    }

    private class Arrangement {

        val cellsRepository = mock(CellsRepository::class)

        suspend fun withSuccessRename() = apply {
            coEvery { cellsRepository.renameNode(any(), any(), any()) }.returns(Unit.right())
        }

        suspend fun arrange() = this to RenameNodeUseCaseImpl(
            cellsRepository = cellsRepository
        )
    }
}
