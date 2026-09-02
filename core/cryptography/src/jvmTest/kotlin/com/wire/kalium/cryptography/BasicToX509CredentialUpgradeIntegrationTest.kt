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

import com.wire.crypto.KeyPackageRef
import com.wire.kalium.cryptography.utils.calcSHA256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.hours

class BasicToX509CredentialUpgradeIntegrationTest {

    @Test
    fun givenBasicCredential_whenAcquiringX509FromItsRef_thenAcquisitionSucceedsAndReusesSigningKey() = runTest {
        withTestEnvironment(this) {
            val mlsClient = client
            mlsClient.initializeBasicCredential()

            val basicSigningKey = mlsClient.getPublicKey().first
            acquireX509UsingBasicCredential().use { acquiredCredential ->
                acquiredCredential.assertContainsCertificate()
                certificateAuthority.assertIssuedCertificateUses(basicSigningKey)
            }
        }
    }

    @Test
    fun givenBasicState_whenX509IsAcquiredButNotInstalled_thenBasicStateIsUntouched() = runTest {
        withTestEnvironment(this) {
            val mlsClient = client
            mlsClient.initializeBasicCredential()

            val basicSigningKey = mlsClient.getPublicKey().first
            mlsClient.requireBasicCredential().use { basicCredential ->
                mlsClient.transaction("prepareBasicStateBeforeAcquisition") {
                    it.createConversation(CONVERSATION_ID, certificateAuthority.externalSenderKey)
                    it.generateKeyPackages(KEY_PACKAGE_COUNT, basicCredential)
                }
            }

            acquireX509UsingBasicCredential().close()

            assertContentEquals(basicSigningKey, mlsClient.getPublicKey().first)
            mlsClient.assertCredentialCounts(basic = 1, x509 = 0)
            assertEquals(CredentialType.Basic, mlsClient.conversationCredentialType())
            assertEquals(KEY_PACKAGE_COUNT, central.countKeyPackages(CredentialType.Basic))
            assertEquals(0, central.countKeyPackages(CredentialType.X509))
        }
    }

    @Test
    fun givenPersistedBasicCredential_whenClientRestartsBeforeAcquisition_thenX509StillReusesSigningKey() = runTest {
        withTestEnvironment(this, configurePki = false) {
            val initialClient = client
            initialClient.initializeBasicCredential()
            val basicSigningKey = initialClient.getPublicKey().first

            val restartedClient = restartClient()
            acquireX509UsingBasicCredential().use {
                assertContentEquals(basicSigningKey, restartedClient.getPublicKey().first)
                certificateAuthority.assertIssuedCertificateUses(basicSigningKey)
                restartedClient.assertCredentialCounts(basic = 1, x509 = 0)
            }
        }
    }

    @Test
    fun givenUntrustedCertificate_whenAcquisitionFails_thenBasicCredentialCanBeRetried() = runTest {
        withTestEnvironment(this, trustRoot = false) {
            val mlsClient = client
            mlsClient.initializeBasicCredential()
            val basicSigningKey = mlsClient.getPublicKey().first

            assertFails {
                acquireX509UsingBasicCredential().close()
            }

            assertContentEquals(basicSigningKey, mlsClient.getPublicKey().first)
            mlsClient.assertCredentialCounts(basic = 1, x509 = 0)

            central.addPkiTrustAnchors(certificateAuthority.rootPem)
            acquireX509UsingBasicCredential().use { acquiredCredential ->
                acquiredCredential.assertContainsCertificate()
                certificateAuthority.assertIssuedCertificateUses(basicSigningKey)
                mlsClient.assertCredentialCounts(basic = 1, x509 = 0)
            }
        }
    }

    @Test
    fun givenX509CredentialWithoutBasic_whenInstalled_thenDefaultOperationsUseX509() = runTest {
        withTestEnvironment(this) {
            central.installCredential(
                central.startX509CredentialAcquisition(ACQUISITION_CONFIG, existingCredentialRef = null)
            ).use { installedCredential ->
                assertEquals(CredentialType.X509, installedCredential.credentialType())
            }

            val publicKey = client.getPublicKey().first
            certificateAuthority.assertIssuedCertificateUses(publicKey)
            client.transaction("useDefaultX509Credential") {
                it.createConversation(CONVERSATION_ID, certificateAuthority.externalSenderKey)
                it.generateKeyPackages(1)
            }

            client.assertCredentialCounts(basic = 0, x509 = 1)
            assertEquals(CredentialType.X509, client.conversationCredentialType())
            assertEquals(1, central.countKeyPackages(CredentialType.X509))
        }
    }

