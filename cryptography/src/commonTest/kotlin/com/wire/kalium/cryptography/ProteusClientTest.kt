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

    @IgnoreJS
    @IgnoreIOS
    @Test
    fun givenExistingUnencryptedProteusData_whenCallingOpenOrError_thenItMigratesExistingData() = runTest {
        val proteusStoreRef = createProteusStoreRef(alice.id)
        val unencryptedAliceClient = createProteusClient(proteusStoreRef)
        val previousFingerprint = unencryptedAliceClient.getLocalFingerprint()
        val encryptedAliceClient = createProteusClient(proteusStoreRef, PROTEUS_DB_SECRET)

        assertEquals(previousFingerprint.decodeToString(), encryptedAliceClient.getLocalFingerprint().decodeToString())
    }

    @IgnoreJS
    @IgnoreIOS
    @Test
    fun givenExistingUnencryptedProteusData_whenCallingOpenOrCreate_thenItMigratesExistingData() = runTest {
        val proteusStoreRef = createProteusStoreRef(alice.id)
        val unencryptedAliceClient = createProteusClient(proteusStoreRef)
        val previousFingerprint = unencryptedAliceClient.getLocalFingerprint()
        val encryptedAliceClient = createProteusClient(proteusStoreRef, PROTEUS_DB_SECRET)


        assertEquals(previousFingerprint.decodeToString(), encryptedAliceClient.getLocalFingerprint().decodeToString())
    }

    @Test
    fun givenProteusClient_whenCallingNewLastKey_thenItReturnsALastPreKey() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val lastPreKey = aliceClient.newLastResortPreKey()
        assertEquals(65535, lastPreKey.id)
    }

    @Test
    fun givenProteusClient_whenCallingNewPreKeys_thenItReturnsAListOfPreKeys() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val preKeyList = aliceClient.newPreKeys(0, 10)
        assertEquals(preKeyList.size, 10)
    }

    @Test
    fun givenIncomingPreKeyMessage_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val message = "Hi Alice!"
        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val encryptedMessage = bobClient.encryptWithPreKey(message.encodeToByteArray(), aliceKey, aliceSessionId)
        val decryptedMessage = aliceClient.decrypt(encryptedMessage, bobSessionId) { it }
        assertEquals(message, decryptedMessage.decodeToString())
    }

    @Test
    fun givenSessionAlreadyExists_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        val encryptedMessage1 = bobClient.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId)
        aliceClient.decrypt(encryptedMessage1, bobSessionId) {}

        val message2 = "Hi again Alice!"
        val encryptedMessage2 = bobClient.encrypt(message2.encodeToByteArray(), aliceSessionId)
        val decryptedMessage2 = aliceClient.decrypt(encryptedMessage2, bobSessionId) { it }

        assertEquals(message2, decryptedMessage2.decodeToString())
    }

    @IgnoreJS
    @IgnoreIOS
    @Test
    fun givenReceivingSameMessageTwice_whenCallingDecrypt_thenDuplicateMessageError() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id), PROTEUS_DB_SECRET)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id), PROTEUS_DB_SECRET)

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        val encryptedMessage1 = bobClient.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId)
        aliceClient.decrypt(encryptedMessage1, bobSessionId) {}

        val exception: ProteusException = assertFailsWith {
            aliceClient.decrypt(encryptedMessage1, bobSessionId) {}
        }
        assertEquals(ProteusException.Code.DUPLICATE_MESSAGE, exception.code)
    }

    @IgnoreJS
    @IgnoreIOS
    @Test
    fun givenMissingSession_whenCallingEncryptBatched_thenMissingSessionAreIgnored() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        bobClient.createSession(aliceKey, aliceSessionId)

        val missingAliceSessionId = CryptoSessionId(alice.id, CryptoClientId("missing"))
        val encryptedMessages = bobClient.encryptBatched(message1.encodeToByteArray(), listOf(aliceSessionId, missingAliceSessionId))

        assertEquals(1, encryptedMessages.size)
        assertTrue(encryptedMessages.containsKey(aliceSessionId))
    }

    @Test
    fun givenNoSessionExists_whenCallingCreateSession_thenSessionIsCreated() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.createSession(aliceKey, aliceSessionId)
        assertNotNull(bobClient.encrypt("Hello World".encodeToByteArray(), aliceSessionId))
    }

    // TODO: cryptobox4j does not expose the session
    @IgnoreIOS //  underlying proteus error is not exposed atm
    @IgnoreJvm
    @IgnoreJS
    @Test
    fun givenNoSessionExists_whenGettingRemoteFingerPrint_thenReturnSessionNotFound() = runTest {
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        assertFailsWith<ProteusException> {
            bobClient.remoteFingerPrint(aliceSessionId)
        }.also {
            assertEquals(ProteusException.Code.SESSION_NOT_FOUND, it.code)
        }
    }

    @IgnoreJvm // cryptobox4j does not expose the session
    @Test
    fun givenSessionExists_whenGettingRemoteFingerPrint_thenReturnSuccess() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.createSession(aliceKey, aliceSessionId)
        bobClient.remoteFingerPrint(aliceSessionId).also {
            assertEquals(aliceClient.getLocalFingerprint().decodeToString(), it.decodeToString())
        }
    }

    // TODO: Implement on CoreCrypto as well once it supports transactions
    @IgnoreJS
    @IgnoreJvm
    @IgnoreIOS
    @Test
    fun givenNonEncryptedClient_whenThrowingDuringTransaction_thenShouldNotSaveSessionAndBeAbleToDecryptAgain() = runTest {
        val aliceRef = createProteusStoreRef(alice.id)
        val failedAliceClient = createProteusClient(aliceRef)
        val bobClient = createProteusClient(createProteusStoreRef(bob.id))

        val aliceKey = failedAliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"

        var decryptedCount = 0

        val encryptedMessage1 = bobClient.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId)
        try {
            failedAliceClient.decrypt(encryptedMessage1, bobSessionId) {
                decryptedCount++
                throw NullPointerException("")
            }
        } catch (ignore: Throwable) {
            /** No-op **/
        }
        // Assume that the app crashed after decrypting but before saving session.
        // Trying to decrypt again should succeed.

        val secondAliceClient = createProteusClient(aliceRef)

        val result = secondAliceClient.decrypt(encryptedMessage1, bobSessionId) { result ->
            decryptedCount++
            result
        }
        assertEquals(message1, result.decodeToString())
        assertEquals(2, decryptedCount)
    }

    companion object {
        val PROTEUS_DB_SECRET = ProteusDBSecret("secret")
    }
}
