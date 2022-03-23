package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import io.ktor.utils.io.core.String
import io.ktor.utils.io.core.toByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

class AESUtilsTest {

    @Test
    @IgnoreJS
    @IgnoreIOS
    fun givenRawByteArray_whenEncryptedAndDecryptedWithAES256_returnsExpectedOriginalByteArray() {
        // Given
        val testMessage = "Hello Crypto World"
        val inputData = PlainData(testMessage.toByteArray())
        val randomAES256Key = generateRandomAES256Key()

        // When
        val encryptedData = encryptDataWithAES256(inputData, randomAES256Key)
        val decryptedData = decryptDataWithAES256(encryptedData, randomAES256Key)
        val decodedMessage = String(decryptedData.data)

        // Then
        assertEquals(decodedMessage, testMessage)
    }
}
