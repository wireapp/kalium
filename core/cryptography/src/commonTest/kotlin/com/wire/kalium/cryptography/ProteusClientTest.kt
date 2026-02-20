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

import com.wire.kalium.cryptography.exceptions.ProteusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
// After removing cryptobox, proteus is not implemented on iOS and JS
@IgnoreJS
@IgnoreIOS
class ProteusClientTest : BaseProteusClientTest() {

    data class SampleUser(val id: CryptoUserID, val name: String)

    private val alice = SampleUser(CryptoUserID("aliceId", "aliceDomain"), "Alice")
    private val bob = SampleUser(CryptoUserID("bobId", "bobDomain"), "Bob")
    private val aliceSessionId = CryptoSessionId(alice.id, CryptoClientId("aliceClient"))
    private val bobSessionId = CryptoSessionId(bob.id, CryptoClientId("bobClient"))

    @BeforeTest
    fun before() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @Test
    fun givenProteusClient_whenCallingNewLastKey_thenItReturnsALastPreKey() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val lastPreKey = aliceClient.newLastResortPreKey()
        assertEquals(65535, lastPreKey.id)
    }

    @Test
    fun givenProteusClient_whenCallingNewPreKeys_thenItReturnsAListOfPreKeys() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val preKeyList = aliceClient.newPreKeys(0, 10)
        assertEquals(preKeyList.size, 10)
    }

    @Test
    fun givenIncomingPreKeyMessage_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val message = "Hi Alice!"
        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val encryptedMessage =
            bobClient.transaction("encryptWithPreKey") { it.encryptWithPreKey(message.encodeToByteArray(), aliceKey, aliceSessionId) }
        val decryptedMessage = aliceClient.transaction("decrypt") { it.decryptMessage(bobSessionId, encryptedMessage) { it } }
        assertEquals(message, decryptedMessage.decodeToString())
    }

    @Test
    fun givenSessionAlreadyExists_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        val encryptedMessage1 = bobClient.transaction("encryptWithPreKey") {
            it.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId)
        }
        aliceClient.transaction("decrypt") { it.decryptMessage(bobSessionId, encryptedMessage1) {} }

        val message2 = "Hi again Alice!"
        val encryptedMessage2 = bobClient.transaction("encrypt") { it.encrypt(message2.encodeToByteArray(), aliceSessionId) }
        val decryptedMessage2 = aliceClient.transaction("decrypt") { it.decryptMessage(bobSessionId, encryptedMessage2) { it } }

        assertEquals(message2, decryptedMessage2.decodeToString())
    }

    @Test
    fun givenReceivingSameMessageTwice_whenCallingDecrypt_thenDuplicateMessageError() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        val encryptedMessage1 =
            bobClient.transaction("encryptWithPreKey") { it.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId) }
        aliceClient.transaction("decrypt") { it.decryptMessage(bobSessionId, encryptedMessage1) {} }

        val exception: ProteusException = assertFailsWith {
            aliceClient.transaction("decrypt") { it.decryptMessage(bobSessionId, encryptedMessage1) {} }
        }
        assertEquals(ProteusException.Code.DUPLICATE_MESSAGE, exception.code)
    }

    @Test
    fun givenProteusRawErrorCode209_whenMapping_thenDuplicateMessage() {
        assertEquals(
            ProteusException.Code.DUPLICATE_MESSAGE,
            ProteusException.fromProteusCode(209)
        )
    }

    @Test
    fun givenProteusRawErrorCode204_whenMapping_thenRemoteIdentityChanged() {
        assertEquals(
            ProteusException.Code.REMOTE_IDENTITY_CHANGED,
            ProteusException.fromProteusCode(204)
        )
    }

    @Test
    fun givenMissingSession_whenCallingEncryptBatched_thenMissingSessionAreIgnored() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        bobClient.transaction("createSession") { it.createSession(aliceKey, aliceSessionId) }

        val missingAliceSessionId = CryptoSessionId(alice.id, CryptoClientId("missing"))
        val encryptedMessages = bobClient.transaction("encryptBatched") {
            it.encryptBatched(
                message1.encodeToByteArray(),
                listOf(aliceSessionId, missingAliceSessionId)
            )
        }

        assertEquals(1, encryptedMessages.size)
        assertTrue(encryptedMessages.containsKey(aliceSessionId))
    }

    @Test
    fun givenNoSessionExists_whenCallingCreateSession_thenSessionIsCreated() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.transaction("createSession") { it.createSession(aliceKey, aliceSessionId) }
        assertNotNull(bobClient.transaction("encrypt") { it.encrypt("Hello World".encodeToByteArray(), aliceSessionId) })
    }

    @Test
    fun givenNoSessionExists_whenGettingRemoteFingerPrint_thenReturnSessionNotFound() = runTest {
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        assertFailsWith<ProteusException> {
            bobClient.transaction("remoteFingerPrint") { it.remoteFingerPrint(aliceSessionId) }
        }.also {
            assertEquals(ProteusException.Code.SESSION_NOT_FOUND, it.code)
        }
    }

    @Test
    fun givenSessionExists_whenGettingRemoteFingerPrint_thenReturnSuccess() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.transaction("createSession") { it.createSession(aliceKey, aliceSessionId) }
        bobClient.transaction("remoteFingerPrint") { it.remoteFingerPrint(aliceSessionId) }.also {
            assertEquals(aliceClient.transaction("getLocalFingerprint") { it.getLocalFingerprint() }, it)
        }
    }

    @Test
    fun givenEncryptedClient_whenThrowingDuringTransaction_thenShouldNotSaveSessionAndBeAbleToDecryptAgain() = runTest {
        val aliceRef = createProteusStoreRef(alice.id)
        val failedAliceClient = createProteusClient(aliceRef, PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = failedAliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"

        var decryptedCount = 0

        val encryptedMessage1 =
            bobClient.transaction("encryptWithPreKey") { it.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId) }
        try {
            failedAliceClient.transaction("decrypt") {
                it.decryptMessage(bobSessionId, encryptedMessage1) {
                    decryptedCount++
                    throw NullPointerException("")
                }
            }
        } catch (ignore: Throwable) {
            /** No-op **/
        }
        // Assume that the app crashed after decrypting but before saving session.
        // Trying to decrypt again should succeed.

        val secondAliceClient = createProteusClient(aliceRef, PROTEUS_DB_SECRET)

        val result = secondAliceClient.transaction("decrypt") {
            it.decryptMessage(bobSessionId, encryptedMessage1) { result ->
                decryptedCount++
                result
            }
        }
        assertEquals(message1, result.decodeToString())
        assertEquals(2, decryptedCount)
    }

    @Test
    fun givenDestroyedProteusClient_whenTransaction_thenThrowsWrappedIllegalStateException() = runTest {
        val client = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        client.close()

        val exception = assertFailsWith<ProteusException> {
            client.transaction("test") { it.getLocalFingerprint() }
        }

        assertTrue(
            exception.message?.contains("destroyed", ignoreCase = true) == true,
            "Expected message to mention 'destroyed', got: ${exception.message}"
        )

        assertTrue(
            exception.cause is IllegalStateException,
            "Expected cause to be IllegalStateException, but was: ${exception.cause}"
        )
    }

    companion object {
        val PROTEUS_DB_SECRET = ProteusDBSecret(ByteArray(32) { 0 })
    }
}
