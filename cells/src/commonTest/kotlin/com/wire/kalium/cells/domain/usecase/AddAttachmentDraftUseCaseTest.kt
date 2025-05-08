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

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
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
        val destNodePath = "wire-cells-android/$conversationId/$fileName"
        val mimeType = "image/jpeg"
        val metadata = AssetContent.AssetMetadata.Image(0, 0)
    }

    @Test
    fun given_valid_request_upload_manager_is_called() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessAdd()
            .withSuccessPreCheck()
            .arrange()

        useCase(conversationId, fileName, mimeType, assetPath, assetSize, metadata)

        coVerify {
            arrangement.uploadManager.upload(
                assetPath = assetPath,
                assetSize = assetSize,
                destNodePath = destNodePath,
            )
        }.wasInvoked(once)
    }

    @Test
    fun given_success_pre_check_attachment_is_persisted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccessAdd()
            .withSuccessPreCheck()
            .arrange()

        useCase(conversationId, fileName, mimeType, assetPath, assetSize, metadata)

        coVerify {
            arrangement.repository.add(
                conversationId = conversationId,
                node = testNode,
                dataPath = assetPath.toString(),
                mimeType = mimeType,
                metadata = AssetContent.AssetMetadata.Image(0, 0),
                uploadStatus = AttachmentUploadStatus.UPLOADING,
            )
        }.wasInvoked(once)
    }

    @Test
    fun given_success_attachment_persist_upload_events_observer_is_started() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withUploadEvents()
            .arrange()

        useCase(conversationId, fileName, mimeType, assetPath, assetSize, metadata)

        advanceTimeBy(1)

        verify {
            arrangement.uploadManager.observeUpload(any())
        }.wasInvoked(once)
    }

    @Test
    fun given_upload_complete_event_upload_status_is_updated() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withSuccessUpdate()
            .withUploadCompleteEvent()
            .arrange()

        useCase(conversationId, fileName, mimeType, assetPath, assetSize, metadata)

        advanceTimeBy(1)

        coVerify {
            arrangement.repository.updateStatus(testNode.uuid, AttachmentUploadStatus.UPLOADED)
        }.wasInvoked(once)
    }

    @Test
    fun given_upload_error_event_upload_status_is_updated() = runTest {
        val (arrangement, useCase) = Arrangement(this.backgroundScope)
            .withSuccessAdd()
            .withSuccessPreCheck()
            .withSuccessUpdate()
            .withUploadErrorEvent()
            .arrange()

        useCase(conversationId, fileName, mimeType, assetPath, assetSize, metadata)

        advanceTimeBy(1)

        coVerify {
            arrangement.repository.updateStatus(testNode.uuid, AttachmentUploadStatus.FAILED)
        }.wasInvoked(once)
    }

    private class Arrangement(val useCaseScope: CoroutineScope = TestScope()) {

        val uploadManager = mock(CellUploadManager::class)
        val conversationRepository = mock(CellConversationRepository::class)
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
                    dataPath = any(),
                    mimeType = any(),
                    metadata = any(),
                    uploadStatus = any(),
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

        suspend fun arrange(): Pair<Arrangement, AddAttachmentDraftUseCaseImpl> {

            coEvery { conversationRepository.getCellName(any()) }.returns("wire-cells-android/$conversationId".right())

            return this to AddAttachmentDraftUseCaseImpl(
                uploadManager = uploadManager,
                conversationRepository = conversationRepository,
                repository = repository,
                scope = useCaseScope,
            )
        }
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
    isRecycled = false,
    isDraft = false
)
