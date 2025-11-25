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

import com.wire.kalium.cryptography.swift.CoreCryptoWrapper
import com.wire.kalium.cryptography.swift.DecryptedMessageWrapper
import com.wire.kalium.cryptography.swift.WelcomeBundleWrapper
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration

@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class)
class MLSClientImpl(
    private val wrapper: CoreCryptoWrapper,
    private val defaultCipherSuite: MLSCiphersuite
) : MLSClient {

    override fun getDefaultCipherSuite(): MLSCiphersuite {
        return defaultCipherSuite
    }

    override suspend fun close() {
        // CoreCrypto wrapper is managed by CoreCryptoCentral
    }

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> {
        val credentialType = if (isE2EIEnabled()) CredentialType.X509 else CredentialType.DEFAULT
        val publicKey = suspendCoroutine<ByteArray> { continuation ->
            wrapper.clientPublicKeyWithCiphersuite(
                ciphersuite = defaultCipherSuite.toTag(),
                credentialType = credentialType.toInt()
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }
        return publicKey to defaultCipherSuite
    }

    override suspend fun <R> transaction(name: String, block: suspend (context: MlsCoreCryptoContext) -> R): R {
        // In the Apple implementation, we don't have the same transaction model as JVM
        // We create a context wrapper that delegates directly to the CoreCryptoWrapper
        return block(createMlsCoreCryptoContext())
    }

    private suspend fun isE2EIEnabled(): Boolean {
        return suspendCoroutine { continuation ->
            wrapper.e2eiIsEnabledWithCiphersuite(ciphersuite = defaultCipherSuite.toTag()) { result, error ->
                if (error != null) {
                    continuation.resume(false)
                } else {
                    continuation.resume(result)
                }
            }
        }
    }

    private fun createMlsCoreCryptoContext(): MlsCoreCryptoContext = object : MlsCoreCryptoContext {

        override fun getDefaultCipherSuite(): MLSCiphersuite = defaultCipherSuite

        override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
            val credentialType = if (isE2EIEnabled()) CredentialType.X509 else CredentialType.DEFAULT
            return suspendCoroutine { continuation ->
                wrapper.clientKeypackagesWithCiphersuite(
                    ciphersuite = defaultCipherSuite.toTag(),
                    credentialType = credentialType.toInt(),
                    amountRequested = amount.toUInt()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.map { (it as platform.Foundation.NSData).toByteArray() })
                    }
                }
            }
        }

        override suspend fun validKeyPackageCount(): ULong {
            val credentialType = if (isE2EIEnabled()) CredentialType.X509 else CredentialType.DEFAULT
            return suspendCoroutine { continuation ->
                wrapper.clientValidKeypackagesCountWithCiphersuite(
                    ciphersuite = defaultCipherSuite.toTag(),
                    credentialType = credentialType.toInt()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result)
                    }
                }
            }
        }

        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
            suspendCoroutine { continuation ->
                wrapper.updateKeyingMaterialWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
            val credentialType = if (isE2EIEnabled()) CredentialType.X509 else CredentialType.DEFAULT
            return suspendCoroutine { continuation ->
                wrapper.joinByExternalCommitWithGroupInfo(
                    groupInfo = publicGroupState.toNSData(),
                    credentialType = credentialType.toInt()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toWelcomeBundle())
                    }
                }
            }
        }

        override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
            return suspendCoroutine { continuation ->
                wrapper.conversationExistsWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { result, error ->
                    if (error != null) {
                        continuation.resume(false)
                    } else {
                        continuation.resume(result)
                    }
                }
            }
        }

        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
            return suspendCoroutine { continuation ->
                wrapper.conversationEpochWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result)
                    }
                }
            }
        }

        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
            val credentialType = if (isE2EIEnabled()) CredentialType.X509 else CredentialType.DEFAULT
            suspendCoroutine { continuation ->
                wrapper.createConversationWithConversationId(
                    conversationId = groupId.decodeToByteArray().toNSData(),
                    credentialType = credentialType.toInt(),
                    ciphersuite = defaultCipherSuite.toTag()
                ) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
            return suspendCoroutine { continuation ->
                wrapper.getExternalSenderWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(ExternalSenderKey(result!!.toByteArray()))
                    }
                }
            }
        }

        override suspend fun wipeConversation(groupId: MLSGroupId) {
            suspendCoroutine { continuation ->
                wrapper.wipeConversationWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
            return suspendCoroutine { continuation ->
                wrapper.processWelcomeMessageWithWelcomeMessage(welcomeMessage = message.toNSData()) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toWelcomeBundle())
                    }
                }
            }
        }

        override suspend fun commitPendingProposals(groupId: MLSGroupId) {
            suspendCoroutine { continuation ->
                wrapper.commitPendingProposalsWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
            return suspendCoroutine { continuation ->
                wrapper.encryptMessageWithConversationId(
                    conversationId = groupId.decodeToByteArray().toNSData(),
                    message = message.toNSData()
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toByteArray())
                    }
                }
            }
        }

        override suspend fun decryptMessage(groupId: String, message: ByteArray): List<DecryptedMessageBundle> {
            return suspendCoroutine { continuation ->
                wrapper.decryptMessageWithConversationId(
                    conversationId = groupId.encodeToByteArray().toNSData(),
                    payload = message.toNSData()
                ) { result, error ->
                    if (error != null) {
                        // Handle buffered messages silently
                        continuation.resume(emptyList())
                    } else {
                        continuation.resume(listOf(result!!.toDecryptedMessageBundle()))
                    }
                }
            }
        }

        override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
            return suspendCoroutine { continuation ->
                wrapper.getClientIdsWithConversationId(conversationId = groupId.decodeToByteArray().toNSData()) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        val clientIds = result!!.mapNotNull { data ->
                            val bytes = (data as platform.Foundation.NSData).toByteArray()
                            CryptoQualifiedClientId.fromEncodedString(bytes.decodeToString())
                        }
                        continuation.resume(clientIds)
                    }
                }
            }
        }

        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): List<String>? {
            if (membersKeyPackages.isEmpty()) return null

            return suspendCoroutine { continuation ->
                wrapper.addClientsToConversationWithConversationId(
                    conversationId = groupId.decodeToByteArray().toNSData(),
                    keyPackages = membersKeyPackages.map { it.toNSData() }
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as? List<String>)
                    }
                }
            }
        }

        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
            if (members.isEmpty()) return

            suspendCoroutine { continuation ->
                wrapper.removeClientsFromConversationWithConversationId(
                    conversationId = groupId.decodeToByteArray().toNSData(),
                    clientIds = members.map { it.toString().encodeToByteArray().toNSData() }
                ) { error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
            return suspendCoroutine { continuation ->
                wrapper.exportSecretKeyWithConversationId(
                    conversationId = groupId.decodeToByteArray().toNSData(),
                    keyLength = keyLength
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        continuation.resume(result!!.toByteArray())
                    }
                }
            }
        }

        override suspend fun e2eiNewActivationEnrollment(
            displayName: String,
            handle: String,
            teamId: String?,
            expiry: Duration
        ): E2EIClient {
            // This requires creating enrollment through CoreCryptoCentral
            TODO("E2EI activation enrollment should be created through CoreCryptoCentral")
        }

        override suspend fun e2eiNewRotateEnrollment(
            displayName: String?,
            handle: String?,
            teamId: String?,
            expiry: Duration
        ): E2EIClient {
            // This requires creating enrollment through CoreCryptoCentral
            TODO("E2EI rotate enrollment should be created through CoreCryptoCentral")
        }

        override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
            val enrollmentWrapper = (enrollment as E2EIClientImpl).enrollmentWrapper
            return suspendCoroutine { continuation ->
                wrapper.e2eiMlsInitOnlyWithEnrollment(
                    enrollment = enrollmentWrapper,
                    certificateChain = certificateChain,
                    nbKeyPackage = 0
                ) { result, error ->
                    if (error != null) {
                        continuation.resumeWithException(error.toKotlinException())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as? List<String>)
                    }
                }
            }
        }

        override suspend fun isE2EIEnabled(): Boolean {
            return this@MLSClientImpl.isE2EIEnabled()
        }

        override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState {
            // TODO: Implement when wrapper supports e2eiConversationState
            return E2EIConversationState.NOT_ENABLED
        }

        override suspend fun getDeviceIdentities(
            groupId: MLSGroupId,
            clients: List<CryptoQualifiedClientId>
        ): List<WireIdentity> {
            // TODO: Implement when wrapper supports getDeviceIdentities
            return emptyList()
        }

        override suspend fun getUserIdentities(
            groupId: MLSGroupId,
            users: List<CryptoQualifiedID>
        ): Map<String, List<WireIdentity>> {
            // TODO: Implement when wrapper supports getUserIdentities
            return emptyMap()
        }

        override suspend fun removeStaleKeyPackages() {
            // TODO: Implement when wrapper supports deleteStaleKeyPackages
        }

        override suspend fun saveX509Credential(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
            // TODO: Implement when wrapper supports saveX509Credential
            return null
        }

        override suspend fun e2eiRotateGroups(groupList: List<MLSGroupId>) {
            // TODO: Implement when wrapper supports e2eiRotate
        }

        override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration {
            return suspendCoroutine { continuation ->
                wrapper.e2eiRegisterCrlWithCrlDp(crlDp = url, crlDer = crl.toNSData()) { result, error ->
                    if (error != null) {
                        kaliumLogger.w("Registering Crl failed, exception: ${error.localizedDescription}")
                        continuation.resume(CrlRegistration(dirty = false, expiration = null))
                    } else {
                        continuation.resume(
                            CrlRegistration(
                                dirty = result!!.dirty(),
                                expiration = result.expiration().takeIf { it != 0UL }
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        private fun CredentialType.toInt(): Int = when (this) {
            CredentialType.Basic -> 0
            CredentialType.X509 -> 1
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun WelcomeBundleWrapper.toWelcomeBundle() = WelcomeBundle(
            groupId = this.id().toByteArray().decodeToString(),
            crlNewDistributionPoints = this.crlNewDistributionPoints()?.map { it as String }
        )

        @OptIn(ExperimentalForeignApi::class)
        private fun DecryptedMessageWrapper.toDecryptedMessageBundle() = DecryptedMessageBundle(
            message = this.message()?.toByteArray(),
            commitDelay = this.commitDelay().takeIf { it != 0UL }?.toLong(),
            senderClientId = this.senderClientId()?.toByteArray()?.let {
                CryptoQualifiedClientId.fromEncodedString(it.decodeToString())
            },
            hasEpochChanged = this.hasEpochChanged(),
            identity = this.identity()?.toWireIdentity(),
            crlNewDistributionPoints = this.crlNewDistributionPoints()?.map { it as String }
        )

        @OptIn(ExperimentalForeignApi::class)
        private fun com.wire.kalium.cryptography.swift.WireIdentityWrapper.toWireIdentity(): WireIdentity {
            val clientId = CryptoQualifiedClientId.fromEncodedString(this.clientId())
                ?: CryptoQualifiedClientId("", CryptoUserID("", ""))
            val status = when (this.status().toInt()) {
                0 -> CryptoCertificateStatus.REVOKED
                1 -> CryptoCertificateStatus.EXPIRED
                2 -> CryptoCertificateStatus.VALID
                else -> CryptoCertificateStatus.VALID
            }
            val credentialType = when (this.credentialType().toInt()) {
                0 -> CredentialType.Basic
                1 -> CredentialType.X509
                else -> CredentialType.Basic
            }
            val x509Identity = this.handle()?.let { handle ->
                val domain = this.domain() ?: ""
                WireIdentity.X509Identity(
                    handle = WireIdentity.Handle.fromString(handle, domain),
                    displayName = this.displayName() ?: "",
                    domain = domain,
                    certificate = this.certificate() ?: "",
                    serialNumber = this.serialNumber() ?: "",
                    notBefore = this.notBefore().toLong(),
                    notAfter = this.notAfter().toLong()
                )
            }
            return WireIdentity(
                clientId = clientId,
                status = status,
                thumbprint = this.thumbprint(),
                credentialType = credentialType,
                x509Identity = x509Identity
            )
        }

        private fun MLSGroupId.decodeToByteArray(): ByteArray = this.encodeToByteArray()
    }
}
