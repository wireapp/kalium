package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.calcFileMd5
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import io.ktor.utils.io.core.String
import io.ktor.utils.io.core.toByteArray
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoUtilsTest {

    @Test
    @IgnoreJS
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

        // When
        val digest = calcFileMd5(inputPath, fileSystem)

        // Then
        assertEquals("sQqNsWTgdUEFt6mb5y4/5Q==", digest)
    }

    @Test
    @IgnoreJS
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

        // When
        val digest = calcFileSHA256(inputPath, fileSystem)
        val expectedValue = "3df79d34abbca99308e79cb94461c1893582604d68329a41fd4bec1885e6adb4".decodeHex()

        // Then
        assertNotNull(digest)
        assertTrue(digest.contentEquals(expectedValue))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
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

        // When
        encryptFileWithAES256(tempPath, randomAES256Key, encryptedDataPath, fakeFileSystem)
        val decryptedDataSize =
            decryptFileWithAES256(fakeFileSystem.source(encryptedDataPath), decryptedDataPath, randomAES256Key, fakeFileSystem)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }

        // Then
        assertEquals(decryptedDataSize, input.size.toLong())
        assertTrue(input.contentEquals(decryptedData))
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
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

        // When
        encryptFileWithAES256(tempPath, randomAES256Key, encryptedDataPath, fakeFileSystem)
        val decryptedDataSize =
            decryptFileWithAES256(fakeFileSystem.source(encryptedDataPath), decryptedDataPath, randomAES256Key, fakeFileSystem)

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
