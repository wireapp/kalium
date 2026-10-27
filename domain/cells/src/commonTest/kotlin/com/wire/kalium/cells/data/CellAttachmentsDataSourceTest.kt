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

import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CellAttachmentsDataSourceTest {
    private val messageAttachments = mock<MessageAttachmentsDao>(MockMode.autoUnit)
    private val repository = CellAttachmentsDataSource(messageAttachments, mock<CellFileDao>(MockMode.autoUnit))

    @Test
    fun givenRestoredAttachmentsWithAndWithoutLocalCopy_whenRefreshingStatus_thenEachGetsItsStatus() = runTest {
        everySuspend { messageAttachments.getAttachment("with-copy") } returns attachment("with-copy", localPath = "localPath")
        everySuspend { messageAttachments.getAttachment("without-copy") } returns attachment("without-copy", localPath = null)

        repository.refreshRestoredAttachmentStatus(listOf("with-copy", "without-copy"))

        verifySuspend(VerifyMode.exactly(1)) { messageAttachments.setTransferStatus("with-copy", "SAVED_INTERNALLY") }
        verifySuspend(VerifyMode.exactly(1)) { messageAttachments.setTransferStatus("without-copy", "NOT_DOWNLOADED") }
    }

    @Test
    fun givenANodeThatIsNotACellAttachment_whenRefreshingStatus_thenItIsLeftUntouched() = runTest {
        everySuspend { messageAttachments.getAttachment("not-cell") } returns attachment("not-cell", localPath = null, cellAsset = false)

        repository.refreshRestoredAttachmentStatus(listOf("not-cell"))

        verifySuspend(VerifyMode.not) { messageAttachments.setTransferStatus(any(), any()) }
    }

    @Test
    fun givenANodeWhoseAttachmentCannotBeRead_whenRefreshingStatus_thenTheOthersAreStillUpdated() = runTest {
        everySuspend { messageAttachments.getAttachment("missing") } throws IllegalStateException("No attachment")
        everySuspend { messageAttachments.getAttachment("present") } returns attachment("present", localPath = "localPath")

        repository.refreshRestoredAttachmentStatus(listOf("missing", "present"))

        verifySuspend(VerifyMode.not) { messageAttachments.setTransferStatus("missing", any()) }
        verifySuspend(VerifyMode.exactly(1)) { messageAttachments.setTransferStatus("present", "SAVED_INTERNALLY") }
    }

    private fun attachment(assetId: String, localPath: String?, cellAsset: Boolean = true) = MessageAttachmentEntity(
        assetId = assetId,
        cellAsset = cellAsset,
        mimeType = "image/png",
        assetPath = null,
        assetSize = 128,
        localPath = localPath,
        previewUrl = null,
        assetWidth = 64,
        assetHeight = 64,
        assetDuration = null,
        assetTransferStatus = "NOT_FOUND",
        contentUrl = null,
        contentHash = null,
        assetIndex = 0,
        contentExpiresAt = null,
        isEditSupported = false,
    )
}
