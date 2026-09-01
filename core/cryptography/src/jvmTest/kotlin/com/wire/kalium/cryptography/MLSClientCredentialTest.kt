/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.crypto.Credential
import com.wire.kalium.cryptography.utils.toCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MLSClientCredentialTest {

    @Test
    fun givenCredentialSelectionSucceeds_whenUsingDefaultOperations_thenTheSelectedCredentialIsUsed() = runTest {
        withCredentialTestClient(this) { testClient ->
            val initialPublicKey = testClient.client.getPublicKey().first
            val newCredentialRef = testClient.addBasicCredential()

            testClient.client.transaction("selectCredential") {
                it.selectCredential(newCredentialRef)
            }

            assertFalse(initialPublicKey.contentEquals(testClient.client.getPublicKey().first))
            assertTrue(testClient.client.transaction { it.generateKeyPackages(1) }.isNotEmpty())
        }
    }

    @Test
    fun givenCredentialSelectionTransactionFails_whenUsingDefaultOperations_thenThePreviousCredentialIsStillUsed() = runTest {
        withCredentialTestClient(this) { testClient ->
            val initialPublicKey = testClient.client.getPublicKey().first
            val newCredentialRef = testClient.addBasicCredential()

            assertFailsWith<ExpectedTransactionFailure> {
                testClient.client.transaction("failedCredentialSelection") {
                    it.selectCredential(newCredentialRef)
                    throw ExpectedTransactionFailure()
                }
            }

            assertContentEquals(initialPublicKey, testClient.client.getPublicKey().first)
        }
    }

    @Test
    fun givenAnExistingBasicCredential_whenReinitializingTheClient_thenItIsReusedInsteadOfCreatingAnother() = runTest {
        val root = Files.createTempDirectory("mls-credential-reuse").toFile()
        try {
            val firstClient = createCredentialTestClient(this, root.absolutePath)
            val initialCredentialHash = firstClient.useAndGetBasicCredentialHash()

            val reopenedClient = createCredentialTestClient(this, root.absolutePath)
            try {
                val credentialRefs = reopenedClient.client.getCredentialRefs(CredentialType.Basic)
                assertEquals(1, credentialRefs.size)
                assertContentEquals(initialCredentialHash, credentialRefs.single().publicKeyHash())
            } finally {
                reopenedClient.client.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun withCredentialTestClient(
        scope: CoroutineScope,
        block: suspend (CredentialTestClient) -> Unit
    ) {
        val root = Files.createTempDirectory("mls-credential-selection").toFile()
        try {
            val testClient = createCredentialTestClient(scope, root.absolutePath)
            try {
                block(testClient)
            } finally {
                testClient.client.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun createCredentialTestClient(
        scope: CoroutineScope,
        rootPath: String
    ): CredentialTestClient {
        val central = coreCryptoCentral(rootPath, PASSPHRASE) as CoreCryptoCentralImpl
        return try {
            CredentialTestClient(
                central = central,
                client = central.mlsClient(
                    clientId = CLIENT_ID,
                    defaultCipherSuite = CIPHER_SUITE,
                    mlsTransporter = NO_OP_TRANSPORTER,
                    epochObserver = NO_OP_EPOCH_OBSERVER,
                    coroutineScope = scope
                )
            )
        } catch (exception: Exception) {
            central.close()
            throw exception
        }
    }

    private data class CredentialTestClient(
        val central: CoreCryptoCentralImpl,
        val client: MLSClient
    ) {
        suspend fun addBasicCredential(): CryptoCredentialRef {
            // Core Crypto distinguishes credentials by a creation timestamp with one-second resolution.
            withContext(Dispatchers.Default) { delay(1_100) }

            val nativeClientId = CLIENT_ID.toCoreCryptoClientId()
            val credential = try {
                Credential.basic(CIPHER_SUITE.toCrypto(), nativeClientId)
            } finally {
                nativeClientId.close()
            }
            return try {
                CryptoCredentialRefImpl(central.transaction("addTestCredential") { it.addCredential(credential) })
            } finally {
                credential.close()
            }
        }

        suspend fun useAndGetBasicCredentialHash(): ByteArray {
            try {
                val credentialRefs = client.getCredentialRefs(CredentialType.Basic)
                assertEquals(1, credentialRefs.size)
                return credentialRefs.single().publicKeyHash()
            } finally {
                client.close()
            }
        }
    }

    private class ExpectedTransactionFailure : Exception()

    private companion object {
        val PASSPHRASE = ByteArray(32)
        val CIPHER_SUITE = MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        val CLIENT_ID = CryptoQualifiedClientId(
            value = "fb4b58152e20",
            userId = CryptoQualifiedID(
                value = "837655f7-b448-465a-b4b2-93f0919b38f0",
                domain = "wire.com"
            )
        )
        val NO_OP_TRANSPORTER = object : MLSTransporter {
            override suspend fun sendCommitBundle(commitBundle: CommitBundle) = Unit
        }
        val NO_OP_EPOCH_OBSERVER = object : MLSEpochObserver {
            override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
        }
    }
}
