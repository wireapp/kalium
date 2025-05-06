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

import com.wire.kalium.cryptography.MLSClientTest.Arrangement.Companion.create
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IgnoreJS
@IgnoreIOS
class MLSClientTest : BaseMLSClientTest() {

    data class SampleUser(val id: CryptoQualifiedID, val clientId: CryptoClientId, val name: String) {
        val qualifiedClientId: CryptoQualifiedClientId = CryptoQualifiedClientId(clientId.value, id)
    }

    @Test
    fun givenMlsClient_whenCallingGetDefaultCipherSuite_ReturnExpectedValue() = runTest {
        val arrangement = create(
            ALICE1,
            ::createMLSClient,
        )
        assertEquals(DEFAULT_CIPHER_SUITES, arrangement.mlsClient.getDefaultCipherSuite())
    }

    @Test
    fun givenClient_whenCallingGetPublicKey_ReturnNonEmptyResult() = runTest {
        val arrangement = create(
            ALICE1,
            ::createMLSClient,
        )
        assertTrue(arrangement.mlsClient.getPublicKey().first.isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGenerateKeyPackages_ReturnListOfExpectedSize() = runTest {
        val arrangement = create(
            ALICE1,
            ::createMLSClient,
        )
        assertTrue(arrangement.mlsClient.generateKeyPackages(10).isNotEmpty())
    }

    @Test
    fun givenNewConversation_whenCallingConversationEpoch_ReturnZeroEpoch() = runTest {
        val arrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        arrangement.mlsClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        assertEquals(0UL, arrangement.mlsClient.conversationEpoch(MLS_CONVERSATION_ID))
    }

    // TODO figure out why this test crashes on iosX64
    @IgnoreIOS
    @Test
    fun givenTwoClients_whenCallingUpdateKeyingMaterial_weCanProcessTheCommitMessage() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        val aliceKeyPackage = aliceClient.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(aliceKeyPackage)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        val welcome = bobArrangement.sendCommitBundleFlow.first()

        val welcomeBundle = aliceClient.processWelcomeMessage(welcome.first.welcome!!)

        bobClient.updateKeyingMaterial(MLS_CONVERSATION_ID)

        val keyMaterialCommit = bobArrangement.sendCommitBundleFlow.first()

        val result = aliceClient.decryptMessage(welcomeBundle.groupId, keyMaterialCommit.first.commit,  Clock.System.now())

        assertNull(result.first().message)
    }

    @Test
    fun givenTwoClients_whenCallingCreateConversation_weCanProcessTheWelcomeMessage() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        val aliceKeyPackage = aliceClient.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(aliceKeyPackage)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, welcomeBundle.groupId)
    }

    @Test
    fun givenTwoClients_whenCallingEncryptMessage_weCanDecryptTheMessage() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        val clientKeyPackageList = listOf(aliceClient.generateKeyPackages(1).first())

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        val applicationMessage = aliceClient.encryptMessage(welcomeBundle.groupId, PLAIN_TEXT.encodeToByteArray())
        val plainMessage = bobClient.decryptMessage(welcomeBundle.groupId, applicationMessage,  Clock.System.now()).first().message

        assertEquals(PLAIN_TEXT, plainMessage?.decodeToString())
    }

    @Test
    fun givenTwoClients_whenCallingAddMember_weCanProcessTheWelcomeMessage() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        val clientKeyPackageList = listOf(aliceClient.generateKeyPackages(1).first())

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, welcomeBundle.groupId)
    }

    @Test
    fun givenThreeClients_whenCallingAddMember_weCanProcessTheHandshakeMessage() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val carolArrangement = create(
            CAROL1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient
        val carolClient = carolArrangement.mlsClient

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        aliceClient.processWelcomeMessage(welcome)

        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )
        val commit = bobArrangement.sendCommitBundleFlow.first().first.commit

        assertNull(aliceClient.decryptMessage(MLS_CONVERSATION_ID, commit,  Clock.System.now()).first().message)
    }

    @Test
    fun givenThreeClients_whenCallingRemoveMember_weCanProcessTheHandshakeMessage() = runTest {

        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val carolArrangement = create(
            CAROL1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient
        val carolClient = carolArrangement.mlsClient

        val clientKeyPackageList = listOf(
            aliceClient.generateKeyPackages(1).first(),
            carolClient.generateKeyPackages(1).first()
        )
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)


        val clientRemovalList = listOf(CAROL1.qualifiedClientId)
        bobClient.removeMember(welcomeBundle.groupId, clientRemovalList)
        val commit = bobArrangement.sendCommitBundleFlow.first().first.commit
        assertNull(aliceClient.decryptMessage(welcomeBundle.groupId, commit, Clock.System.now()).first().message)
    }

    @Test
    fun givenThreeClients_whenProcessingCommitOutOfOrder_shouldCatchBufferedFutureMessageAndBuffer() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val carolArrangement = create(
            CAROL1,
            ::createMLSClient,
        )

        // Bob creates a conversation.
        val bobClient = bobArrangement.mlsClient
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)

        // Bob adds Alice, Alice processes the welcome.
        val aliceClient = aliceArrangement.mlsClient
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )
        val welcomeAlice = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        aliceClient.processWelcomeMessage(welcomeAlice)

        // Bob adds Carol but Alice does NOT process that commit => out of order for Alice later.
        val carolClient = carolArrangement.mlsClient
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )

        // Bob immediately removes Carol => definitely out of order for Alice.
        bobClient.removeMember(MLS_CONVERSATION_ID, listOf(CAROL1.qualifiedClientId))
        val commitRemoveCarol = bobArrangement.sendCommitBundleFlow.first().first.commit

        // Alice tries to decrypt the removeCarol commit, which references an epoch Alice hasn't seen yet.
        // In normal MLS logic, this triggers a "buffering" error, typically thrown as MlsException.BufferedFutureMessage
        // wrapped in CoreCryptoException.Mls. The client code is supposed to swallow that error in a transaction
        // and return an empty DecryptedMessage list.

        val decryptedBundlesResult = runCatching {
            aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitRemoveCarol,  Clock.System.now())
        }

        // The exception should be caught internally, so from the caller's perspective we succeed with an empty result.
        // That indicates the message was buffered instead of fully decrypted.
        assertTrue(
            decryptedBundlesResult.isSuccess,
            "Out-of-order commit should not propagate BufferedFutureMessage as an unhandled exception."
        )

        val decryptedBundles = decryptedBundlesResult.getOrThrow()
        assertTrue(
            decryptedBundles.isEmpty(),
            "Decryption result should be empty for a buffered out-of-order commit."
        )
    }

    @Test
    fun givenOutOfOrderCommits_whenProcessingMissingCommitLater_shouldAlsoProcessBufferedOne() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val carolArrangement = create(
            CAROL1,
            ::createMLSClient,
        )

        val bobClient = bobArrangement.mlsClient
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)

        val aliceClient = aliceArrangement.mlsClient
        // Bob adds Alice to the conversation
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )
        val welcomeForAlice = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        aliceClient.processWelcomeMessage(welcomeForAlice)

        // Bob adds Carol, but Alice never sees this commit => out-of-order for Alice
        val carolClient = carolArrangement.mlsClient
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )
        val commitAddCarol = bobArrangement.sendCommitBundleFlow.first().first.commit

        // Immediately Bob removes Carol => definitely out-of-order for Alice
        bobClient.removeMember(MLS_CONVERSATION_ID, listOf(CAROL1.qualifiedClientId))
        val commitRemoveCarol = bobArrangement.sendCommitBundleFlow.first().first.commit

        // Alice tries to decrypt the removeCarol commit first => out-of-order => should buffer
        val removeResult = aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitRemoveCarol,  Clock.System.now())
        assertTrue(
            removeResult.isEmpty(),
            "Out-of-order remove commit should be buffered and return an empty list."
        )

        // Now Alice processes the missing 'addCarol' commit.
        // By processing the addCarol commit, MLS should also flush any previously buffered commits (the removeCarol).
        val addResult = aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitAddCarol,  Clock.System.now())

        val epoch = aliceArrangement.epochChangeFlow.first()

        // We expect 2 total commits to be processed now: (1) addCarol, (2) removeCarol.
        assertEquals(
            2,
            addResult.size,
            "Processing the older 'addCarol' commit should also flush the buffered 'removeCarol' commit, resulting in 2 items."
        )
        assertEquals(
            2UL,
            epoch.second,
            "Epoch should be incremented after processing the 'addCarol' and 'removeCarol' commit."
        )
    }

    @Test
    fun givenBatchOfMessages_whenCallingDecryptMessages_weGetDecryptedBatch() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )

        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        // Set up
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        // Encrypt a few messages
        val messages = (1..3).map { idx ->
            val message = aliceClient.encryptMessage(welcomeBundle.groupId, "Test message $idx".encodeToByteArray())
            EncryptedMessage(
                content = message,
                eventId = "event-$idx",
                messageInstant = Clock.System.now()
            )
        }

        // Decrypt batch
        val decryptedBatch = bobClient.decryptMessages(welcomeBundle.groupId, messages)

        // Assertions
        assertEquals(3, decryptedBatch.messages.size)
        decryptedBatch.messages.forEachIndexed { idx, messageBundle ->
            assertTrue(messageBundle.message?.decodeToString()?.contains("Test message ${idx + 1}") == true)
        }
    }

    @Test
    fun givenDuplicateMessageInBatch_whenDecrypting_shouldIgnoreAndContinue() = runTest {
        val aliceArrangement = create(
            ALICE1,
            ::createMLSClient,
        )
        val bobArrangement = create(
            BOB1,
            ::createMLSClient,
        )

        val aliceClient = aliceArrangement.mlsClient
        val bobClient = bobArrangement.mlsClient

        // Set up conversation
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )
        val welcome = bobArrangement.sendCommitBundleFlow.first().first.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        // Alice encrypts two messages
        val message1 = aliceClient.encryptMessage(welcomeBundle.groupId, "First".encodeToByteArray())
        val message2 = aliceClient.encryptMessage(welcomeBundle.groupId, "Second".encodeToByteArray())

        val encryptedMessages = listOf(
            EncryptedMessage(
                content = message1,
                eventId = "event-1",
                messageInstant = Clock.System.now()
            ),
            EncryptedMessage(
                content = message1, // <- intentionally duplicate!
                eventId = "event-duplicate",
                messageInstant = Clock.System.now()
            ),
            EncryptedMessage(
                content = message2,
                eventId = "event-2",
                messageInstant = Clock.System.now()
            )
        )

        // First decrypt call: decrypt all messages (no duplicates yet)
        val firstDecrypt = bobClient.decryptMessages(welcomeBundle.groupId, encryptedMessages.take(1))
        assertEquals(1, firstDecrypt.messages.size)

        // Second decrypt call: batch with duplicate + new message
        val secondDecrypt = bobClient.decryptMessages(welcomeBundle.groupId, encryptedMessages)

        // Should decrypt only the second NEW message
        assertEquals(1, secondDecrypt.messages.size)
        assertTrue(secondDecrypt.messages.first().message?.decodeToString()?.contains("Second") == true)
    }




    companion object {
        val externalSenderKey = ByteArray(32)
        val DEFAULT_CIPHER_SUITES = MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val ALLOWED_CIPHER_SUITES = listOf(MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)
        const val MLS_CONVERSATION_ID = "JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY="
        const val PLAIN_TEXT = "Hello World"
        val ALICE1 = SampleUser(
            CryptoQualifiedID("837655f7-b448-465a-b4b2-93f0919b38f0", "wire.com"),
            CryptoClientId("fb4b58152e20"),
            "Alice"
        )
        val ALICE2 = ALICE1.copy(clientId = CryptoClientId("fb4b58152e21"))
        val BOB1 = SampleUser(
            CryptoQualifiedID("6980b74d-f358-4b1b-b7ad-557a77501e40", "wire.com"),
            CryptoClientId("ab4c8153e19"),
            "Bob"
        )
        val CAROL1 = SampleUser(
            CryptoQualifiedID("2380b74d-f321-4c11-b7dd-552a74502e30", "wire.com"),
            CryptoClientId("244c2153e18"),
            "Carol"
        )
    }

    class Arrangement private constructor(
        val user: SampleUser,
        val mlsClient: MLSClient,
        val sendMessageFlow: MutableSharedFlow<Pair<ByteArray, MlsTransportResponse>>,
        val sendCommitBundleFlow: MutableSharedFlow<Pair<CommitBundle, MlsTransportResponse>>,
        val epochChangeFlow: MutableSharedFlow<Pair<MLSGroupId, ULong>>,
        private val sendMessageResponses: MutableList<MlsTransportResponse>,
        private val sendCommitResponses: MutableList<MlsTransportResponse>,
        private val mutex: Mutex,

        ) {

        companion object {
            suspend fun CoroutineScope.create(
                user: SampleUser,
                createMLSClient: suspend (
                    clientId: CryptoQualifiedClientId,
                    allowedCipherSuites: List<MLSCiphersuite>,
                    defaultCipherSuite: MLSCiphersuite,
                    mlsTransporter: MLSTransporter,
                    epochObserver: MLSEpochObserver,
                    coroutineScope: CoroutineScope
                ) -> MLSClient,
                initialSendMessageResponses: List<MlsTransportResponse> = listOf(MlsTransportResponse.Success),
                initialSendCommitResponses: List<MlsTransportResponse> = listOf(MlsTransportResponse.Success)
            ): Arrangement {
                val sendMessageFlow = MutableSharedFlow<Pair<ByteArray, MlsTransportResponse>>(replay = 1)
                val sendCommitBundleFlow = MutableSharedFlow<Pair<CommitBundle, MlsTransportResponse>>(replay = 1)
                val epochChangeFlow = MutableSharedFlow<Pair<MLSGroupId, ULong>>(replay = 1)

                val sendMessageResponses = initialSendMessageResponses.toMutableList()
                val sendCommitResponses = initialSendCommitResponses.toMutableList()
                val mutex = Mutex()

                val mlsTransporter = object : MLSTransporter {
                    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse {
                        val response = mutex.withLock {
                            if (sendMessageResponses.isNotEmpty()) sendMessageResponses.removeFirst() else MlsTransportResponse.Success
                        }
                        sendMessageFlow.emit(mlsMessage to response)
                        return response
                    }

                    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
                        val response = mutex.withLock {
                            if (sendCommitResponses.isNotEmpty()) sendCommitResponses.removeFirst() else MlsTransportResponse.Success
                        }
                        sendCommitBundleFlow.emit(commitBundle to response)
                        return response
                    }
                }

                val epochObserver = object : MLSEpochObserver {
                    override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) {
                        epochChangeFlow.emit(groupId to epoch)
                    }
                }

                val mlsClient = createMLSClient(
                    user.qualifiedClientId,
                    ALLOWED_CIPHER_SUITES,
                    DEFAULT_CIPHER_SUITES,
                    mlsTransporter,
                    epochObserver,
                    this,
                )

                return Arrangement(
                    user,
                    mlsClient,
                    sendMessageFlow,
                    sendCommitBundleFlow,
                    epochChangeFlow,
                    sendMessageResponses,
                    sendCommitResponses,
                    mutex
                )
            }
        }

        suspend fun addSendMessageResponse(response: MlsTransportResponse) {
            mutex.withLock { sendMessageResponses.add(response) }
        }

        suspend fun addSendCommitBundleResponse(response: MlsTransportResponse) {
            mutex.withLock { sendCommitResponses.add(response) }
        }
    }

}
