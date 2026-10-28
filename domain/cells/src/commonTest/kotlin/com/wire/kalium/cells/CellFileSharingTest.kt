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

package com.wire.kalium.cells

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.FakeCellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class CellFileSharingTest {
    @Test
    fun uploadsEveryFileBeforePublishingAndUsesCollisionSafePaths() = runTest {
        val repository = RecordingRepository()
        val progress = mutableListOf<Long>()
        val result = CellFileSharing(repository).upload("cell", files) { sent, total ->
            assertEquals(12L, total)
            progress += sent
        } as Either.Right
        assertEquals(listOf("upload:cell/a.txt", "upload:cell/b-2.txt", "publish"), repository.events)
        assertEquals(listOf("cell/a.txt", "cell/b-2.txt"), result.value.map { it.assetPath })
        assertNotEquals(result.value[0].id, result.value[1].id)
        assertEquals(12L, progress.last())
        assertTrue(repository.deleted.isEmpty())
        assertTrue(result.value.all { it.localPath == null }, "Temporary share paths must not become message paths")
    }

    @Test
    fun failedSecondUploadDoesNotPublishAndDiscardsTheBatch() = runTest {
        val repository = RecordingRepository(failUpload = 2)
        assertTrue(CellFileSharing(repository).upload("cell", files) is Either.Left)
        assertTrue("publish" !in repository.events)
        assertEquals(repository.uploaded.map { it.uuid }, repository.deleted)
    }

    @Test
    fun failedPublishDiscardsTheUnsentBatch() = runTest {
        val repository = RecordingRepository(failPublish = true)
        assertTrue(CellFileSharing(repository).upload("cell", files) is Either.Left)
        assertEquals(repository.uploaded.map { it.uuid }, repository.deleted)
    }

    @Test
    fun cancellationStopsUploadAndDiscardsUnpublishedNodes() = runTest {
        val entered = CompletableDeferred<Unit>()
        val repository = RecordingRepository(block = entered)
        val upload = async { CellFileSharing(repository).upload("cell", files) }
        entered.await()
        upload.cancelAndJoin()
        assertTrue(upload.isCancelled)
        assertEquals(repository.uploaded.map { it.uuid }, repository.deleted)
        assertTrue("publish" !in repository.events)
    }

    private class RecordingRepository(
        val failUpload: Int = 0,
        val failPublish: Boolean = false,
        val block: CompletableDeferred<Unit>? = null,
    ) : CellsRepository by FakeCellsRepository() {
        val events = mutableListOf<String>()
        val uploaded = mutableListOf<CellNode>()
        val deleted = mutableListOf<String>()
        override suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult> =
            Either.Right(if (nodePath.endsWith("b.txt")) PreCheckResult.FileExists("cell/b-2.txt") else PreCheckResult.Success)
        override suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> {
            uploaded += node
            events += "upload:${node.path}"
            block?.let { it.complete(Unit); awaitCancellation() }
            onProgressUpdate(checkNotNull(node.size))
            return if (uploaded.size == failUpload) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
        }
        override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> {
            assertEquals(uploaded.size, nodes.size)
            events += "publish"
            return if (failPublish) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
        }
        override suspend fun deleteFile(nodeUuid: String, permanentDelete: Boolean): Either<NetworkFailure, Unit> {
            assertTrue(permanentDelete)
            deleted += nodeUuid
            return Either.Right(Unit)
        }
    }

    private companion object {
        val files = listOf(CellSharedFile("/tmp/a".toPath(), "a.txt", "text/plain", 5),
            CellSharedFile("/tmp/b".toPath(), "b.txt", "text/plain", 7))
    }
}
