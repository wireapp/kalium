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

import kotlinx.coroutines.test.runTest
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

    private suspend fun createClient(user: SampleUser): MLSClient {
        return createMLSClient(
            clientId = user.qualifiedClientId,
            allowedCipherSuites = ALLOWED_CIPHER_SUITES,
            defaultCipherSuite = DEFAULT_CIPHER_SUITES
        )
    }

    @Test
    fun givenMlsClient_whenCallingGetDefaultCipherSuite_ReturnExpectedValue() = runTest {
        val mlsClient = createClient(ALICE1)
        assertEquals(DEFAULT_CIPHER_SUITES, mlsClient.getDefaultCipherSuite())
    }

    @Test
    fun givenClient_whenCallingGetPublicKey_ReturnNonEmptyResult() = runTest {
        val mlsClient = createClient(ALICE1)
        assertTrue(mlsClient.getPublicKey().first.isNotEmpty())
    }

    @Test
    fun givenClient_whenCallingGenerateKeyPackages_ReturnListOfExpectedSize() = runTest {
        val mlsClient = createClient(ALICE1)
        assertTrue(mlsClient.generateKeyPackages(10).isNotEmpty())
    }

    @Test
    fun givenNewConversation_whenCallingConversationEpoch_ReturnZeroEpoch() = runTest {
        val mlsClient = createClient(ALICE1)
        mlsClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        assertEquals(0UL, mlsClient.conversationEpoch(MLS_CONVERSATION_ID))
    }

    // TODO figure out why this test crashes on iosX64
    @IgnoreIOS
    @Test
    fun givenTwoClients_whenCallingUpdateKeyingMaterial_weCanProcessTheCommitMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)

        val aliceKeyPackage = aliceClient.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(aliceKeyPackage)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)?.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        val commit = bobClient.updateKeyingMaterial(MLS_CONVERSATION_ID).commit
        val result = aliceClient.decryptMessage(welcomeBundle.groupId, commit)

        assertNull(result.first().message)
    }

    @Test
    fun givenTwoClients_whenCallingCreateConversation_weCanProcessTheWelcomeMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)

        val aliceKeyPackage = aliceClient.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(aliceKeyPackage)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)!!.welcome!!
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, welcomeBundle.groupId)
    }

    @Test
    fun givenTwoClients_whenCallingJoinConversation_weCanProcessTheAddProposalMessage() = runTest {
        val alice1Client = createClient(ALICE1)
        val alice2Client = createClient(ALICE2)
        val bobClient = createClient(BOB1)

        val alice1KeyPackage = alice1Client.generateKeyPackages(1).first()
        val clientKeyPackageList = listOf(alice1KeyPackage)

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        val proposal = alice2Client.joinConversation(MLS_CONVERSATION_ID, 1UL)
        bobClient.decryptMessage(MLS_CONVERSATION_ID, proposal)
        val welcome = bobClient.commitPendingProposals(MLS_CONVERSATION_ID)?.welcome
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        val welcomeBundle = alice2Client.processWelcomeMessage(welcome!!)

        assertEquals(MLS_CONVERSATION_ID, welcomeBundle.groupId)
    }

    @Test
    fun givenTwoClients_whenCallingEncryptMessage_weCanDecryptTheMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)

        val clientKeyPackageList = listOf(aliceClient.generateKeyPackages(1).first())

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)?.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        val applicationMessage = aliceClient.encryptMessage(welcomeBundle.groupId, PLAIN_TEXT.encodeToByteArray())
        val plainMessage = bobClient.decryptMessage(welcomeBundle.groupId, applicationMessage).first().message

        assertEquals(PLAIN_TEXT, plainMessage?.decodeToString())
    }

    @Test
    fun givenTwoClients_whenCallingAddMember_weCanProcessTheWelcomeMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)

        val clientKeyPackageList = listOf(aliceClient.generateKeyPackages(1).first())

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)?.welcome!!
        bobClient.commitAccepted((MLS_CONVERSATION_ID))
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        assertEquals(MLS_CONVERSATION_ID, welcomeBundle.groupId)
    }

    @Test
    fun givenThreeClients_whenCallingAddMember_weCanProcessTheHandshakeMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)
        val carolClient = createClient(CAROL1)

        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )?.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)

        aliceClient.processWelcomeMessage(welcome)

        val commit = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )?.commit!!

        assertNull(aliceClient.decryptMessage(MLS_CONVERSATION_ID, commit).first().message)
    }

    @Test
    fun givenThreeClients_whenCallingRemoveMember_weCanProcessTheHandshakeMessage() = runTest {
        val aliceClient = createClient(ALICE1)
        val bobClient = createClient(BOB1)
        val carolClient = createClient(CAROL1)

        val clientKeyPackageList = listOf(
            aliceClient.generateKeyPackages(1).first(),
            carolClient.generateKeyPackages(1).first()
        )
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)
        val welcome = bobClient.addMember(MLS_CONVERSATION_ID, clientKeyPackageList)?.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        val welcomeBundle = aliceClient.processWelcomeMessage(welcome)

        val clientRemovalList = listOf(CAROL1.qualifiedClientId)
        val commit = bobClient.removeMember(welcomeBundle.groupId, clientRemovalList).commit

        assertNull(aliceClient.decryptMessage(welcomeBundle.groupId, commit).first().message)
    }

    @Test
    fun givenThreeClients_whenProcessingCommitOutOfOrder_shouldCatchBufferedFutureMessageAndBuffer() = runTest {
        // Bob creates a conversation.
        val bobClient = createClient(BOB1)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)

        // Bob adds Alice, Alice processes the welcome.
        val aliceClient = createClient(ALICE1)
        val welcomeAlice = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )!!.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        aliceClient.processWelcomeMessage(welcomeAlice)

        // Bob adds Carol but Alice does NOT process that commit => out of order for Alice later.
        val carolClient = createClient(CAROL1)
        val addCarolResult = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )
        val commitAddCarol = addCarolResult!!.commit
        bobClient.commitAccepted(MLS_CONVERSATION_ID)

        // Bob immediately removes Carol => definitely out of order for Alice.
        val removeCarolResult = bobClient.removeMember(MLS_CONVERSATION_ID, listOf(CAROL1.qualifiedClientId))
        val commitRemoveCarol = removeCarolResult.commit
        bobClient.commitAccepted(MLS_CONVERSATION_ID)

        // Alice tries to decrypt the removeCarol commit, which references an epoch Alice hasn't seen yet.
        // In normal MLS logic, this triggers a "buffering" error, typically thrown as MlsException.BufferedFutureMessage
        // wrapped in CoreCryptoException.Mls. The client code is supposed to swallow that error in a transaction
        // and return an empty DecryptedMessage list.

        val decryptedBundlesResult = runCatching {
            aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitRemoveCarol)
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
        val bobClient = createClient(BOB1)
        bobClient.createConversation(MLS_CONVERSATION_ID, externalSenderKey)

        val aliceClient = createClient(ALICE1)
        // Bob adds Alice to the conversation
        val welcomeForAlice = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(aliceClient.generateKeyPackages(1).first())
        )!!.welcome!!
        bobClient.commitAccepted(MLS_CONVERSATION_ID)
        aliceClient.processWelcomeMessage(welcomeForAlice)

        // Bob adds Carol, but Alice never sees this commit => out-of-order for Alice
        val carolClient = createClient(CAROL1)
        val addCarolResult = bobClient.addMember(
            MLS_CONVERSATION_ID,
            listOf(carolClient.generateKeyPackages(1).first())
        )
        val commitAddCarol = addCarolResult!!.commit
        bobClient.commitAccepted(MLS_CONVERSATION_ID)

        // Immediately Bob removes Carol => definitely out-of-order for Alice
        val removeCarolResult = bobClient.removeMember(MLS_CONVERSATION_ID, listOf(CAROL1.qualifiedClientId))
        val commitRemoveCarol = removeCarolResult.commit
        bobClient.commitAccepted(MLS_CONVERSATION_ID)

        // Alice tries to decrypt the removeCarol commit first => out-of-order => should buffer
        val removeResult = aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitRemoveCarol)
        assertTrue(
            removeResult.isEmpty(),
            "Out-of-order remove commit should be buffered and return an empty list."
        )

        // Now Alice processes the missing 'addCarol' commit.
        // By processing the addCarol commit, MLS should also flush any previously buffered commits (the removeCarol).
        val addResult = aliceClient.decryptMessage(MLS_CONVERSATION_ID, commitAddCarol)

        // We expect 2 total commits to be processed now: (1) addCarol, (2) removeCarol.
        assertEquals(
            2,
            addResult.size,
            "Processing the older 'addCarol' commit should also flush the buffered 'removeCarol' commit, resulting in 2 items."
        )
    }

    companion object {
        val externalSenderKey = ByteArray(32)
        val DEFAULT_CIPHER_SUITES = 1.toUShort()
        val ALLOWED_CIPHER_SUITES = listOf(1.toUShort())
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
}
