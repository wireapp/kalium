package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.calcMd5
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.String
import io.ktor.utils.io.core.toByteArray
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        val digest = calcMd5(inputPath, fileSystem)

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
        val digest = calcSHA256(inputPath, fileSystem)

        // Then
        assertNotNull(digest)
        assertEquals("M2RmNzlkMzRhYmJjYTk5MzA4ZTc5Y2I5NDQ2MWMxODkzNTgyNjA0ZDY4MzI5YTQxZmQ0YmVjMTg4NWU2YWRiNA==", digest.encodeBase64())
    }

    @Test
    @IgnoreJS
    @IgnoreIOS
    fun givenRawByteArray_whenEncryptedAndDecryptedWithAES256_returnsExpectedOriginalByteArray() {
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
        encryptDataWithAES256(tempPath, randomAES256Key, encryptedDataPath, fakeFileSystem)
        val decryptedDataSize =
            decryptDataWithAES256(fakeFileSystem.source(encryptedDataPath), decryptedDataPath, randomAES256Key, fakeFileSystem)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }
        val decodedMessage = String(decryptedData)

        // Then
        assertEquals(decryptedDataSize, inputData.size.toLong())
        assertEquals(decodedMessage, testMessage)
    }

}
