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
    fun givenANewClient_whenCreated_thenNoCredentialIsInstalledImplicitly() = runTest {
        withCredentialTestClient(this, initializeBasicCredential = false) { testClient ->
            assertTrue(testClient.client.getCredentialRefs(CredentialType.Basic).isEmpty())
            assertTrue(testClient.client.getCredentialRefs(CredentialType.X509).isEmpty())

            val publicKeyFailure = assertFailsWith<IllegalStateException> {
                testClient.client.getPublicKey()
            }
            assertEquals(NO_DEFAULT_CREDENTIAL_MESSAGE, publicKeyFailure.message)

            val keyPackageFailure = assertFailsWith<IllegalStateException> {
                testClient.client.transaction { it.generateKeyPackages(1) }
            }
            assertEquals(NO_DEFAULT_CREDENTIAL_MESSAGE, keyPackageFailure.message)
        }
    }

    @Test
    fun givenANewClient_whenBasicCredentialIsInitializedRepeatedly_thenExactlyOneCredentialIsInstalled() = runTest {
        withCredentialTestClient(this, initializeBasicCredential = false) { testClient ->
            testClient.client.initializeBasicCredential()
            testClient.client.initializeBasicCredential()

            assertEquals(1, testClient.client.getCredentialRefs(CredentialType.Basic).size)
            assertTrue(testClient.client.getPublicKey().first.isNotEmpty())
        }
    }

    @Test
    fun givenNewerBasicCredential_whenInstalled_thenDefaultOperationsUseIt() = runTest {
        withCredentialTestClient(this) { testClient ->
            val initialPublicKey = testClient.client.getPublicKey().first
            val newCredentialRef = testClient.addBasicCredential()

            newCredentialRef.close()

            assertFalse(initialPublicKey.contentEquals(testClient.client.getPublicKey().first))
            assertTrue(testClient.client.transaction { it.generateKeyPackages(1) }.isNotEmpty())
        }
    }

    @Test
    fun givenNewerBasicCredential_whenRemoved_thenDefaultOperationsUsePreviousCredential() = runTest {
        withCredentialTestClient(this) { testClient ->
            val initialPublicKey = testClient.client.getPublicKey().first
            val newCredentialRef = testClient.addBasicCredential()

            try {
                assertFalse(initialPublicKey.contentEquals(testClient.client.getPublicKey().first))
                testClient.client.transaction { it.removeCredential(newCredentialRef) }
                assertContentEquals(initialPublicKey, testClient.client.getPublicKey().first)
            } finally {
                newCredentialRef.close()
            }
        }
    }

    @Test
    fun givenAnExistingBasicCredential_whenReinitializingTheClient_thenItIsReusedInsteadOfCreatingAnother() = runTest {
        val root = Files.createTempDirectory("mls-credential-reuse").toFile()
        try {
            val firstClient = createCredentialTestClient(this, root.absolutePath, initializeBasicCredential = false)
            firstClient.client.initializeBasicCredential()
            val initialCredentialHash = firstClient.useAndGetBasicCredentialHash()

            val reopenedClient = createCredentialTestClient(this, root.absolutePath, initializeBasicCredential = false)
            try {
                val credentialRefs = reopenedClient.client.getCredentialRefs(CredentialType.Basic)
                assertEquals(1, credentialRefs.size)
                assertContentEquals(initialCredentialHash, credentialRefs.single().publicKeyHash())
                assertTrue(reopenedClient.client.getPublicKey().first.isNotEmpty())

                reopenedClient.client.initializeBasicCredential()
                assertEquals(1, reopenedClient.client.getCredentialRefs(CredentialType.Basic).size)
            } finally {
                reopenedClient.client.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun withCredentialTestClient(
        scope: CoroutineScope,
        initializeBasicCredential: Boolean = true,
        block: suspend (CredentialTestClient) -> Unit
    ) {
        val root = Files.createTempDirectory("mls-credential-selection").toFile()
        try {
            val testClient = createCredentialTestClient(scope, root.absolutePath, initializeBasicCredential)
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
        rootPath: String,
        initializeBasicCredential: Boolean = true
    ): CredentialTestClient {
        val central = coreCryptoCentral(rootPath, PASSPHRASE) as CoreCryptoCentralImpl
        return try {
            val client = central.mlsClient(
                clientId = CLIENT_ID,
                defaultCipherSuite = CIPHER_SUITE,
                mlsTransporter = NO_OP_TRANSPORTER,
                epochObserver = NO_OP_EPOCH_OBSERVER,
                coroutineScope = scope
            )
            if (initializeBasicCredential) client.initializeBasicCredential()
            CredentialTestClient(
                central = central,
                client = client
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

    private companion object {
        const val NO_DEFAULT_CREDENTIAL_MESSAGE =
            "MLS client has no default credential. Initialize Basic or install an X.509 credential first."
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
