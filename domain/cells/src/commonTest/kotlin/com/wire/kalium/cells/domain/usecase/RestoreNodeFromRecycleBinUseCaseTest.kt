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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus("assetId", AssetTransferStatus.SAVED_INTERNALLY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRestoreSuccessAndLocalFileNotAvailable_whenRestoreNode_thenLocalFileStatusIsUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreSuccess()
            .withLocalAttachment(testAttachment.copy(localPath = null))
            .arrange()

        useCase("assetId")

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus("assetId", AssetTransferStatus.NOT_DOWNLOADED)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRestoreFailure_whenRestoreNode_thenLocalFileStatusIsNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeRestoreFailure()
            .arrange()

        useCase("assetId")

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement {

        val cellsRepository = mock(CellsRepository::class)
        val attachmentsRepository = mock(CellAttachmentsRepository::class)

        suspend fun withNodeRestoreSuccess() = apply {
            coEvery { cellsRepository.restoreNode(any()) } returns Unit.right()
        }

        suspend fun withNodeRestoreFailure() = apply {
            coEvery { cellsRepository.restoreNode(any()) } returns NetworkFailure.NoNetworkConnection(null).left()
        }

        suspend fun withLocalAttachment(attachment: CellAssetContent = testAttachment) = apply {
            coEvery { attachmentsRepository.getAttachment(any()) }.returns(attachment.right())
        }

        suspend fun arrange(): Pair<Arrangement, RestoreNodeFromRecycleBinUseCase> {

            coEvery { attachmentsRepository.setAssetTransferStatus(any(), any()) } returns Unit.right()

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
