package com.wire.kalium.cryptography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IgnoreJS
@IgnoreIOS
class MLSClientTest: BaseMLSClientTest() {

    data class SampleUser(val id: CryptoQualifiedID, val clientId: CryptoClientId, val name: String) {
        val qualifiedClientId: CryptoQualifiedClientId = CryptoQualifiedClientId(clientId.value, id)
    }

    private fun createClient(user: SampleUser): MLSClient {
        return createMLSClient(user.qualifiedClientId)
    }

    @Test
    fun givenClient_whenCallingGetPublicKey_ReturnNonEmptyResult() {
        val mlsClient = createClient(ALICE)
        assertTrue(mlsClient.getPublicKey().isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGenerateKeyPackages_ReturnListOfExpectedSize() {
        val mlsClient = createClient(ALICE)
        assertTrue(mlsClient.generateKeyPackages(10).isNotEmpty())
    }

    @Test
    fun givenTwoClients_whenCallingCreateConversation_weCanProcessTheWelcomeMessage() {
        val aliceClient = createClient(ALICE)
        val bobClient = createClient(BOB)

        val aliceKeyPackage = aliceClient.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(Pair(ALICE.qualifiedClientId, aliceKeyPackage))
        val (_, welcome) = bobClient.createConversation(MLS_CONVERSATION_ID, clientKeyPackageList)!!
        val conversationId = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, conversationId)
    }

    @Test
    fun givenTwoClients_whenCallingEncryptMessage_weCanDecryptTheMessage() {
        val aliceClient = createClient(ALICE)
        val bobClient = createClient(BOB)

        val clientKeyPackageList = listOf(
            Pair(ALICE.qualifiedClientId, aliceClient.generateKeyPackages(1).first())
        )
        val (_, welcome) = bobClient.createConversation(MLS_CONVERSATION_ID, clientKeyPackageList)!!
        val conversationId = aliceClient.processWelcomeMessage(welcome)

        val applicationMessage = aliceClient.encryptMessage(conversationId, PLAIN_TEXT.encodeToByteArray())
        val plainMessage = bobClient.decryptMessage(conversationId, applicationMessage)

        assertEquals(PLAIN_TEXT, plainMessage?.decodeToString())
    }

    @Test
    fun givenTwoClients_whenCallingAddMember_weCanProcessTheWelcomeMessage() {
        val aliceClient = createClient(ALICE)
        val bobClient = createClient(BOB)

        val clientKeyPackageList = listOf(
            Pair(ALICE.qualifiedClientId, aliceClient.generateKeyPackages(1).first())
        )
        bobClient.createConversation(MLS_CONVERSATION_ID, emptyList())
        val (_, welcome) = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)!!
        val conversationId = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, conversationId)
    }

    @Test
    fun givenThreeClients_whenCallingAddMember_weCanProcessTheHandshakeMessage() {
        val aliceClient = createClient(ALICE)
        val bobClient = createClient(BOB)
        val carolClient = createClient(CAROL)

        val (_, welcome) = bobClient.createConversation(MLS_CONVERSATION_ID, listOf(
            Pair(ALICE.qualifiedClientId, aliceClient.generateKeyPackages(1).first())
        ))!!

        aliceClient.processWelcomeMessage(welcome)

        val (handshake, _) = bobClient.addMember(MLS_CONVERSATION_ID, listOf(
            Pair(CAROL.qualifiedClientId, carolClient.generateKeyPackages(1).first())
        ))!!

        assertNull(aliceClient.decryptMessage(MLS_CONVERSATION_ID, handshake))
    }

    @Test
    fun givenThreeClients_whenCallingRemoveMember_weCanProcessTheHandshakeMessage() {
        val aliceClient = createClient(ALICE)
        val bobClient = createClient(BOB)
        val carolClient = createClient(CAROL)

        val clientKeyPackageList = listOf(
            Pair(ALICE.qualifiedClientId, aliceClient.generateKeyPackages(1).first()),
            Pair(CAROL.qualifiedClientId, carolClient.generateKeyPackages(1).first())
        )
        val (_, welcome) = bobClient.createConversation(MLS_CONVERSATION_ID, clientKeyPackageList)!!
        val conversationId = aliceClient.processWelcomeMessage(welcome)

        val clientRemovalList = listOf(CAROL.qualifiedClientId)
        val handshake = bobClient.removeMember(conversationId, clientRemovalList)!!

        assertNull(aliceClient.decryptMessage(conversationId, handshake))
    }

    companion object {
        const val MLS_CONVERSATION_ID = "JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY="
        const val PLAIN_TEXT = "Hello World"
        val ALICE = SampleUser(
            CryptoQualifiedID("837655f7-b448-465a-b4b2-93f0919b38f0", "wire.com"),
            CryptoClientId("fb4b58152e20"),
            "Alice")
        val BOB = SampleUser(
            CryptoQualifiedID("6980b74d-f358-4b1b-b7ad-557a77501e40", "wire.com"),
            CryptoClientId("ab4c8153e19"),
            "Bob")
        val CAROL = SampleUser(
            CryptoQualifiedID("2380b74d-f321-4c11-b7dd-552a74502e30", "wire.com"),
            CryptoClientId("244c2153e18"),
            "Carol")
    }

}
