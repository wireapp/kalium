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

import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.publiclink.PublicLinkDao
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CellsDataSourceTest {

    private companion object {
        private const val CELL_PATH = "cell/path/file.txt"
        private const val FILE_CONTENT = "file content"
        private val TARGET_DIR = "/downloads".toPath()
        private val TARGET_PATH = TARGET_DIR / "file.txt"
        private val PART_PATH = TARGET_DIR / "file.txt.part"

        /** Root has no parent, so no `.part` sibling can be derived for it. */
        private val ROOT_PATH = "/".toPath()
    }

    @Test
    fun givenDownloadSucceeds_whenDownloadingFile_thenContentIsWrittenToTargetPath() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        val result = dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertIs<Either.Right<Unit>>(result)
        assertEquals(FILE_CONTENT, arrangement.readFile(TARGET_PATH))
    }

    @Test
    fun givenDownloadSucceeds_whenDownloadingFile_thenPartFileIsNotLeftBehind() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertFalse { arrangement.fileSystem.exists(PART_PATH) }
    }

    @Test
    fun givenDownloadInProgress_whenWritingData_thenDataGoesToPartFileAndTargetIsNotVisible() = runTest {
        lateinit var pathsDuringDownload: Pair<Boolean, Boolean>
        val (_, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT) { fileSystem ->
                pathsDuringDownload = fileSystem.exists(PART_PATH) to fileSystem.exists(TARGET_PATH)
            }
            .arrange()

        dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertEquals(true to false, pathsDuringDownload)
    }

    @Test
    fun givenCellPath_whenDownloadingFile_thenPathIsForwardedToAwsClient() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.awsClient.download(CELL_PATH, any(), any())
        }
    }

    @Test
    fun givenAwsClientReportsProgress_whenDownloadingFile_thenProgressIsForwardedToCaller() = runTest {
        val (_, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT, progressUpdates = listOf(10L, 50L, 100L))
            .arrange()

        val progress = mutableListOf<Long>()
        dataSource.downloadFile(TARGET_PATH, CELL_PATH) { progress.add(it) }

        assertEquals(listOf(10L, 50L, 100L), progress)
    }

    @Test
    fun givenTargetFileAlreadyExists_whenDownloadSucceeds_thenFileIsReplaced() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withExistingFile(TARGET_PATH, "stale content")
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        val result = dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertIs<Either.Right<Unit>>(result)
        assertEquals(FILE_CONTENT, arrangement.readFile(TARGET_PATH))
    }

    @Test
    fun givenStalePartFileExists_whenDownloadSucceeds_thenItIsOverwritten() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withExistingFile(PART_PATH, "stale part content")
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        val result = dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertIs<Either.Right<Unit>>(result)
        assertEquals(FILE_CONTENT, arrangement.readFile(TARGET_PATH))
        assertFalse { arrangement.fileSystem.exists(PART_PATH) }
    }

    @Test
    fun givenPathWithoutParent_whenDownloadingFile_thenFailureIsReturned() = runTest {
        val (_, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        val result = dataSource.downloadFile(ROOT_PATH, CELL_PATH) {}

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
    }

    @Test
    fun givenPathWithoutParent_whenDownloadingFile_thenAwsClientIsNotCalled() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        dataSource.downloadFile(ROOT_PATH, CELL_PATH) {}

        verifySuspend(VerifyMode.not) {
            arrangement.awsClient.download(any(), any(), any())
        }
    }

    @Test
    fun givenAwsClientFails_whenDownloadingFile_thenFailureIsReturned() = runTest {
        val (_, dataSource) = Arrangement()
            .withDownloadFailure(IOException("connection reset"))
            .arrange()

        val result = dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
    }

    @Test
    fun givenAwsClientFails_whenDownloadingFile_thenPartAndTargetFilesAreNotLeftBehind() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadFailure(IOException("connection reset"))
            .arrange()

        dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertFalse { arrangement.fileSystem.exists(PART_PATH) }
        assertFalse { arrangement.fileSystem.exists(TARGET_PATH) }
    }

    @Test
    fun givenAwsClientFailsAfterPartialWrite_whenDownloadingFile_thenExistingTargetFileIsPreserved() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withExistingFile(TARGET_PATH, "previous content")
            .withDownloadWriting("partial") { throw IOException("connection reset") }
            .arrange()

        val result = dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        assertEquals("previous content", arrangement.readFile(TARGET_PATH))
        assertFalse { arrangement.fileSystem.exists(PART_PATH) }
    }

    @Test
    fun givenTargetDirectoryDoesNotExist_whenDownloadingFile_thenFailureIsReturned() = runTest {
        val (_, dataSource) = Arrangement()
            .withDownloadWriting(FILE_CONTENT)
            .arrange()

        val result = dataSource.downloadFile("/missing".toPath() / "file.txt", CELL_PATH) {}

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
    }

    @Test
    fun givenDownloadIsCancelled_whenDownloadingFile_thenCancellationIsRethrown() = runTest {
        val (_, dataSource) = Arrangement()
            .withDownloadFailure(CancellationException("cancelled"))
            .arrange()

        assertFailsWith<CancellationException> {
            dataSource.downloadFile(TARGET_PATH, CELL_PATH) {}
        }
    }

    @Test
    fun givenDownloadIsCancelled_whenDownloadingFile_thenPartFileIsDeleted() = runTest {
        val (arrangement, dataSource) = Arrangement()
            .withDownloadWriting("partial") { throw CancellationException("cancelled") }
            .arrange()

        assertTrue { runCatching { dataSource.downloadFile(TARGET_PATH, CELL_PATH) {} }.isFailure }

        assertFalse { arrangement.fileSystem.exists(PART_PATH) }
        assertFalse { arrangement.fileSystem.exists(TARGET_PATH) }
    }

    private class Arrangement {

        val cellsApi = mock<CellsApi>(mode = MockMode.autoUnit)
        val publicLinkDao = mock<PublicLinkDao>(mode = MockMode.autoUnit)
        val awsClient = mock<CellsAwsClient>(mode = MockMode.autoUnit)
        val fileSystem = FakeFileSystem()

        init {
            fileSystem.createDirectories(TARGET_DIR)
        }

        fun readFile(path: Path): String = fileSystem.read(path) { readUtf8() }

        fun withExistingFile(path: Path, content: String) = apply {
            fileSystem.write(path) { writeUtf8(content) }
        }

        /**
         * Simulates the AWS client writing [content] to the provided sink, emitting [progressUpdates]
         * and then running [afterWrite] - used to assert intermediate state or to fail mid-download.
         */
        fun withDownloadWriting(
            content: String,
            progressUpdates: List<Long> = emptyList(),
            afterWrite: (FakeFileSystem) -> Unit = {},
        ) = apply {
            everySuspend { awsClient.download(any(), any(), any()) } calls { invocation ->
                val sink = invocation.args[1] as Sink

                @Suppress("UNCHECKED_CAST")
                val onProgressUpdate = invocation.args[2] as (Long) -> Unit

                sink.buffer().apply {
                    writeUtf8(content)
                    flush()
                }
                progressUpdates.forEach(onProgressUpdate)
                afterWrite(fileSystem)
            }
        }

        fun withDownloadFailure(error: Throwable) = apply {
            everySuspend { awsClient.download(any(), any(), any()) } throws error
        }

        fun arrange(): Pair<Arrangement, CellsDataSource> = this to CellsDataSource(
            cellsApi = cellsApi,
            publicLinkDao = publicLinkDao,
            awsClient = awsClient,
            fileSystem = fileSystem,
        )
    }
}
