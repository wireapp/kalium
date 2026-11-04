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
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.CellNodeType
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.cellfile.CellFileLocalPath
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CheckCellNodeAvailabilityUseCaseTest {

    @Test
    fun givenTheNodeIsStillThere_whenChecking_thenItIsAvailableWithItsCurrentContentUrl() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNode(NODE.copy(contentUrl = "freshContentUrl"))
            .arrange()

        val result = useCase(UUID)

        val file = assertIs<Node.File>(assertIs<CellNodeAvailability.Available>(result).node)
        assertEquals("freshContentUrl", file.contentUrl)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteStandaloneAsset(any()) }
    }

    @Test
    fun givenTheNodeIsInTheRecycleBin_whenChecking_thenItIsGoneAndItsLocalDataCleanedUp() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNode(NODE.copy(isRecycled = true))
            .withLocalCopy()
            .arrange()

        val result = useCase(UUID)

        assertEquals(CellNodeAvailability.Gone, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.attachmentsRepository.setAssetTransferStatus(UUID, AssetTransferStatus.NOT_FOUND)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.attachmentsRepository.deleteStandaloneAsset(UUID) }
        assertFalse { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenANodeListedInTheRecycleBin_whenCheckingFromThere_thenItIsAvailableAndItsLocalDataKept() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withNode(NODE.copy(isRecycled = true))
            .arrange()

        val result = useCase(UUID, inRecycleBin = true)

        assertIs<CellNodeAvailability.Available>(result)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteStandaloneAsset(any()) }
    }

    @Test
    fun givenAFolderStillThere_whenChecking_thenItIsAvailableAsAFolder() = runTest {
        val (_, useCase) = Arrangement()
            .withNode(NODE.copy(type = CellNodeType.FOLDER.value))
            .arrange()

        val result = useCase(UUID)

        assertIs<Node.Folder>(assertIs<CellNodeAvailability.Available>(result).node)
    }

    @Test
    fun givenTheNodeIsNotFound_whenChecking_thenItIsGone() = runTest {
        val (_, useCase) = Arrangement()
            .withNodeFailure(serverError(HttpStatusCode.NotFound))
            .arrange()

        assertEquals(CellNodeAvailability.Gone, useCase(UUID))
    }

    @Test
    fun givenAccessToTheNodeWasLost_whenChecking_thenItIsGone() = runTest {
        val (_, useCase) = Arrangement()
            .withNodeFailure(serverError(HttpStatusCode.Forbidden))
            .arrange()

        assertEquals(CellNodeAvailability.Gone, useCase(UUID))
    }

    @Test
    fun givenNoNetwork_whenChecking_thenItIsUnknownAndLocalDataIsKept() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withNodeFailure(failure)
            .withLocalCopy()
            .arrange()

        assertEquals(CellNodeAvailability.Unknown(failure), useCase(UUID))
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteStandaloneAsset(any()) }
        assertEquals(true, arrangement.fileSystem.exists(LOCAL_PATH.toPath()))
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)
        val fileSystem = FakeFileSystem()
        private var localPaths = emptyList<CellFileLocalPath>()

        suspend fun withNode(node: CellNode) = apply {
            everySuspend { cellsRepository.getNode(any()) } returns node.right()
        }

        suspend fun withNodeFailure(failure: NetworkFailure) = apply {
            everySuspend { cellsRepository.getNode(any()) } returns failure.left()
        }

        fun withLocalCopy() = apply {
            fileSystem.write(LOCAL_PATH.toPath()) { "".encodeToByteArray() }
            localPaths = listOf(CellFileLocalPath(UUID, LOCAL_PATH))
        }

        suspend fun arrange(): Pair<Arrangement, CheckCellNodeAvailabilityUseCase> {
            everySuspend { attachmentsRepository.getStandaloneAssetPathsByIds(any()) } returns localPaths.right()
            everySuspend { attachmentsRepository.setAssetTransferStatus(any(), any()) } returns Unit.right()
            everySuspend { attachmentsRepository.deleteStandaloneAsset(any()) } returns Unit.right()
            everySuspend { attachmentsRepository.deleteFolderContents(any()) } returns emptyList<String>().right()

            return this to CheckCellNodeAvailabilityUseCaseImpl(
                cellsRepository = cellsRepository,
                cellAttachmentsRepository = attachmentsRepository,
                fileSystem = fileSystem,
            )
        }
    }

    private companion object {
        const val UUID = "uuid"
        const val LOCAL_PATH = "localPath"
        val NODE = CellNode(uuid = UUID, versionId = "versionId", path = "conversationId/file.pdf")

        fun serverError(status: HttpStatusCode) = NetworkFailure.ServerMiscommunication(
            KaliumException.ServerError(GenericAPIErrorResponse(status.value, "Test", ""))
        )
    }
}
