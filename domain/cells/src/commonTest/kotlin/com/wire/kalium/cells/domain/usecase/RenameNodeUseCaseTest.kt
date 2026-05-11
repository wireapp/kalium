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
import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.right
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RenameNodeUseCaseTest {

    @Test
    fun givenRepository_whenUseCaseIsCalled_thenInvokeMoveNodeWithSpecificParamsForRenamingOnce() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessPreCheck()
            .withSuccessRename()
            .arrange()

        useCase.invoke(
            uuid = "uuid",
            path = "somePath/someFile.txt",
            newName = "newName.jpg"
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.renameNode(
                uuid = ("uuid"),
                path = ("somePath/someFile.txt"),
                targetPath = ("somePath/newName.jpg")
            )
        }
    }

    @Test
    fun givenRepository_whenRenameIsSuccess_thenRemotePathInDatabaseIsUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessPreCheck()
            .withSuccessRename()
            .arrange()

        useCase.invoke(
            uuid = "uuid",
            path = "somePath/someFile.txt",
            newName = "newName.jpg"
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.updateAssetPath(
                assetId = ("uuid"),
                remotePath = ("somePath/newName.jpg")
            )
        }
    }

    @Test
    fun givenRepository_whenFileAlreadyExist_thenFileExistsErrorReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessPreCheck()
            .withFileAlreadyExist()
            .arrange()

        val result = useCase.invoke(
            uuid = "uuid",
            path = "somePath/someFile.txt",
            newName = "newName.jpg"
        )

        verifySuspend(VerifyMode.not) {
            arrangement.attachmentsRepository.updateAssetPath(
                assetId = ("uuid"),
                remotePath = ("somePath/newName.jpg")
            )
        }

        assertTrue(result is Either.Left<RenameNodeFailure>)
        assertTrue(result.value is RenameNodeFailure.FileAlreadyExists)
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)

        suspend fun withSuccessRename() = apply {
            everySuspend { cellsRepository.renameNode(any(), any(), any()) }.returns(Unit.right())
        }

        suspend fun withSuccessPreCheck() = apply {
            everySuspend { cellsRepository.preCheck(any()) }.returns(PreCheckResult.Success.right())
        }

        suspend fun withFileAlreadyExist() = apply {
            everySuspend { cellsRepository.preCheck(any()) }.returns(PreCheckResult.FileExists("").right())
        }

        suspend fun arrange(): Pair<Arrangement, RenameNodeUseCaseImpl> {

            everySuspend { attachmentsRepository.updateAssetPath(any(), any()) }.returns(Unit.right())

            return this to RenameNodeUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }
}
