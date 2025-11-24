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
package com.wire.backup.encryption

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class XChaChaPoly1305EncryptedStreamTest {

    private val stream = EncryptedStream.XChaCha20Poly1305

    @Test
    fun givenEncryptedMessageAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        val messageToEncrypt = "Hello Alice!"
        testCorrectDecryptionOfMessage(messageToEncrypt.encodeToByteArray())
    }

    private suspend fun testCorrectDecryptionOfMessage(messageToEncrypt: ByteArray) {
        val originalBuffer = Buffer()
        originalBuffer.write(messageToEncrypt)
        val originalBufferData = originalBuffer.peek().readByteArray()

        val encryptedBuffer = Buffer()
        val passphrase = "password"
        val salt = XChaChaPoly1305AuthenticationData.newSalt()
        val additionalData = "additionalData".encodeToByteArray().toUByteArray()
        val authenticationData = XChaChaPoly1305AuthenticationData(passphrase, salt, additionalData)
        stream.encrypt(
            originalBuffer,
            encryptedBuffer,
            authenticationData
        )

        val decryptedBuffer = Buffer()
        val result = stream.decrypt(encryptedBuffer, decryptedBuffer, authenticationData)
        assertEquals(DecryptionResult.Success, result)
        assertContentEquals(originalBufferData, decryptedBuffer.readByteArray())
    }

    private suspend fun testWithRandomMessageOfSpecificSize(size: Int) {
        val message = Random.Default.nextBytes(size)
        testCorrectDecryptionOfMessage(message)
    }

    @Test
    fun givenEncryptedMessagesOfSizeOneAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(1)
    }

    @Test
    fun givenEncryptedMessageOfSizeSmallerThanAPageAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(4095)
    }

    @Test
    fun givenEncryptedMessageWithExactlyOnePageOfSizeAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(4096)
    }

    @Test
    fun givenEncryptedMessageSlightlyBiggerThanAPageAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(4097)
    }

    @Test
    fun givenEncryptedMessageSmallerThanTwoPagesAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(8191)
    }

    @Test
    fun givenEncryptedMessageExactlyTwoPagesLongAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(8192)
    }

    @Test
    fun givenEncryptedMessageBiggerThanTwoPagesAndCorrectAuthentication_whenDecrypting_thenShouldReturnOriginalMessage() = runTest {
        testWithRandomMessageOfSpecificSize(8193)
    }

    @Test
    fun givenEncryptedMessageAndWrongAdditionalData_whenDecrypting_thenShouldFailDecryption() = runTest {
        val originalBuffer = Buffer()
        originalBuffer.writeUtf8("Hello Alice!")

        val encryptedBuffer = Buffer()
        val passphrase = "password"
        val salt = XChaChaPoly1305AuthenticationData.newSalt()
        val additionalData = "additionalData".encodeToByteArray().toUByteArray()
        val authenticationData = XChaChaPoly1305AuthenticationData(passphrase, salt, additionalData)
        stream.encrypt(
            originalBuffer,
            encryptedBuffer,
            authenticationData
        )

        val result = stream.decrypt(
            source = encryptedBuffer,
            outputSink = Buffer(),
            authenticationData = authenticationData.copy(
                additionalData = "INCORRECT".encodeToByteArray().toUByteArray(),
            ),
        )
        assertEquals(DecryptionResult.Failure.AuthenticationFailure, result)
    }

    @Test
    fun givenEncryptedMessageAndWrongSalt_whenDecrypting_thenShouldFailDecryption() = runTest {
        val originalBuffer = Buffer()
        originalBuffer.writeUtf8("Hello Alice!")

        val encryptedBuffer = Buffer()
        val passphrase = "password"
        val salt = XChaChaPoly1305AuthenticationData.newSalt()
        val additionalData = "additionalData".encodeToByteArray().toUByteArray()
        val authenticationData = XChaChaPoly1305AuthenticationData(passphrase, salt, additionalData)
        stream.encrypt(
            originalBuffer,
            encryptedBuffer,
            authenticationData
        )

        val wrongSalt = UByteArray(crypto_pwhash_SALTBYTES)
        for (i in wrongSalt.indices) {
            wrongSalt[i] = 42U
        }
        val result = stream.decrypt(
            source = encryptedBuffer,
            outputSink = Buffer(),
            authenticationData = authenticationData.copy(
                salt = wrongSalt,
            ),
        )
        assertEquals(DecryptionResult.Failure.AuthenticationFailure, result)
    }

    @Test
    fun givenEncryptedMessageAndWrongPassword_whenDecrypting_thenShouldFailDecryption() = runTest {
        val originalBuffer = Buffer()
        originalBuffer.writeUtf8("Hello Alice!")

        val encryptedBuffer = Buffer()
        val passphrase = "password"
        val salt = XChaChaPoly1305AuthenticationData.newSalt()
        val additionalData = "additionalData".encodeToByteArray().toUByteArray()
        val authenticationData = XChaChaPoly1305AuthenticationData(passphrase, salt, additionalData)
        stream.encrypt(
            originalBuffer,
            encryptedBuffer,
            authenticationData
        )

        val result = stream.decrypt(
            source = encryptedBuffer,
            outputSink = Buffer(),
            authenticationData = authenticationData.copy(
                passphrase = "WRONG PASSWORD",
            ),
        )
        assertEquals(DecryptionResult.Failure.AuthenticationFailure, result)
    }
}
