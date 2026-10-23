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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreNodesFromRecycleBinUseCaseTest {

    @Test
    fun givenNodes_whenRestoreSucceeds_thenRestoreAllInOneRequestAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .arrange()

        val result = useCase(UUIDS)

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.cellsRepository.restoreNodes(UUIDS) }
    }

    @Test
    fun givenNodesWithAttachments_whenRestoreSucceeds_thenMakeEveryAttachmentAvailableAgain() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .withAttachment("uuid-1", attachment("uuid-1", localPath = "localPath"))
            .withAttachment("uuid-2", attachment("uuid-2", localPath = null))
            .arrange()

        useCase(UUIDS)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus("uuid-1", AssetTransferStatus.SAVED_INTERNALLY)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus("uuid-2", AssetTransferStatus.NOT_DOWNLOADED)
        }
    }

    @Test
    fun givenNodes_whenRestoreFails_thenReturnFailureAndKeepAttachmentStatus() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(failure.left())
            .arrange()

        val result = useCase(UUIDS)

        assertEquals(failure.left(), result)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.setAssetTransferStatus(any(), any()) }
    }

    @Test
    fun givenNoNodes_whenInvoked_thenReturnRightWithoutRequest() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRestoreReturning(Unit.right())
            .arrange()

        val result = useCase(emptyList())

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.not) { arrangement.cellsRepository.restoreNodes(any()) }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)
        private val attachments = mutableMapOf<String, CellAssetContent>()

        suspend fun withRestoreReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { cellsRepository.restoreNodes(any()) } returns result
        }

        fun withAttachment(uuid: String, attachment: CellAssetContent) = apply {
            attachments[uuid] = attachment
        }

        suspend fun arrange(): Pair<Arrangement, RestoreNodesFromRecycleBinUseCase> {
            // The catch-all first: the stub defined last wins, so the specific attachments below take precedence.
            everySuspend { attachmentsRepository.getAttachment(any()) } returns StorageFailure.DataNotFound.left()
            attachments.forEach { (uuid, attachment) ->
                everySuspend { attachmentsRepository.getAttachment(uuid) } returns attachment.right()
            }
            everySuspend { attachmentsRepository.setAssetTransferStatus(any(), any()) } returns Unit.right()

            return this to RestoreNodesFromRecycleBinUseCaseImpl(
                cellsRepository = cellsRepository,
                attachmentsRepository = attachmentsRepository,
            )
        }
    }

    private companion object {
        val UUIDS = listOf("uuid-1", "uuid-2")

        fun attachment(uuid: String, localPath: String?) = CellAssetContent(
            id = uuid,
            versionId = "versionId",
            mimeType = "image/png",
            assetPath = "assetPath",
            assetSize = 1024,
            contentHash = "contentHash",
            localPath = localPath,
            contentUrl = "http://contentUrl",
            previewUrl = "http://previewUrl",
            metadata = null,
            transferStatus = AssetTransferStatus.NOT_FOUND,
        )
    }
}
