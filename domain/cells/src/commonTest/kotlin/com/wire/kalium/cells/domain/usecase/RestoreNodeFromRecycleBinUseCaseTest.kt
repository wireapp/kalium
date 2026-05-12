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
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RestoreNodeFromRecycleBinUseCaseTest {

    @Test
    fun givenRestoreSuccessAndLocalFileAvailable_whenRestoreNode_thenLocalFileStatusIsUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreSuccess()
            .withLocalAttachment()
            .arrange()

        useCase("assetId")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus("assetId", AssetTransferStatus.SAVED_INTERNALLY)
        }
    }

    @Test
    fun givenRestoreSuccessAndLocalFileNotAvailable_whenRestoreNode_thenLocalFileStatusIsUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreSuccess()
            .withLocalAttachment(testAttachment.copy(localPath = null))
            .arrange()

        useCase("assetId")

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus("assetId", AssetTransferStatus.NOT_DOWNLOADED)
        }
    }

    @Test
    fun givenRestoreFailure_whenRestoreNode_thenLocalFileStatusIsNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreFailure()
            .arrange()

        useCase("assetId")

        verifySuspend(VerifyMode.not) {
            arrangement.attachmentsRepository.setAssetTransferStatus(any(), any())
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

        suspend fun withLocalAttachment(attachment: CellAssetContent = testAttachment) = apply {
            everySuspend { attachmentsRepository.getAttachment(any()) }.returns(attachment.right())
        }

        suspend fun arrange(): Pair<Arrangement, RestoreNodeFromRecycleBinUseCase> {

            everySuspend { attachmentsRepository.setAssetTransferStatus(any(), any()) } returns Unit.right()

            return this to RestoreNodeFromRecycleBinUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }
}

private val testAttachment = CellAssetContent(
    id = "assetId",
    versionId = "versionId",
    mimeType = "image/png",
    assetPath = "assetPath",
    assetSize = 1024,
    contentHash = "contentHash",
    localPath = "localPath",
    contentUrl = "http://contentUrl",
    previewUrl = "http://previewUrl",
    metadata = null,
    transferStatus = AssetTransferStatus.SAVED_INTERNALLY
)