    @Ignore("WPB-26599: Core Crypto 10.4.0 addCredential fails with SQLite error 2067")
    @Test
    fun givenBasicCredential_whenUpgradingToX509_thenCompletesMigrationAndSurvivesRestart() = runTest {
        withTestEnvironment(this) {
            val initialClient = client
            initialClient.initializeBasicCredential()

            val basicCredentialHash = initialClient.requireBasicCredential().use { basicCredential ->
                initialClient.transaction("prepareBasicState") {
                    it.createConversation(CONVERSATION_ID, certificateAuthority.externalSenderKey)
                    it.generateKeyPackages(KEY_PACKAGE_COUNT, basicCredential)
                }
                basicCredential.publicKeyHash()
            }
            assertEquals(CredentialType.Basic, initialClient.conversationCredentialType())
            assertEquals(KEY_PACKAGE_COUNT, central.countKeyPackages(CredentialType.Basic))

            central.installCredential(acquireX509UsingBasicCredential()).use { installedX509Credential ->
                assertEquals(CredentialType.X509, installedX509Credential.credentialType())
                assertContentEquals(basicCredentialHash, installedX509Credential.publicKeyHash())

                initialClient.transaction("migrateConversationAndReplaceKeyPackages") {
                    it.setConversationCredential(CONVERSATION_ID, installedX509Credential)
                    it.removeKeyPackages(installedX509Credential)
                    it.generateKeyPackages(KEY_PACKAGE_COUNT, installedX509Credential)
                }
            }
            assertEquals(CredentialType.X509, initialClient.conversationCredentialType())
            assertEquals(0, central.countKeyPackages(CredentialType.Basic))
            assertEquals(KEY_PACKAGE_COUNT, central.countKeyPackages(CredentialType.X509))

            val restartedClient = restartClient(configurePki = false)

            assertContentEquals(basicCredentialHash, restartedClient.getPublicKey().first.publicKeyHash())
            assertEquals(CredentialType.X509, restartedClient.conversationCredentialType())
            assertEquals(KEY_PACKAGE_COUNT, central.countKeyPackages(CredentialType.X509))

            restartedClient.getCredentialRefs(CredentialType.Basic).useCredentialRefs { remainingBasicCredentials ->
                restartedClient.transaction("cleanupBasicCredential") { context ->
                    remainingBasicCredentials.forEach { context.removeCredential(it) }
                }
            }

            restartedClient.assertCredentialCounts(basic = 0, x509 = 1)
            assertEquals(KEY_PACKAGE_COUNT, central.countKeyPackages(CredentialType.X509))
        }
    }

