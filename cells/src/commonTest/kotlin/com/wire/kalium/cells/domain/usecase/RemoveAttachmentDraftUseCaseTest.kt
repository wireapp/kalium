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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoveAttachmentDraftUseCaseTest {

    @Test
    fun `given attachment with UPLOADING status when removing then upload is cancelled`() = runTest {
        val uuid = uuid4().toString()
        val (arrangement, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withAttachment(uuid, AttachmentUploadStatus.UPLOADING)
            .arrange()

        removeAttachment(uuid)

        coVerify {
            arrangement.uploadManager.cancelUpload(uuid)
        }.wasInvoked(once)
    }

    @Test
    fun `given attachment with UPLOADING status when removing then attachment is removed`() = runTest {
        val uuid = uuid4().toString()
        val (arrangement, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withAttachment(uuid, AttachmentUploadStatus.UPLOADING)
            .arrange()

        removeAttachment(uuid)

        coVerify {
            arrangement.attachmentsRepository.remove(uuid)
        }.wasInvoked(once)
    }

    @Test
    fun `given attachment with UPLOADED status when removing then attachment draft is cancelled`() = runTest {
        val uuid = uuid4().toString()
        val (arrangement, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withAttachment(uuid, AttachmentUploadStatus.UPLOADED)
            .arrange()

        removeAttachment(uuid)

        coVerify {
            arrangement.cellsRepository.cancelDraft(uuid, "")
        }.wasInvoked(once)
    }

    @Test
    fun `given attachment with UPLOADED status when removing then attachment is removed`() = runTest {
        val uuid = uuid4().toString()
        val (arrangement, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withAttachment(uuid, AttachmentUploadStatus.UPLOADED)
            .arrange()

        removeAttachment(uuid)

        coVerify {
            arrangement.attachmentsRepository.remove(uuid)
        }.wasInvoked(once)
    }

    @Test
    fun `given attachment with FAILED status when removing then attachment is removed`() = runTest {
        val uuid = uuid4().toString()
        val (arrangement, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withAttachment(uuid, AttachmentUploadStatus.FAILED)
            .arrange()

        removeAttachment(uuid)

        coVerify {
            arrangement.attachmentsRepository.remove(uuid)
        }.wasInvoked(once)
    }

    @Test
    fun `given attachment not found when removing then error is returned`() = runTest {
        val uuid = uuid4().toString()
        val (_, removeAttachment) = Arrangement()
            .withRepository()
            .withUploadManager()
            .withCellsRepository()
            .withNoAttachment()
            .arrange()

        val result = removeAttachment(uuid)

        assertEquals(StorageFailure.DataNotFound.left(), result)
    }

    private class Arrangement {

        @Mock
        val attachmentsRepository = mock(MessageAttachmentDraftRepository::class)

        @Mock
        val cellsRepository = mock(CellsRepository::class)

        @Mock
        val uploadManager = mock(CellUploadManager::class)

        suspend fun withRepository() = apply {
            coEvery { attachmentsRepository.remove(any()) }.returns(Unit.right())
        }

        suspend fun withCellsRepository() = apply {
            coEvery { cellsRepository.cancelDraft(any(), any()) }.returns(Unit.right())
        }

        suspend fun withUploadManager() = apply {
            coEvery { uploadManager.cancelUpload(any()) }.returns(Unit)
        }

        suspend fun withAttachment(uuid: String, status: AttachmentUploadStatus) = apply {
            coEvery { attachmentsRepository.get(any()) }.returns(
                AttachmentDraft(
                    uuid = uuid,
                    versionId = "",
                    fileName = "",
                    localFilePath = "",
                    fileSize = 1,
                    uploadStatus = status
                ).right()
            )
        }

        suspend fun withNoAttachment() = apply {
            coEvery { attachmentsRepository.get(any()) }.returns(null.right())
        }

        fun arrange() = this to RemoveAttachmentDraftUseCaseImpl(uploadManager, attachmentsRepository, cellsRepository)
    }
}
