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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellUploadRequest
import com.wire.kalium.cells.domain.CellUploadState
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CellUploadCoordinatorIntegrationTest {

    @Test
    fun given_real_manager_when_transfer_completes_then_publish_once_and_delete_staged_file() = runTest {
        val arrangement = Arrangement(this)
        try {
            arrangement.coordinator.enqueue(listOf(arrangement.request("a.txt")))
            advanceUntilIdle()
            assertEquals(CellUploadState.Uploading(0.5f), arrangement.item("a.txt").state)
            assertTrue(arrangement.fileSystem.exists("/staged/a.txt".toPath()))

            arrangement.transfers.getValue("a.txt").complete(Unit)
            advanceUntilIdle()

            val item = arrangement.item("a.txt")
            assertEquals(CellUploadState.Completed, item.state)
            assertEquals(listOf(NodeIdAndVersion(item.nodeUuid!!, item.versionId!!)), arrangement.published)
            assertFalse(arrangement.fileSystem.exists(assertNotNull(item.request).localPath))
            assertFalse(arrangement.coordinator.hasActiveUploads.value)
        } finally {
            arrangement.close()
        }
    }

    @Test
    fun given_real_manager_when_cancel_then_transfer_drains_and_next_file_starts() = runTest {
        val arrangement = Arrangement(this)
        try {
            arrangement.coordinator.enqueue(listOf(arrangement.request("a.txt"), arrangement.request("b.txt")))
            advanceUntilIdle()
            val first = arrangement.item("a.txt")

            arrangement.coordinator.cancel(first.id)
            advanceUntilIdle()

            assertEquals(CellUploadState.Cancelled, arrangement.item("a.txt").state)
            assertFalse(arrangement.manager.isUploading(first.nodeUuid!!))
            assertEquals(CellUploadState.Uploading(0.5f), arrangement.item("b.txt").state)
            assertTrue(arrangement.fileSystem.exists(assertNotNull(first.request).localPath))
            assertTrue(arrangement.published.isEmpty())

            arrangement.coordinator.dismiss(first.id)
            advanceUntilIdle()
            assertFalse(arrangement.fileSystem.exists(assertNotNull(first.request).localPath))
            arrangement.transfers.getValue("b.txt").complete(Unit)
            advanceUntilIdle()
            assertEquals(CellUploadState.Completed, arrangement.item("b.txt").state)
        } finally {
            arrangement.close()
        }
    }

    @Test
    fun given_real_manager_when_failed_upload_retried_then_replace_event_flow_and_keep_draft() = runTest {
        val arrangement = Arrangement(this)
        try {
            arrangement.failNextTransfer = true
            arrangement.coordinator.enqueue(listOf(arrangement.request("a.txt")))
            advanceUntilIdle()
            val failed = arrangement.item("a.txt")
            assertEquals(CellUploadState.Failed, failed.state)
            assertTrue(arrangement.fileSystem.exists(assertNotNull(failed.request).localPath))

            arrangement.coordinator.retry(failed.id)
            advanceUntilIdle()
            assertEquals(CellUploadState.Uploading(0.5f), arrangement.item("a.txt").state)
            assertEquals(failed.nodeUuid, arrangement.item("a.txt").nodeUuid)
            assertEquals(1, arrangement.preChecks)
            assertEquals(2, arrangement.uploadCalls)

            arrangement.transfers.getValue("a.txt").complete(Unit)
            advanceUntilIdle()
            assertEquals(CellUploadState.Completed, arrangement.item("a.txt").state)
            assertEquals(1, arrangement.published.size)
        } finally {
            arrangement.close()
        }
    }

    @Test
    fun given_cancel_between_precheck_and_node_announcement_then_real_transfer_is_cancelled() = runTest {
        val arrangement = Arrangement(this)
        try {
            arrangement.afterPreCheck = { arrangement.coordinator.cancel(arrangement.item("a.txt").id) }
            arrangement.coordinator.enqueue(listOf(arrangement.request("a.txt")))
            advanceUntilIdle()

            val item = arrangement.item("a.txt")
            assertEquals(CellUploadState.Cancelled, item.state)
            assertFalse(arrangement.manager.isUploading(item.nodeUuid!!))
            assertTrue(arrangement.published.isEmpty())
            assertTrue(arrangement.fileSystem.exists(assertNotNull(item.request).localPath))
        } finally {
            arrangement.close()
        }
    }

    private class Arrangement(testScope: TestScope) {
        private val scope = CoroutineScope(testScope.coroutineContext + Job())
        private val repository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val fileSystem = FakeFileSystem().apply { createDirectories("/staged".toPath()) }
        val manager = CellUploadManagerImpl(repository, scope, fileSystem)
        private val networkObserver = mock<NetworkStateObserver> {
            every { observeNetworkState() } returns MutableStateFlow(NetworkState.ConnectedWithInternet)
        }
        val coordinator = CellUploadCoordinatorImpl(
            manager, repository, scope, networkObserver, maxConcurrentUploads = 1, fileSystem = fileSystem
        )
        val transfers = mutableMapOf<String, CompletableDeferred<Unit>>()
        val published = mutableListOf<NodeIdAndVersion>()
        var afterPreCheck: () -> Unit = {}
        var failNextTransfer = false
        var preChecks = 0
        var uploadCalls = 0

        init {
            everySuspend { repository.preCheck(any()) } calls {
                preChecks += 1
                afterPreCheck()
                PreCheckResult.Success.right()
            }
            everySuspend { repository.uploadFile(any(), any(), any()) } calls { invocation ->
                uploadCalls += 1
                val node = invocation.args[1] as CellNode
                @Suppress("UNCHECKED_CAST")
                val progress = invocation.args[2] as (Long) -> Unit
                if (failNextTransfer) {
                    failNextTransfer = false
                    NetworkFailure.ServerMiscommunication(Exception("upload failed")).left()
                } else {
                    val transfer = CompletableDeferred<Unit>()
                    transfers[node.path.substringAfterLast('/')] = transfer
                    progress(50)
                    transfer.await()
                    Unit.right()
                }
            }
            everySuspend { repository.publishDrafts(any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val nodes = invocation.args[0] as List<NodeIdAndVersion>
                published += nodes
                Unit.right()
            }
        }

        fun request(name: String): CellUploadRequest {
            val path = "/staged/$name".toPath()
            fileSystem.write(path) { writeUtf8("content") }
            return CellUploadRequest(path, name, 100, "cell/folder")
        }

        fun item(name: String) = coordinator.uploads.value.first { it.fileName == name }
        fun close() = scope.cancel()
    }
}
