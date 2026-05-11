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
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteNodeAssetUseCaseTest {

    private companion object {
        private const val assetId = "assetId"
        private val localPath: String = "localPath"
    }

    @Test
    fun given_Asset_whenDeleteSuccess_thenTransferStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessDelete()
            .arrange()

        useCase(assetId, localPath)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
        }
    }

    @Test
    fun given_Asset_whenDeleteSuccess_then_StandaloneAssetRemoved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessDelete()
            .arrange()

        useCase(assetId, localPath)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.deleteStandaloneAsset(assetId)
        }
    }

    @Test
    fun given_LocalPath_when_DeleteSuccess_thenLocalFileRemoved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withSuccessDelete()
            .arrange()
        assertTrue { arrangement.fileSystem.exists(localPath.toPath()) }

        useCase(assetId, localPath)

        assertFalse { arrangement.fileSystem.exists(localPath.toPath()) }
    }

    @Test
    fun given_Asset_whenDeleteFailed_thenErrorReturned() = runTest {
        val (_, useCase) = Arrangement()
            .withDeleteFailure()
            .arrange()

        val result = useCase(assetId, localPath)

        assertTrue { result.isLeft() }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)
        val fileSystem = FakeFileSystem()

        suspend fun withSuccessDelete() = apply {
            everySuspend { cellsRepository.deleteFile(any(), any()) }.returns(Unit.right())
        }

        suspend fun withDeleteFailure() = apply {
            everySuspend { cellsRepository.deleteFile(any(), any()) }.returns(
                NetworkFailure.ServerMiscommunication(IllegalStateException("Test")).left()
            )
        }

        fun withLocalFileAvailable() = apply {
            fileSystem.write(localPath.toPath()) { "".encodeToByteArray()}
        }

        suspend fun arrange(): Pair<Arrangement, DeleteCellAssetUseCaseImpl> {

            everySuspend { attachmentsRepository.setAssetTransferStatus(any(), any()) }.returns(Unit.right())

            everySuspend { attachmentsRepository.deleteStandaloneAsset(any()) }.returns(Unit.right())

            return this to DeleteCellAssetUseCaseImpl(
                cellsRepository = cellsRepository,
                cellAttachmentsRepository = attachmentsRepository,
                fileSystem = fileSystem,
            )
        }
    }
}
