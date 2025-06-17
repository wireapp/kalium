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
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshNodeAssetStateUseCaseTest {

    private companion object {

        private const val assetId = "assetId"
        private const val localPath = "localPath"

        private val testPreviews = listOf(
            NodePreview(
                url = "http://previewUrl",
                dimension = 720
            )
        )

        private val testNode: CellNode = CellNode(
            uuid = assetId,
            versionId = "versionId",
            path = "assetPath",
            modified = 1,
            size = 1024,
            eTag = "",
            type = "LEAF",
            isRecycled = false,
            isDraft = false,
            contentUrl = "http://contentUrl",
            contentHash = "contentHash",
            mimeType = "image/png",
            previews = emptyList(),
            ownerUserId = "ownerUserId",
            conversationId = "conversationId",
            publicLinkId = "publicLinkId",
        )
        private val testAttachment = CellAssetContent(
            id = assetId,
            versionId = "versionId",
            mimeType = "image/png",
            assetPath = "assetPath",
            assetSize = 1024,
            contentHash = "contentHash",
            localPath = localPath,
            contentUrl = "http://contentUrl",
            previewUrl = "http://previewUrl",
            metadata = null,
            transferStatus = AssetTransferStatus.SAVED_INTERNALLY
        )
    }

    @Test
    fun given_RequestSuccess_when_HashMismatch_then_LocalFileDeleted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                contentHash = "updated",
                mimeType = "no_previews"
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        assertFalse { arrangement.fileSystem.exists("localPath".toPath()) }
    }

    @Test
    fun given_RequestSuccess_when_HashMismatch_then_LocalFilePathCleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                contentHash = "updated",
                mimeType = "no_previews"
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.saveLocalPath(testAttachment.id, null)
        }.wasInvoked(once)
    }

    @Test
    fun given_RequestSuccess_when_LocalFileMissing_then_LocalFilePathCleared() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                mimeType = "no_previews"
            ))
            .withLocalAttachment()
            .withLocalFileMissing()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.saveLocalPath(testAttachment.id, null)
        }.wasInvoked(once)
    }

    @Test
    fun given_RequestSuccess_when_NodeNotRecycled_then_LocalDataUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                mimeType = "no_previews"
            ))
            .withLocalAttachment()
            .withLocalFileMissing()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.updateAttachment(assetId, testNode.contentUrl, testNode.contentHash, testNode.path)
        }.wasInvoked(once)
    }

    @Test
    fun given_CurrentAssetStatusNotFound_when_LocalDataUpdated_and_ContentHashMismatch_then_CurrentAssetStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                contentHash = "updated",
                mimeType = "no_previews"
            ))
            .withLocalAttachment(testAttachment.copy(
                transferStatus = AssetTransferStatus.NOT_FOUND,
            ))
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_DOWNLOADED)
        }.wasInvoked(once)
    }

    @Test
    fun given_CurrentAssetStatusNotFound_when_LocalDataUpToDate_then_CurrentAssetStatusUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                mimeType = "no_previews"
            ))
            .withLocalAttachment(testAttachment.copy(
                transferStatus = AssetTransferStatus.NOT_FOUND,
            ))
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.SAVED_INTERNALLY)
        }.wasInvoked(once)
    }

    @Test
    fun given_RequestSuccess_when_NodeRecycled_then_LocalFileDeleted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                mimeType = "no_previews",
                isRecycled = true,
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        assertFalse { arrangement.fileSystem.exists("localPath".toPath()) }

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_when_NodeRequestFailed_then_ErrorReturned() = runTest {
        val (_, useCase) = Arrangement()
            .withNodeResponseError()
            .arrange()

        val result = useCase.invoke(assetId)

        assertTrue { result.isLeft() }
    }

    @Test
    fun given_Asset_when_NodeNotFound_then_LocalFileDeleted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseNotFound()
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        assertFalse { arrangement.fileSystem.exists("localPath".toPath()) }

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
        }.wasInvoked(once)
    }

    @Test
    fun given_Asset_when_NodeForbidden_then_LocalFileDeleted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseForbidden()
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        assertFalse { arrangement.fileSystem.exists("localPath".toPath()) }

        coVerify {
            arrangement.attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
        }.wasInvoked(once)
    }

    @Test
    fun given_NodePreviewNotSupported_when_RefreshInvoked_then_PreviewNotRequested() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                mimeType = "no_previews",
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.cellsRepository.getPreviews(any())
        }.wasNotInvoked()
    }

    @Test
    fun given_NodeIsRecycled_when_RefreshInvoked_then_PreviewNotRequested() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                isRecycled = true,
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.cellsRepository.getPreviews(any())
        }.wasNotInvoked()
    }

    @Test
    fun given_NodePreviewsReturned_when_RefreshInvoked_then_PreviewNotRequested() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode.copy(
                previews = testPreviews,
            ))
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.cellsRepository.getPreviews(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.attachmentsRepository.savePreviewUrl(assetId, testPreviews.first().url)
        }
    }

    @Test
    fun given_NodePreviewsNotReady_when_RefreshInvoked_then_PreviewRequestRetried() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode)
            .withPreviewsNotReady()
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.cellsRepository.getPreviews(any())
        }.wasInvoked(20)
    }

    @Test
    fun given_NodePreviewsNotReady_when_RefreshInvoked_and_AssetNotFound_then_PreviewRequestNotRetried() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode)
            .withAssetNotFound()
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.cellsRepository.getPreviews(any())
        }.wasInvoked(1)
    }

    @Test
    fun given_NodePreviewsSuccess_when_RefreshInvoked_then_PreviewUrlUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNodeResponseSuccess(testNode)
            .withPreviewsReady()
            .withLocalAttachment()
            .withLocalFileAvailable()
            .arrange()

        useCase.invoke(assetId)

        coVerify {
            arrangement.attachmentsRepository.savePreviewUrl(assetId, testPreviews.first().url)
        }.wasInvoked(1)
    }

    private class Arrangement {

        val cellsRepository = mock(CellsRepository::class)
        val attachmentsRepository = mock(CellAttachmentsRepository::class)

        val fileSystem = FakeFileSystem()

        suspend fun withNodeResponseSuccess(node: CellNode = testNode) = apply {
            coEvery { cellsRepository.getNode(any()) }.returns(node.right())
        }

        suspend fun withNodeResponseError() = apply {
            coEvery { cellsRepository.getNode(any()) }.returns(
                NetworkFailure.ServerMiscommunication(IllegalStateException("Test")).left()
            )
        }

        suspend fun withNodeResponseNotFound() = apply {
            coEvery { cellsRepository.getNode(any()) }.returns(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.ServerError(
                        ErrorResponse(HttpStatusCode.NotFound.value, "Test", "")
                    )
                ).left()
            )
        }

        suspend fun withNodeResponseForbidden() = apply {
            coEvery { cellsRepository.getNode(any()) }.returns(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.ServerError(
                        ErrorResponse(HttpStatusCode.Forbidden.value, "Test", "")
                    )
                ).left()
            )
        }

        suspend fun withLocalAttachment(attachment: CellAssetContent = testAttachment) = apply {
            coEvery { attachmentsRepository.getAttachment(any()) }.returns(attachment.right())
        }

        fun withLocalFileAvailable() = apply {
            fileSystem.write(localPath.toPath()) { "".toByteArray()}
        }

        fun withLocalFileMissing() = apply {
            fileSystem.delete(localPath.toPath())
        }

        suspend fun withPreviewsNotReady() = apply {
            coEvery { cellsRepository.getPreviews(any()) }.returns(emptyList<NodePreview>().right())
        }

        suspend fun withPreviewsReady() = apply {
            coEvery { cellsRepository.getPreviews(any()) }.returns(testPreviews.right())
        }

        suspend fun withAssetNotFound() = apply {
            coEvery { cellsRepository.getPreviews(any()) }.returns(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.ServerError(
                        ErrorResponse(HttpStatusCode.NotFound.value, "Test", "")
                    )
                ).left()
            )
        }

        suspend fun arrange(): Pair<Arrangement, RefreshCellAssetStateUseCaseImpl> {

            coEvery { attachmentsRepository.setAssetTransferStatus(any(), any()) }.returns(Unit.right())
            coEvery { attachmentsRepository.saveLocalPath(any(), any()) }.returns(Unit.right())
            coEvery { attachmentsRepository.savePreviewUrl(any(), any()) }.returns(Unit.right())
            coEvery { attachmentsRepository.updateAttachment(any(), any(), any(), any()) }.returns(Unit.right())

            return this to RefreshCellAssetStateUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
                fileSystem = fileSystem
            )
        }
    }
}
