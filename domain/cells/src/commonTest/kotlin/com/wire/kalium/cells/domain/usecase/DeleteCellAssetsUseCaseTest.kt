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
import com.wire.kalium.cells.domain.model.NodeToDelete
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.persistence.dao.cellfile.CellFileLocalPath
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteCellAssetsUseCaseTest {

    @Test
    fun givenNodes_whenDeleteSucceeds_thenDeleteAllInOneRequestAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()

        val result = useCase(NODES)

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.deleteNodes(NODES.map { it.uuid }, false)
        }
    }

    @Test
    fun givenPermanentDelete_whenInvoked_thenRequestPermanentDeleteOfAllNodes() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()

        useCase(NODES, permanentDelete = true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cellsRepository.deleteNodes(NODES.map { it.uuid }, true)
        }
    }

    @Test
    fun givenNodes_whenDeleteSucceeds_thenCleanUpLocalDataOfEveryNode() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withDeleteReturning(Unit.right())
            .arrange()

        useCase(NODES)

        NODES.forEach { node ->
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.attachmentsRepository.setAssetTransferStatus(node.uuid, AssetTransferStatus.NOT_FOUND)
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.attachmentsRepository.deleteStandaloneAsset(node.uuid)
            }
        }
        assertFalse { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenLocalCleanupFailsForANode_whenDeleteSucceeds_thenStillCleanUpTheOthersAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withDeleteReturning(Unit.right())
            .arrange()
        everySuspend {
            arrangement.attachmentsRepository.setAssetTransferStatus(FOLDER.uuid, any())
        } returns StorageFailure.DataNotFound.left()

        val result = useCase(listOf(FOLDER, FILE))

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteStandaloneAsset(FOLDER.uuid) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.attachmentsRepository.deleteStandaloneAsset(FILE.uuid) }
        assertFalse { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenALocalFileThatCannotBeRemoved_whenDeleteSucceeds_thenStillRemoveTheOthersAndReturnRight() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withDeleteReturning(Unit.right())
            .arrange()
        arrangement.fileSystem.write(OTHER_LOCAL_PATH.toPath()) { "".encodeToByteArray() }
        // FakeFileSystem refuses to delete an open file, with an IOException.
        arrangement.fileSystem.source(LOCAL_PATH.toPath()).use {
            val result = useCase(listOf(FILE, OTHER_FILE))

            assertEquals(Unit.right(), result)
        }
        assertTrue { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
        assertFalse { arrangement.fileSystem.exists(OTHER_LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenCallerCancelledDuringCleanup_whenDeleteSucceeds_thenStillCleanUpEveryNode() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withDeleteReturning(Unit.right())
            .arrange()
        lateinit var job: Job
        // The caller goes away while the first node is being cleaned up, e.g. its screen closed.
        everySuspend { arrangement.attachmentsRepository.setAssetTransferStatus(FILE.uuid, any()) } calls {
            job.cancel()
            Unit.right()
        }

        job = launch { useCase(listOf(FILE, FOLDER)) }
        job.join()

        assertFalse { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.attachmentsRepository.deleteStandaloneAsset(FOLDER.uuid) }
    }

    @Test
    fun givenAFolder_whenDeleteSucceeds_thenCleanUpTheFilesStoredUnderIt() = runTest {
        val folder = NodeToDelete(uuid = "folder-uuid", localPath = null, folderPath = "conversation/folder")
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()
        arrangement.fileSystem.write(FILE_IN_FOLDER_LOCAL_PATH.toPath()) { "".encodeToByteArray() }
        everySuspend {
            arrangement.attachmentsRepository.deleteFolderContents("conversation/folder")
        } returns listOf(FILE_IN_FOLDER_LOCAL_PATH).right()

        useCase(listOf(folder))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.attachmentsRepository.deleteFolderContents("conversation/folder") }
        assertFalse { arrangement.fileSystem.exists(FILE_IN_FOLDER_LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenALocalCopyOnlyRecordedInTheDatabase_whenDeleteSucceeds_thenRemoveIt() = runTest {
        val file = NodeToDelete(uuid = "uuid-4", localPath = null)
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()
        arrangement.fileSystem.write(STORED_LOCAL_PATH.toPath()) { "".encodeToByteArray() }
        everySuspend {
            arrangement.attachmentsRepository.getStandaloneAssetPathsByIds(listOf(file.uuid))
        } returns listOf(CellFileLocalPath(file.uuid, STORED_LOCAL_PATH)).right()

        useCase(listOf(file))

        assertFalse { arrangement.fileSystem.exists(STORED_LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenFiles_whenDeleteSucceeds_thenNoFolderContentsAreCleanedUp() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()

        useCase(listOf(FILE))

        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteFolderContents(any()) }
    }

    @Test
    fun givenNodes_whenDeleteFails_thenReturnFailureAndKeepLocalData() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withLocalFileAvailable()
            .withDeleteReturning(failure.left())
            .arrange()

        val result = useCase(NODES)

        assertEquals(failure.left(), result)
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.setAssetTransferStatus(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.attachmentsRepository.deleteStandaloneAsset(any()) }
        assertTrue { arrangement.fileSystem.exists(LOCAL_PATH.toPath()) }
    }

    @Test
    fun givenNoNodes_whenInvoked_thenReturnRightWithoutRequest() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withDeleteReturning(Unit.right())
            .arrange()

        val result = useCase(emptyList())

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.not) { arrangement.cellsRepository.deleteNodes(any(), any()) }
    }

    private class Arrangement {

        val cellsRepository = mock<CellsRepository>(mode = MockMode.autoUnit)
        val attachmentsRepository = mock<CellAttachmentsRepository>(mode = MockMode.autoUnit)
        val fileSystem = FakeFileSystem()

        suspend fun withDeleteReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { cellsRepository.deleteNodes(any(), any()) }.returns(result)
        }

        fun withLocalFileAvailable() = apply {
            fileSystem.write(LOCAL_PATH.toPath()) { "".encodeToByteArray() }
        }

        suspend fun arrange(): Pair<Arrangement, DeleteCellAssetsUseCaseImpl> {
            everySuspend { attachmentsRepository.setAssetTransferStatus(any(), any()) }.returns(Unit.right())
            everySuspend { attachmentsRepository.deleteStandaloneAsset(any()) }.returns(Unit.right())
            everySuspend { attachmentsRepository.deleteFolderContents(any()) }.returns(emptyList<String>().right())
            everySuspend { attachmentsRepository.getStandaloneAssetPathsByIds(any()) }.returns(emptyList<CellFileLocalPath>().right())

            return this to DeleteCellAssetsUseCaseImpl(
                cellsRepository = cellsRepository,
                cellAttachmentsRepository = attachmentsRepository,
                fileSystem = fileSystem,
            )
        }
    }

    private companion object {
        const val LOCAL_PATH = "localPath"
        const val OTHER_LOCAL_PATH = "otherLocalPath"
        const val FILE_IN_FOLDER_LOCAL_PATH = "fileInFolderLocalPath"
        const val STORED_LOCAL_PATH = "storedLocalPath"
        val FILE = NodeToDelete(uuid = "uuid-1", localPath = LOCAL_PATH)
        val FOLDER = NodeToDelete(uuid = "uuid-2", localPath = null)
        val OTHER_FILE = NodeToDelete(uuid = "uuid-3", localPath = OTHER_LOCAL_PATH)
        val NODES = listOf(FILE, FOLDER)
    }
}
