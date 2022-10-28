package com.wire.kalium.cryptography

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@IgnoreIOS
@OptIn(ExperimentalCoroutinesApi::class)
class ProteusClientTest : BaseProteusClientTest() {

    data class SampleUser(val id: CryptoUserID, val name: String)

    private val alice = SampleUser(CryptoUserID("aliceId", "aliceDomain"), "Alice")
    private val bob = SampleUser(CryptoUserID("bobId", "bobDomain"), "Bob")
    private val aliceSessionId = CryptoSessionId(alice.id, CryptoClientId("aliceClient"))
    private val bobSessionId = CryptoSessionId(alice.id, CryptoClientId("aliceClient"))

    @IgnoreJS
    @Test
    fun givenExistingUnencryptedProteusData_whenCallingOpenOrError_thenItMigratesExistingData() = runTest {
        val proteusStoreRef = createProteusStoreRef(alice.id)
        val unencryptedAliceClient = createProteusClient(proteusStoreRef)
        unencryptedAliceClient.openOrCreate()
        val previousFingerprint = unencryptedAliceClient.getLocalFingerprint()
        val encryptedAliceClient = createProteusClient(proteusStoreRef, PROTEUS_DB_SECRET)

        assertTrue(encryptedAliceClient.needsMigration())
        encryptedAliceClient.openOrError()

        assertEquals(previousFingerprint.decodeToString(), encryptedAliceClient.getLocalFingerprint().decodeToString())
        assertFalse(encryptedAliceClient.needsMigration())
    }

    @IgnoreJS
    @Test
    fun givenExistingUnencryptedProteusData_whenCallingOpenOrCreate_thenItMigratesExistingData() = runTest {
        val proteusStoreRef = createProteusStoreRef(alice.id)
        val unencryptedAliceClient = createProteusClient(proteusStoreRef)
        unencryptedAliceClient.openOrCreate()
        val previousFingerprint = unencryptedAliceClient.getLocalFingerprint()
        val encryptedAliceClient = createProteusClient(proteusStoreRef, PROTEUS_DB_SECRET)

        assertTrue(encryptedAliceClient.needsMigration())
        encryptedAliceClient.openOrCreate()

        assertEquals(previousFingerprint.decodeToString(), encryptedAliceClient.getLocalFingerprint().decodeToString())
        assertFalse(encryptedAliceClient.needsMigration())
    }

    @Test
    fun givenProteusClient_whenCallingNewLastKey_thenItReturnsALastPreKey() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        aliceClient.openOrCreate()
        val lastPreKey = aliceClient.newLastPreKey()
        assertEquals(65535, lastPreKey.id)
    }

    @Test
    fun givenProteusClient_whenCallingNewPreKeys_thenItReturnsAListOfPreKeys() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        aliceClient.openOrCreate()
        val preKeyList = aliceClient.newPreKeys(0, 10)
        assertEquals(preKeyList.size, 10)
    }

    @Test
    fun givenIncomingPreKeyMessage_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        aliceClient.openOrCreate()

        val bobClient = createProteusClient(createProteusStoreRef(bob.id))
        bobClient.openOrCreate()

        val message = "Hi Alice!"
        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        val encryptedMessage = bobClient.encryptWithPreKey(message.encodeToByteArray(), aliceKey, aliceSessionId)
        val decryptedMessage = aliceClient.decrypt(encryptedMessage, bobSessionId)
        assertEquals(message, decryptedMessage.decodeToString())
    }

    @Test
    fun givenSessionAlreadyExists_whenCallingDecrypt_thenMessageIsDecrypted() = runTest {
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        aliceClient.openOrCreate()

        val bobClient = createProteusClient(createProteusStoreRef(bob.id))
        bobClient.openOrCreate()

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
        val aliceClient = createProteusClient(createProteusStoreRef(alice.id))
        aliceClient.openOrCreate()

        val bobClient = createProteusClient(createProteusStoreRef(bob.id))
        bobClient.openOrCreate()

        val aliceKey = aliceClient.newPreKeys(0, 10).first()
        bobClient.createSession(aliceKey, aliceSessionId)
        assertNotNull(bobClient.encrypt("Hello World".encodeToByteArray(), aliceSessionId))
    }

    companion object {
        val PROTEUS_DB_SECRET = ProteusDBSecret("secret")
    }

}
