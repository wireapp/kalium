package com.wire.kalium.cryptography

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalJsExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@IgnoreIOS
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalJsExport::class)
class ProteusClientTest : BaseProteusClientTest() {

    data class SampleUser(val id: CryptoUserID, val name: String)

    private val alice = SampleUser(CryptoUserID("aliceId", "aliceDomain"), "Alice")
    private val bob = SampleUser(CryptoUserID("bobId", "bobDomain"), "Bob")
    val aliceSessionId = CryptoSessionId(alice.id, CryptoClientId("aliceClient"))
    val bobSessionId = CryptoSessionId(alice.id, CryptoClientId("aliceClient"))

    @Test
    fun givenProteusClient_whenCallingNewLastKey_thenItReturnsALastPreKey() = runTest {
        val aliceClient = createProteusClient(alice.id)
        aliceClient.open()
        val lastPreKey = aliceClient.newLastPreKey()
        assertEquals(65535, lastPreKey.id)
    }

    @Test
    fun givenProteusClient_whenCallingNewPreKeys_thenItReturnsAListOfPreKeys() = runTest {
        val aliceClient = createProteusClient(alice.id)
        aliceClient.open()
        val preKeyList = aliceClient.newPreKeys(0, 10)
        assertEquals(preKeyList.size, 10)
    }

    @Test
    fun givenIncomingPreKeyMessage_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(alice.id)
        aliceClient.open()

        val bobClient = createProteusClient(bob.id)
        bobClient.open()

        val message = "Hi Alice!"
        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val encryptedMessage = bobClient.encryptWithPreKey(message.encodeToByteArray(), aliceKey, aliceSessionId)
        val decryptedMessage = aliceClient.decrypt(encryptedMessage, bobSessionId)
        assertEquals(message, decryptedMessage.decodeToString())
    }

    @Test
    fun givenSessionAlreadyExists_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(alice.id)
        aliceClient.open()

        val bobClient = createProteusClient(bob.id)
        bobClient.open()

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val message1 = "Hi Alice!"
        val encryptedMessage1 = bobClient.encryptWithPreKey(message1.encodeToByteArray(), aliceKey, aliceSessionId)
        aliceClient.decrypt(encryptedMessage1, bobSessionId)

        val message2 = "Hi again Alice!"
        val encryptedMessage2 = bobClient.encrypt(message2.encodeToByteArray(), aliceSessionId)
        val decryptedMessage2 = aliceClient.decrypt(encryptedMessage2, bobSessionId)

        assertEquals(message2, decryptedMessage2.decodeToString())
    }

    @Test
    fun givenNoSessionExists_whenCallingCreateSession_thenSessionIsCreated() = runTest {
        val aliceClient = createProteusClient(alice.id)
        aliceClient.open()

        val bobClient = createProteusClient(bob.id)
        bobClient.open()

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.createSession(aliceKey, aliceSessionId)
        assertNotNull(bobClient.encrypt("Hello World".encodeToByteArray(), aliceSessionId))
    }

}
