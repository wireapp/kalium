package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.util.createCompressedFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractCompressedBackupUseCaseTest {

    @Test
    fun `given some correct compressed encrypted backup file path, when invoked, then files are extracted correctly`() = runTest {
        // Given
        val encryptedDataPath = fakeFileSystem.tempFilePath("encryptedData.cc20")
        val encryptedData = encryptedDataPath.toString().encodeToByteArray()
        val compressedBackupFilePath = fakeFileSystem.tempFilePath("compressedEncryptedBackupFile.zip")
        val (arrangement, useCase) = Arrangement()
            .withPreStoredData(listOf(encryptedData to encryptedDataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = useCase(compressedBackupFilePath)

        // Then
        assertTrue(result is ExtractCompressedBackupFileResult.Success)
        assertTrue(result.isEncrypted)
    }

    private class Arrangement {

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

        fun arrange() = this to ExtractCompressedBackupUseCaseImpl(fakeFileSystem)
    }

    companion object {
        val fakeFileSystem = FakeKaliumFileSystem()
    }
}
