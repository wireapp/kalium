package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AESUtilsTest {

    @Test
    @IgnoreJS
    @IgnoreIOS
    fun givenRawByteArray_whenEncryptedAndDecryptedWithAES256_returnsExpectedOriginalByteArray() {
        val testMessage = "Hello Crypto World"
        val inputByteArray = testMessage.toByteArray()

        val (encryptedData, symmetricKey) = encryptDataWithAES256(inputByteArray)
        val decryptedData = decryptDataWithAES256(encryptedData, symmetricKey)
        val decodedMessage = String(decryptedData)

        assertEquals(decodedMessage, testMessage)

    }
}
