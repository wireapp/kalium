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
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ObserveAttachmentDraftsUseCaseTest {

    @Test
    fun test_stale_uploads_are_removed() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withUploadManager()
            .withRepository()
            .arrange()

        useCase.invoke(ConversationId("1", "test"))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.repository.remove("1")
        }
    }

    private class Arrangement {

        val repository = mock<MessageAttachmentDraftRepository>(mode = MockMode.autoUnit)
        val uploadManager = mock<CellUploadManager>(mode = MockMode.autoUnit)

        fun withUploadManager() = apply {
            every { uploadManager.isUploading(any()) }.returns(false)
        }

        suspend fun withRepository() = apply {
            everySuspend { repository.getAll(any()) }.returns(listOf(
                AttachmentDraft(
                    uuid = "1",
                    versionId = "1",
                    uploadStatus = AttachmentUploadStatus.UPLOADING,
                    fileName = "",
                    localFilePath = "",
                    fileSize = 1,
                    remoteFilePath = "",
                    mimeType = "",
                    assetWidth = 0,
                    assetHeight = 0,
                    assetDuration = 0,
                )
            ).right())

            everySuspend { repository.remove(any()) }.returns(Unit.right())

            everySuspend { repository.observe(any()) }.returns(emptyFlow())
        }

        fun arrange() = this to ObserveAttachmentDraftsUseCaseImpl(repository, uploadManager)
    }
}
