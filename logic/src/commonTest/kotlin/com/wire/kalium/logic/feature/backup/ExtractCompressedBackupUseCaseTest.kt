package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.util.createCompressedFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractCompressedBackupUseCaseTest {

    @BeforeTest
    fun setup() {
        fakeFileSystem = FakeKaliumFileSystem()
    }

    @Test
    fun `given some correct compressed encrypted backup file path, when invoked, then files are extracted correctly`() = runTest {
        // Given
        val encryptedDataFileName = "encryptedData.cc20"
        val encryptedDataPath = fakeFileSystem.tempFilePath(encryptedDataFileName)
        val encryptedData = encryptedDataPath.toString().encodeToByteArray()
        val encryptedDataSize = encryptedData.size.toLong()
        val compressedBackupFilePath = fakeFileSystem.tempFilePath("compressedEncryptedBackupFile.zip")
        val extractCompressedBackup = Arrangement()
            .withPreStoredData(listOf(encryptedData to encryptedDataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = extractCompressedBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is ExtractCompressedBackupFileResult.Success)
        assertTrue(result.isEncrypted)
        val totalExtractedFilesSize = getTotalExtractedFilesSize(result.extractedFilesRootPath, result.isEncrypted)
        assertEquals(totalExtractedFilesSize, encryptedDataSize)
    }

    @Test
    fun `given some correct compressed non-encrypted backup file path, when invoked, then files are extracted correctly`() = runTest {
        // Given
        val dbFileName = "encryptedData.db"
        val dbPath = fakeFileSystem.tempFilePath(dbFileName)
        val dummyDBData = dbPath.toString().encodeToByteArray()
        val metadataFileName = BackupConstants.BACKUP_METADATA_FILE_NAME
        val metadataPath = fakeFileSystem.tempFilePath(metadataFileName)
        val metadataContent = metadataPath.toString().encodeToByteArray()

        val totalDataSize = dummyDBData.size.toLong() + metadataContent.size.toLong()
        val compressedBackupFilePath = fakeFileSystem.tempFilePath("compressedBackupFile.zip")
        val extractCompressedBackup = Arrangement()
            .withPreStoredData(listOf(dummyDBData to dbPath, metadataContent to metadataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = extractCompressedBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is ExtractCompressedBackupFileResult.Success)
        assertFalse(result.isEncrypted)
        val totalExtractedFilesSize = getTotalExtractedFilesSize(result.extractedFilesRootPath, result.isEncrypted)
        assertEquals(totalDataSize, totalExtractedFilesSize)
    }

    @Test
    fun `given some incorrect compressed non-encrypted backup file path, when invoked, then the total uncompressed size is 0`() =
        runTest {
            // Given
            val wrongBackupFilePath = fakeFileSystem.tempFilePath("compressedBackupFile.weird")
            val weirdData = "Some weird data".encodeToByteArray()
            val extractCompressedBackup = Arrangement()
                .withWrongPreStoredData(weirdData, wrongBackupFilePath)
                .arrange()

            // When
            val result = extractCompressedBackup(wrongBackupFilePath)

            // Then
            assertTrue(result is ExtractCompressedBackupFileResult.Success)
            assertEquals(false, result.isEncrypted)
            val totalExtractedFilesSize = getTotalExtractedFilesSize(result.extractedFilesRootPath, result.isEncrypted)
            assertNotEquals(weirdData.size.toLong(), totalExtractedFilesSize)
        }

    private class Arrangement {
        @Suppress("NestedBlockDepth")
        fun withPreStoredData(data: List<Pair<ByteArray, Path>>, storedPath: Path) = apply {
            with(fakeFileSystem) {
                data.forEach { (rawData, dataPath) ->
                    sink(dataPath).buffer().use {
                        it.write(rawData)
                    }
                }
                val outputSink = sink(storedPath)
                createCompressedFile(data.map {
                    source(it.second) to it.second.name
                }, outputSink)
            }
        }

        fun withWrongPreStoredData(data: ByteArray, storedPath: Path) = apply {
            with(fakeFileSystem) {
                sink(storedPath).buffer().use {
                    it.write(data)
                    it.close()
                }
            }
        }

        fun arrange() = ExtractCompressedBackupUseCaseImpl(fakeFileSystem)
    }

    companion object {
        var fakeFileSystem = FakeKaliumFileSystem()
    }

    @Suppress("NestedBlockDepth")
    private suspend fun getTotalExtractedFilesSize(extractedDataRootPath: Path, isEncrypted: Boolean): Long = with(fakeFileSystem) {
        var totalSize = 0L
        val rootPath = if (isEncrypted) extractedDataRootPath.parent else extractedDataRootPath
        rootPath?.let {
            listDirectories(rootPath).forEach { directory ->
                source(directory).buffer().use {
                    totalSize += it.readByteArray().size
                }
            }
        }
        return@with totalSize
    }
}
