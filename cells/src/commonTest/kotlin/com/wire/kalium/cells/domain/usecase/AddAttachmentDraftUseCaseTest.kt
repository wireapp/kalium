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

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddAttachmentDraftUseCaseTest {

    private companion object {
        val conversationId = ConversationId("1", "test")
        val assetPath = "path".toPath()
        const val assetSize = 1000L
        const val fileName = "testfile.test"
        val destNodePath = "${CellsScope.ROOT_CELL}/$conversationId/$fileName"
    }

    @Test
    fun `given valid request upload manager is called`() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessAdd()
            .withSuccessPreCheck()
            .arrange()

        useCase(conversationId, fileName, assetPath, assetSize)

        coVerify {
            arrangement.uploadManager.upload(
                assetPath = assetPath,
                assetSize = assetSize,
                destNodePath = destNodePath,
            )
        }.wasInvoked(once)
    }

    @Test
    fun `given success pre-check attachment is persisted`() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessAdd()
            .withSuccessPreCheck()
            .arrange()

        useCase(conversationId, fileName, assetPath, assetSize)

        coVerify {
            arrangement.repository.add(
                conversationId = conversationId,
                node = testNode,
                dataPath = assetPath.toString(),
            )
        }.wasInvoked(once)
    }

    @Test
    fun `given success attachment persist upload events observer is started`() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withUploadEvents()
            .arrange()

        useCase(conversationId, fileName, assetPath, assetSize)

        advanceTimeBy(1)

        verify {
            arrangement.uploadManager.observeUpload(any())
        }.wasInvoked(once)
    }

    @Test
    fun `given upload complete event upload status is updated`() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withSuccessUpdate()
            .withUploadCompleteEvent()
            .arrange()

        useCase(conversationId, fileName, assetPath, assetSize)

        advanceTimeBy(1)

        coVerify {
            arrangement.repository.updateStatus(testNode.uuid, AttachmentUploadStatus.UPLOADED)
        }.wasInvoked(once)
    }

    @Test
    fun `given upload error event upload status is updated`() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withSuccessUpdate()
            .withUploadErrorEvent()
            .arrange()

        useCase(conversationId, fileName, assetPath, assetSize)

        advanceTimeBy(1)

        coVerify {
            arrangement.repository.updateStatus(testNode.uuid, AttachmentUploadStatus.FAILED)
        }.wasInvoked(once)
    }

    private class Arrangement(val useCaseScope: CoroutineScope = TestScope()) {

        @Mock
        val uploadManager = mock(CellUploadManager::class)

        @Mock
        val repository = mock(MessageAttachmentDraftRepository::class)

        val uploadEventsFlow = MutableSharedFlow<CellUploadEvent>()

        suspend fun withSuccessPreCheck() = apply {
            coEvery {
                uploadManager.upload(
                    assetPath = any(),
                    assetSize = any(),
                    destNodePath = any()
                )
            }.returns(testNode.right())
        }

        suspend fun withSuccessAdd() = apply {
            coEvery {
                repository.add(
                    conversationId = any(),
                    node = any(),
                    dataPath = any()
                )
            }.returns(Unit.right())
        }

        suspend fun withSuccessUpdate() = apply {
            coEvery {
                repository.updateStatus(
                    uuid = any(),
                    status = any()
                )
            }.returns(Unit.right())
        }

        fun withUploadEvents() = apply {
            every {
                uploadManager.observeUpload(any())
            }.returns(uploadEventsFlow)
        }

        fun withUploadCompleteEvent() = apply {
            every {
                uploadManager.observeUpload(any())
            }.returns(flowOf(CellUploadEvent.UploadCompleted))
        }

        fun withUploadErrorEvent() = apply {
            every {
                uploadManager.observeUpload(any())
            }.returns(flowOf(CellUploadEvent.UploadError))
        }

        fun arrange() = this to AddAttachmentDraftUseCaseImpl(
            uploadManager = uploadManager,
            repository = repository,
            scope = useCaseScope,
        )
    }
}

private val testNode = CellNode(
    uuid = "uuid",
    versionId = "versionId",
    path = "path",
    modified = 0,
    size = 0,
    eTag = "eTag",
    type = "type",
    isRecycleBin = false,
    isDraft = false
)
