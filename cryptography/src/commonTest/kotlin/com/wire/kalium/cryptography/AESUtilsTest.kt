package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import io.ktor.utils.io.core.*
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AESUtilsTest {

    @Test
    @IgnoreJS
    @IgnoreIOS
    fun givenRawByteArray_whenEncryptedAndDecryptedWithAES256_returnsExpectedOriginalByteArray() {
        // Given
        val testMessage = "Hello Crypto World"
        val inputData = testMessage.toByteArray()
        val randomAES256Key = generateRandomAES256Key()
        val fakeFileSystem = FakeFileSystem()
        val encryptedDataPath = "encrypted_data_path.aes".toPath()
        val decryptedDataPath = "decrypted_data_path.txt".toPath()

        // When
        val encryptedDataSucceeded = encryptDataWithAES256(inputData, randomAES256Key, encryptedDataPath, fakeFileSystem)
        val decryptedDataSucceeded = decryptDataWithAES256(encryptedDataPath, decryptedDataPath, randomAES256Key, fakeFileSystem)

        val decryptedData = fakeFileSystem.read(decryptedDataPath) {
            readByteArray()
        }
        val decodedMessage = String(decryptedData)

        // Then
        assertTrue(encryptedDataSucceeded)
        assertTrue(decryptedDataSucceeded)
        assertEquals(decodedMessage, testMessage)
    }
}
