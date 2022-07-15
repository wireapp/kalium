package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.*
import io.ktor.utils.io.core.*
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoUtilsTest {
    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun testGivenByteArray_whenCallingCalcMd5_returnsExpectedDigest() {
        // Given
        val fileSystem = FakeFileSystem()
        val userHome = "/Users/me".toPath()
        val inputPath = "$userHome/test-text.txt".toPath()
        val input = "Hello World".encodeToByteArray()
        fileSystem.createDirectories(userHome)
        fileSystem.write(inputPath) {
            write(input)
        }
        val dataSource = fileSystem.source(inputPath)

        // When
        val digest = calcFileMd5(dataSource)

        // Then
        assertEquals("sQqNsWTgdUEFt6mb5y4/5Q==", digest)
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun testGivenByteArray_whenCallingCalcSHA256_returnsExpectedDigest() {
        // Given
        val fileSystem = FakeFileSystem()
        val userHome = "/Users/me".toPath()
        val inputPath = "$userHome/test-dummy.pdf".toPath()
        val input = readBinaryResource("dummy.pdf")
        fileSystem.createDirectories(userHome)
        fileSystem.write(inputPath) {
            write(input)
        }
        val dataSource = fileSystem.source(inputPath)

        // When
        val digest = calcFileSHA256(dataSource)
        val expectedValue = "3df79d34abbca99308e79cb94461c1893582604d68329a41fd4bec1885e6adb4".decodeHex()

        // Then
        assertNotNull(digest)
        assertTrue(digest.contentEquals(expectedValue))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun givenSomeDummyFile_whenEncryptedAndDecryptedWithAES256_returnsExpectedOriginalFile() {
        // Given
        val input = readBinaryResource("dummy.pdf")
        val randomAES256Key = generateRandomAES256Key()
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()
        fakeFileSystem.createDirectories(rootPath)

        val tempPath = "$rootPath/temp_path".toPath()
        val encryptedDataPath = "encrypted_data_path.aes".toPath()
        val decryptedDataPath = "decrypted_data_path.pdf".toPath()

        fakeFileSystem.write(tempPath) {
            write(input)
        }
        val rawAssetDataSource = fakeFileSystem.source(tempPath)
        val outputEncryptedSink = fakeFileSystem.appendingSink(encryptedDataPath)
        val outputDecryptedSink = fakeFileSystem.sink(decryptedDataPath)

        // When
        encryptFileWithAES256(rawAssetDataSource, randomAES256Key, outputEncryptedSink)
        fakeFileSystem.source(encryptedDataPath).buffer().use { it.readByteArray() }
        val encryptedAssetDataSource = fakeFileSystem.source(encryptedDataPath)
        val decryptedDataSize = decryptFileWithAES256(encryptedAssetDataSource, outputDecryptedSink, randomAES256Key)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }

        // Then
        assertTrue(input.size % 16 == 0)
        assertEquals(decryptedDataSize, input.size.toLong())
        assertTrue(input.contentEquals(decryptedData))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun givenSomeDummyFile_whenEncryptedAsDataAndDecryptedWithAES256AsAFile_returnsExpectedOriginalFileContent() {
        // Given
        val input = readBinaryResource("dummy.zip")
        val randomAES256Key = generateRandomAES256Key()
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()
        fakeFileSystem.createDirectories(rootPath)

        val encryptedDataPath = "encrypted_data_path.aes".toPath()
        val decryptedDataPath = "decrypted_data_path.zip".toPath()

        // When
        val encryptedData = encryptDataWithAES256(PlainData(input), randomAES256Key)
        val encryptedDataSize = encryptedData.data.size

        fakeFileSystem.write(encryptedDataPath) {
            write(encryptedData.data)
            flush()
        }
        val encryptedDataSource = fakeFileSystem.source(encryptedDataPath)
        val decryptedDataSink = fakeFileSystem.sink(decryptedDataPath)
        val decryptedDataSize = decryptFileWithAES256(encryptedDataSource, decryptedDataSink, randomAES256Key)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }

        // Then
        assertTrue(encryptedDataSize % 16 == 0)
        assertEquals(decryptedData.size, input.size)
        assertEquals(decryptedDataSize, input.size.toLong())
        assertTrue(input.contentEquals(decryptedData))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun givenSomeDummyFile_whenEncryptedAsAFileAndDecryptedWithAES256AsData_returnsExpectedOriginalFileContent() {
        // Given
        val input = readBinaryResource("dummy.pdf")
        val randomAES256Key = generateRandomAES256Key()
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()
        fakeFileSystem.createDirectories(rootPath)

        val tempPath = "$rootPath/temp_path".toPath()
        val encryptedDataPath = "encrypted_data_path.aes".toPath()
        fakeFileSystem.write(tempPath) {
            write(input)
        }
        val rawDataSource = fakeFileSystem.source(tempPath)
        val encryptedDataSink = fakeFileSystem.sink(encryptedDataPath)

        // When
        encryptFileWithAES256(rawDataSource, randomAES256Key, encryptedDataSink)

        val encryptedData = fakeFileSystem.read(encryptedDataPath) {
            readByteArray()
        }
        val decryptedData = decryptDataWithAES256(EncryptedData(encryptedData), randomAES256Key)

        // Then
        assertTrue(input.size % 16 == 0)
        assertEquals(decryptedData.data.size, input.size)
        assertTrue(input.contentEquals(decryptedData.data))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    @IgnoreAndroidTest
    fun givenDummyText_whenEncryptedAndDecryptedWithAES256_returnsOriginalText() {
        // Given
        val testMessage = "Hello Crypto World"
        val inputData = testMessage.toByteArray()
        val randomAES256Key = generateRandomAES256Key()
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()
        fakeFileSystem.createDirectories(rootPath)

        val tempPath = "$rootPath/temp_path".toPath()
        val encryptedDataPath = "encrypted_data_path.aes".toPath()
        val decryptedDataPath = "decrypted_data_path.txt".toPath()
        fakeFileSystem.write(tempPath) {
            write(inputData)
        }
        val rawDataSource = fakeFileSystem.source(tempPath)
        val encryptedDataSink = fakeFileSystem.sink(encryptedDataPath)

        // When
        encryptFileWithAES256(rawDataSource, randomAES256Key, encryptedDataSink)
        val encryptedDataSource = fakeFileSystem.source(encryptedDataPath)
        val decryptedDataSink = fakeFileSystem.sink(decryptedDataPath)
        val decryptedDataSize = decryptFileWithAES256(encryptedDataSource, decryptedDataSink, randomAES256Key)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }
        val decodedMessage = String(decryptedData)

        // Then
        assertEquals(decryptedDataSize, inputData.size.toLong())
        assertEquals(decodedMessage, testMessage)
    }
}

private fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
