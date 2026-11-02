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

class RestoreNodeFromRecycleBinUseCaseTest {

    @Test
    fun givenRestoreSuccess_whenRestoreNode_thenAttachmentStatusIsRefreshed() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreSuccess()
            .arrange()

        useCase("assetId")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.refreshRestoredAttachmentStatus(listOf("assetId"))
        }
    }

    @Test
    fun givenRestoreFailure_whenRestoreNode_thenAttachmentStatusIsNotRefreshed() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreFailure()
            .arrange()

        useCase("assetId")

        verifySuspend(VerifyMode.not) {
            arrangement.attachmentsRepository.refreshRestoredAttachmentStatus(any())
        }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)

        suspend fun withNodeRestoreSuccess() = apply {
            everySuspend { cellsRepository.restoreNode(any()) } returns Unit.right()
        }

        suspend fun withNodeRestoreFailure() = apply {
            everySuspend { cellsRepository.restoreNode(any()) } returns NetworkFailure.NoNetworkConnection(null).left()
        }

        suspend fun arrange(): Pair<Arrangement, RestoreNodeFromRecycleBinUseCase> {

            everySuspend { attachmentsRepository.refreshRestoredAttachmentStatus(any()) } returns Unit.right()

            return this to RestoreNodeFromRecycleBinUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }
}