    private suspend fun <T> withTestEnvironment(
        scope: CoroutineScope,
        configurePki: Boolean = true,
        trustRoot: Boolean = configurePki,
        block: suspend TestEnvironment.() -> T
    ): T {
        val root = Files.createTempDirectory("cc-basic-to-x509").toFile()
        val environment = TestEnvironment(root, scope)
        return try {
            environment.openClient(configurePki, trustRoot)
            environment.block()
        } finally {
            try {
                environment.close()
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private inner class TestEnvironment(
        private val root: File,
        private val scope: CoroutineScope
    ) {
        val certificateAuthority = X509TestCertificateAuthority()
        private var activeCentral: CoreCryptoCentralImpl? = null
        private var activeClient: MLSClient? = null

        val central: CoreCryptoCentralImpl
            get() = checkNotNull(activeCentral) { "Test environment is not open" }

        val client: MLSClient
            get() = checkNotNull(activeClient) { "Test environment is not open" }

        suspend fun openClient(configurePki: Boolean = true, trustRoot: Boolean = configurePki): MLSClient {
            check(activeCentral == null && activeClient == null) { "Test environment is already open" }
            require(configurePki || !trustRoot) { "A trust root requires a PKI environment" }

            val newCentral = coreCryptoCentral(root.absolutePath, PASSPHRASE) as CoreCryptoCentralImpl
            activeCentral = newCentral
            if (configurePki) {
                newCentral.configurePkiEnvironment(X509TestAcmeHooks(certificateAuthority))
                if (trustRoot) newCentral.addPkiTrustAnchors(certificateAuthority.rootPem)
            }
            return newCentral.mlsClient(
                clientId = CLIENT_ID,
                defaultCipherSuite = CIPHER_SUITE,
                mlsTransporter = NO_OP_TRANSPORTER,
                epochObserver = NO_OP_EPOCH_OBSERVER,
                coroutineScope = scope
            ).also { activeClient = it }
        }

        suspend fun restartClient(configurePki: Boolean = true, trustRoot: Boolean = configurePki): MLSClient {
            close()
            return openClient(configurePki, trustRoot)
        }

        suspend fun acquireX509UsingBasicCredential(): CryptoCredential =
            central.startX509CredentialAcquisition(
                config = ACQUISITION_CONFIG,
                existingCredentialRef = client.requireBasicCredential()
            )

        suspend fun close() {
            try {
                activeClient?.close() ?: activeCentral?.close()
            } finally {
                activeClient = null
                activeCentral = null
            }
        }
    }

    private suspend fun MLSClient.requireBasicCredential(): CryptoCredentialRef =
        requireNotNull(getCredentialRef(CredentialType.Basic)) { "Expected an installed Basic credential" }

    private suspend fun MLSClient.assertCredentialCounts(basic: Int, x509: Int) {
        assertEquals(basic, credentialCount(CredentialType.Basic), "Basic credential count")
        assertEquals(x509, credentialCount(CredentialType.X509), "X509 credential count")
    }

    private fun CryptoCredential.assertContainsCertificate() {
        assertContains(exportPem(), BEGIN_CERTIFICATE)
    }

    private fun X509TestCertificateAuthority.assertIssuedCertificateUses(expectedPublicKey: ByteArray) {
        assertContentEquals(
            expectedPublicKey,
            issuedPublicKeyBytes(expectedPublicKey.size)
        )
    }

    private suspend fun MLSClient.conversationCredentialType(): CredentialType = transaction("conversationCredentialType") {
        it.getConversationCredentialRef(CONVERSATION_ID).use(CryptoCredentialRef::credentialType)
    }

    private suspend fun MLSClient.credentialCount(type: CredentialType): Int =
        getCredentialRefs(type).useCredentialRefs(List<CryptoCredentialRef>::size)

    private suspend fun CoreCryptoCentralImpl.countKeyPackages(type: CredentialType): Int =
        transaction("countKeyPackages") { context ->
            context.getKeyPackages().useKeyPackageRefs { refs ->
                refs.count { it.credentialType().name.equals(type.name, ignoreCase = true) }
            }
        }

    private inline fun <T> CryptoCredential.use(block: (CryptoCredential) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private inline fun <T> CryptoCredentialRef.use(block: (CryptoCredentialRef) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private inline fun <T> List<CryptoCredentialRef>.useCredentialRefs(
        block: (List<CryptoCredentialRef>) -> T
    ): T = try {
        block(this)
    } finally {
        forEach(CryptoCredentialRef::close)
    }

    private inline fun <T> List<KeyPackageRef>.useKeyPackageRefs(block: (List<KeyPackageRef>) -> T): T = try {
        block(this)
    } finally {
        forEach(KeyPackageRef::close)
    }

    private companion object {
        const val KEY_PACKAGE_COUNT = 2
        const val BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----"
        const val CONVERSATION_ID = "JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY="
        val PASSPHRASE = ByteArray(32)
        val CIPHER_SUITE = MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        val CLIENT_ID = CryptoQualifiedClientId(
            value = "fb4b58152e20",
            userId = CryptoQualifiedID(
                value = "837655f7-b448-465a-b4b2-93f0919b38f0",
                domain = "wire.test"
            )
        )
        val ACQUISITION_CONFIG = X509CredentialAcquisitionConfig(
            acmeDirectoryUrl = X509TestAcmeHooks.DIRECTORY_URL,
            cipherSuite = CIPHER_SUITE,
            displayName = "Alice Smith",
            clientId = CLIENT_ID,
            handle = "alice",
            teamId = "team",
            validity = 1.hours
        )
        val NO_OP_TRANSPORTER = object : MLSTransporter {
            override suspend fun sendCommitBundle(commitBundle: CommitBundle) = Unit
        }
        val NO_OP_EPOCH_OBSERVER = object : MLSEpochObserver {
            override suspend fun onEpochChange(groupId: MLSGroupId, epoch: ULong) = Unit
        }
    }
}

private fun ByteArray.publicKeyHash(): ByteArray = calcSHA256(this)
