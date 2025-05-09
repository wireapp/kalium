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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadNodeFileUseCaseTest {

    private companion object {
        private const val assetId = "assetid"
        private const val assetPath = "assetPath"
        private val outFilePath: Path = "outFilePath".toPath()
        private const val assetSize: Long = 1024L
        private const val remoteFilePath: String = "remoteFilePath"
        private val progressListener: (Long) -> Unit = {}
    }

    @Test
    fun given_Asset_whenAssetPathFound_thenDownloadStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.cellsRepository.downloadFile(outFilePath, remoteFilePath, progressListener)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_whenDownloadStarted_thenTransferStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.DOWNLOAD_IN_PROGRESS)
        }.wasInvoked(once)
    }

    @Test
    fun given_AssetRemotePath_whenDownloadStarted_thenRemotePathPreferred() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.cellsRepository.downloadFile(outFilePath, remoteFilePath, progressListener)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_whenNoPathAvailable_thenErrorReturned() = runTest {
        val (_, useCase) = Arrangement()
            .withAssetPathMissing()
            .withDownloadSuccess()
            .arrange()

        val result = useCase(assetId, outFilePath, assetSize, null, progressListener)

        assertTrue { result.isLeft() }
    }

    @Test
    fun given_Asset_whenDownloadSuccess_thenStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.SAVED_INTERNALLY)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_whenDownloadSuccess_thenLocalPathUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.attachmentsRepository.saveLocalPath(assetId, outFilePath.toString())
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_whenDownloadFailed_thenStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetPath()
            .withDownloadFailure()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.FAILED_DOWNLOAD)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_whenAssetNotFound_thenRemotePathUsedForDownload() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetNotFound()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.cellsRepository.downloadFile(outFilePath, remoteFilePath, progressListener)
        }.wasInvoked(once)
    }

    @Test
    fun given_AssetNotFound_whenRemotePathNotAvailable_thenErrorReturned() = runTest {
        val (_, useCase) = Arrangement()
            .withAssetNotFound()
            .withDownloadSuccess()
            .arrange()

        val result = useCase(assetId, outFilePath, assetSize, null, progressListener)

        assertTrue { result.isLeft() }
    }

    @Test
    fun given_Asset_whenStandaloneDownloadSuccess_thenStandaloneAssetPathSaved() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetNotFound()
            .withDownloadSuccess()
            .arrange()

        useCase(assetId, outFilePath, assetSize, remoteFilePath, progressListener)

        coVerify {
            arrangement.attachmentsRepository.saveStandaloneAssetPath(assetId, outFilePath.toString(), assetSize)
        }.wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val cellsRepository = mock(CellsRepository::class)

        @Mock
        val attachmentsRepository = mock(CellAttachmentsRepository::class)

        suspend fun withAssetPath() = apply {
            coEvery { attachmentsRepository.getAssetPath(any()) }.returns(assetPath.right())
        }

        suspend fun withAssetPathMissing() = apply {
            coEvery { attachmentsRepository.getAssetPath(any()) }.returns(null.right())
        }

        suspend fun withAssetNotFound() = apply {
            coEvery { attachmentsRepository.getAssetPath(any()) }.returns(StorageFailure.DataNotFound.left())
        }

        suspend fun withDownloadSuccess() = apply {
            coEvery { cellsRepository.downloadFile(any(), any(), any()) }.returns(Unit.right())
        }

        suspend fun withDownloadFailure() = apply {
            coEvery { cellsRepository.downloadFile(any(), any(), any()) }.returns(
                NetworkFailure.ServerMiscommunication(IllegalStateException("Test")).left()
            )
        }

        suspend fun arrange(): Pair<Arrangement, DownloadCellFileUseCaseImpl> {

            coEvery { attachmentsRepository.setAssetTransferStatus(any(), any()) }.returns(Unit.right())
            coEvery { attachmentsRepository.saveLocalPath(any(), any()) }.returns(Unit.right())
            coEvery { attachmentsRepository.saveStandaloneAssetPath(any(), any(), any()) }.returns(Unit.right())

            return this to DownloadCellFileUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }
}
