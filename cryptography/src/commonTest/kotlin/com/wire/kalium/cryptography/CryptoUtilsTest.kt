/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.calcFileMd5
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import io.ktor.utils.io.core.String
import io.ktor.utils.io.core.toByteArray
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoUtilsTest {
    @Test
    @IgnoreJS
    @IgnoreAndroidInstrumented
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
    @IgnoreAndroidInstrumented
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
        assertContentEquals(expectedValue, digest)
    }

    @Test
    @IgnoreJS
    @IgnoreAndroidInstrumented
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
        assertContentEquals(decryptedData, input)
    }

    @Test
    @IgnoreJS
    @IgnoreAndroidInstrumented
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
        assertContentEquals(decryptedData, input)
    }

    @Test
    @IgnoreJS
    @IgnoreAndroidInstrumented
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
        assertEquals(input.size, decryptedData.data.size)
        assertContentEquals(input, decryptedData.data)
    }

    @Test
    @IgnoreJS
    @IgnoreAndroidInstrumented
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
