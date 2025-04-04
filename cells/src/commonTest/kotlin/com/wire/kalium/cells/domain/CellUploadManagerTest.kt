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
package com.wire.kalium.cells.domain

import app.cash.turbine.test
import com.wire.kalium.cells.data.CellUploadManagerImpl
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CellUploadManagerTest {

    private companion object {
        val assetPath = "path".toPath()
        const val assetSize = 1000L
        const val fileName = "testfile.test"
        const val suggestedFileName = "testfile-1.test"
        val destNodePath = "wire-cells-android/$fileName"
        val suggestedDestNodePath = "wire-cells-android/$suggestedFileName"
    }

    @Test
    fun given_new_file_pre_check_new_node_returned() = runTest {

        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadSuccess()
            .arrange()

        val result = uploadManager.upload(assetPath, assetSize, destNodePath).getOrNull()

        assertEquals(destNodePath, result?.path)
    }

    @Test
    fun given_existing_file_pre_check_new_node_with_suggested_name_returned() = runTest {

        val (_, uploadManager) = Arrangement()
            .withPreCheckFileExists(suggestedDestNodePath)
            .withUploadSuccess()
            .arrange()

        val result = uploadManager.upload(assetPath, assetSize, destNodePath).getOrNull()

        assertEquals(suggestedDestNodePath, result?.path)
    }

    @Test
    fun given_pre_check_failure_error_is_returned() = runTest {

        val (_, uploadManager) = Arrangement()
            .withPreCheckFailed()
            .withUploadSuccess()
            .arrange()

        val result = uploadManager.upload(assetPath, assetSize, destNodePath)

        assertTrue { result.isLeft() }
    }

    @Test
    fun given_success_pre_check_file_upload_is_started() = runTest {
        val (arrangement, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadSuccess()
            .arrange()

        uploadManager.upload(assetPath, assetSize, destNodePath)

        advanceTimeBy(1)

        coVerify {
            arrangement.repository.uploadFile(any(), any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun given_success_upload_upload_complete_event_is_emitted() = runTest {
        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadSuccess()
            .arrange()

        val node = uploadManager.upload(assetPath, assetSize, destNodePath).getOrFail { error("") }

        uploadManager.observeUpload(node.uuid)?.test {
            val uploadEvent = awaitItem()
            assertEquals(CellUploadEvent.UploadCompleted, uploadEvent)
        }
    }

    @Test
    fun given_success_upload_upload_job_is_removed_from_upload_manager() = runTest {
        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadSuccess()
            .arrange()

        val node = uploadManager.upload(assetPath, assetSize, destNodePath).getOrFail { error("") }

        advanceTimeBy(1)

        assertFalse(uploadManager.isUploading(node.uuid))
    }

    @Test
    fun given_failed_upload_upload_error_event_is_emitted() = runTest {
        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadFailed()
            .arrange()

        val node = uploadManager.upload(assetPath, assetSize, destNodePath).getOrFail { error("") }

        uploadManager.observeUpload(node.uuid)?.test {
            val uploadEvent = awaitItem()
            assertEquals(CellUploadEvent.UploadError, uploadEvent)
        }
    }

    @Test
    fun given_failed_upload_upload_job_error_flag_is_set() = runTest {
        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadFailed()
            .arrange()

        val node = uploadManager.upload(assetPath, assetSize, destNodePath).getOrFail { error("") }

        advanceTimeBy(1)

        assertTrue(uploadManager.getUploadInfo(node.uuid)?.uploadFailed == true)
    }

    @Test
    fun given_upload_progress_is_updated_then_progress_event_is_emitted() = runTest {
        val (_, uploadManager) = Arrangement()
            .withPreCheckSuccess()
            .withUploadSuccess()
            .arrange(TestRepository())

        val node = uploadManager.upload(assetPath, assetSize, destNodePath).getOrFail { error("") }

        uploadManager.observeUpload(node.uuid)?.test {
            val uploadEvent = awaitItem()
            assertEquals(CellUploadEvent.UploadProgress(0.5f), uploadEvent)
        }
    }

    private class Arrangement(val uploadScope: CoroutineScope) {

        @Mock
        val repository = mock(CellsRepository::class)

        suspend fun withPreCheckFileExists(suggestedName: String) = apply {
            coEvery { repository.preCheck(any()) }.returns(PreCheckResult.FileExists(suggestedName).right())
        }

        suspend fun withPreCheckSuccess() = apply {
            coEvery { repository.preCheck(any()) }.returns(PreCheckResult.Success.right())
        }

        suspend fun withPreCheckFailed() = apply {
            coEvery { repository.preCheck(any()) }.returns(NetworkFailure.NoNetworkConnection(IllegalStateException("test")).left())
        }

        suspend fun withUploadSuccess() = apply {
            coEvery { repository.uploadFile(any(), any(), any()) }.returns(Unit.right())
        }

        suspend fun withUploadFailed() = apply {
            coEvery { repository.uploadFile(any(), any(), any()) }.returns(
                NetworkFailure.NoNetworkConnection(IllegalStateException("test")).left()
            )
        }

        fun arrange(cellRepo: CellsRepository = repository) = this to CellUploadManagerImpl(
            repository = cellRepo,
            uploadScope = uploadScope
        )
    }

    private fun TestScope.Arrangement() = Arrangement(this.backgroundScope)
}

private class TestRepository : CellsRepository {

    override suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> {
        onProgressUpdate(500)
        delay(100)
        return Unit.right()
    }

    override suspend fun getFiles(path: String?, query: String, limit: Int, offset: Int) = emptyList<CellNode>().right()
    override suspend fun deleteFile(nodeUuid: String) = Unit.right()
    override suspend fun preCheck(nodePath: String) = PreCheckResult.Success.right()
    override suspend fun downloadFile(out: Path, cellPath: String, onProgressUpdate: (Long) -> Unit) = Unit.right()
    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String) = Unit.right()
    override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>) = Unit.right()
    override suspend fun getPreviews(nodeUuid: String) = emptyList<NodePreview>().right()
    override suspend fun getNode(nodeUuid: String) = CellNode(
        uuid = nodeUuid,
        versionId = "versionId",
        path = "path",
        size = 1000,
    ).right()

    override suspend fun deleteFiles(paths: List<String>): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun createPublicLink(nodeUuid: String, fileName: String): Either<NetworkFailure, PublicLink> {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicLink(linkUuid: String): Either<NetworkFailure, String> {
        TODO("Not yet implemented")
    }

    override suspend fun deletePublicLink(linkUuid: String): Either<NetworkFailure, Unit> {
        TODO("Not yet implemented")
    }

}
