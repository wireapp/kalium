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

import com.wire.crypto.Ciphersuite
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.MlsException
import com.wire.crypto.toClientId
import com.wire.crypto.toExternalSenderKey
import com.wire.crypto.toGroupId
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toMLSKeyPackage
import com.wire.crypto.toWelcome
import com.wire.kalium.cryptography.utils.toBundle
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import com.wire.kalium.cryptography.utils.toExternalSenderKey
import io.ktor.util.decodeBase64Bytes
import kotlin.time.Duration

typealias ConversationId = ByteArray

@Suppress("TooManyFunctions")
class MLSClientImpl(
    private val coreCrypto: CoreCrypto,
    private val defaultCipherSuite: Ciphersuite
) : MLSClient {

    override suspend fun close() {
        coreCrypto.close()
    }

    override fun getDefaultCipherSuite(): MLSCiphersuite {
        return defaultCipherSuite.toCryptography()
    }

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> {
        return coreCrypto.transaction("getPublicKey") { cc ->
            val mlsCredentialType = if (cc.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            cc.getPublicKey(defaultCipherSuite, mlsCredentialType.toCrypto()) to defaultCipherSuite.toCryptography()
        }
    }

    override suspend fun <R> transaction(name: String, block: suspend (context: MlsCoreCryptoContext) -> R): R {
        return coreCrypto.transaction(name) { context ->
            block(mlsCoreCryptoContext(context))
        }
    }

    private fun mlsCoreCryptoContext(context: CoreCryptoContext) = object : MlsCoreCryptoContext {
        override fun getDefaultCipherSuite(): MLSCiphersuite {
            return defaultCipherSuite.toCryptography()
        }

        override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.generateKeyPackages(amount.toUInt(), defaultCipherSuite, mlsCredentialType.toCrypto()).map {
                it.toByteArray()
            }
        }

        // // TODO workaround because copyBytes is not available
        @Suppress("MagicNumber")
        fun com.wire.crypto.MLSKeyPackage.toByteArray(): ByteArray =
            toString()
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

        override suspend fun validKeyPackageCount(): ULong {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.validKeyPackageCount(defaultCipherSuite, mlsCredentialType.toCrypto())
        }

        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
            return context.updateKeyingMaterial(groupId.decodeBase64Bytes().toGroupId())
        }

        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.joinByExternalCommit(
                publicGroupState.toGroupInfo(),
                mlsCredentialType.toCrypto(),
            ).toCryptography()
        }

        override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
            return context.conversationExists(groupId.decodeBase64Bytes().toGroupId())
        }

        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
            return context.conversationEpoch(groupId.decodeBase64Bytes().toGroupId())
        }

        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
            kaliumLogger.d("createConversation: using defaultCipherSuite=$defaultCipherSuite")
            val mlsCredentialType = credentialType(context)
            return context.createConversation(
                groupId.decodeBase64Bytes().toGroupId(),
                defaultCipherSuite,
                mlsCredentialType.toCrypto(),
                listOf(externalSenders.toExternalSenderKey())
            )
        }

        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
            return toExternalSenderKey(
                context.getExternalSender(groupId.decodeBase64Bytes().toGroupId()).copyBytes()
            )
        }

        override suspend fun wipeConversation(groupId: MLSGroupId) {
            return context.wipeConversation(groupId.decodeBase64Bytes().toGroupId())
        }

        override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
            return context.processWelcomeMessage(message.toWelcome()).toCryptography()
        }

        override suspend fun commitPendingProposals(groupId: MLSGroupId) {
            return context.commitPendingProposals(groupId.decodeBase64Bytes().toGroupId())
        }

        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
            return context.encryptMessage(groupId.decodeBase64Bytes().toGroupId(), message)
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun decryptMessage(
            groupId: MLSGroupId,
            message: ByteArray,
        ): List<DecryptedMessageBundle> {
            var decryptedMessage: DecryptedMessage? = null
            try {
                val result = context.decryptMessage(
                    groupId.decodeBase64Bytes().toGroupId(),
                    message
                )
                decryptedMessage = result

            } catch (throwable: Throwable) {
                val isBufferedFutureError = throwable is CoreCryptoException.Mls
                        && (
                        throwable.exception is MlsException.BufferedFutureMessage
                                || throwable.exception is MlsException.BufferedCommit
                        )
                if (!isBufferedFutureError) {
                    throw throwable
                }
            }

            if (decryptedMessage == null) {
                return emptyList()
            }

            val mainMessageBundle = listOf(decryptedMessage.toBundle())
            val bufferedBundles = decryptedMessage.bufferedMessages
                ?.map { it.toBundle() }
                ?: emptyList()

            return mainMessageBundle + bufferedBundles
        }

        override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
            return context.members(groupId.decodeBase64Bytes().toGroupId()).mapNotNull {
                CryptoQualifiedClientId.fromEncodedString(it.value.decodeToString())
            }
        }

        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): List<String>? {
            return if (membersKeyPackages.isNotEmpty()) {
                context.addMember(
                    groupId.decodeBase64Bytes().toGroupId(),
                    membersKeyPackages.map { keyPackage -> keyPackage.toMLSKeyPackage() }
                )
            } else {
                null
            }
        }

        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
            if (members.isNotEmpty()) {
                context.removeMember(
                    groupId.decodeBase64Bytes().toGroupId(),
                    members.map { it.toString().toClientId() }
                )
            }
        }

        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
            return context.deriveAvsSecret(groupId.decodeBase64Bytes().toGroupId(), keyLength).copyBytes()
        }

        override suspend fun e2eiNewActivationEnrollment(
            displayName: String,
            handle: String,
            teamId: String?,
            expiry: Duration
        ): E2EIClient {
            return E2EIClientImpl(
                context.e2eiNewActivationEnrollment(
                    displayName,
                    handle,
                    expiry.inWholeSeconds.toUInt(),
                    defaultCipherSuite,
                    teamId,
                )
            )
        }

        override suspend fun e2eiNewRotateEnrollment(
            displayName: String?,
            handle: String?,
            teamId: String?,
            expiry: Duration
        ): E2EIClient {
            return E2EIClientImpl(
                context.e2eiNewRotateEnrollment(
                    expiry.inWholeSeconds.toUInt(),
                    defaultCipherSuite,
                    displayName,
                    handle,
                    teamId,
                )
            )
        }

        override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
            return context.e2eiMlsInitOnly(
                (enrollment as E2EIClientImpl).wireE2eIdentity,
                certificateChain,
                null
            )?.value
                ?.map { it.toString() }
        }

        override suspend fun isE2EIEnabled(): Boolean {
            return context.e2eiIsEnabled(defaultCipherSuite)
        }

        override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState {
            return context.e2eiConversationState(groupId.decodeBase64Bytes().toGroupId()).toCryptography()
        }

        override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
            return context.getDeviceIdentities(
                groupId.decodeBase64Bytes().toGroupId(),
                clients.map { it.toString().toClientId() }
            )
                .mapNotNull {
                    it.toCryptography()
                }
        }

        override suspend fun getUserIdentities(
            groupId: MLSGroupId,
            users: List<CryptoQualifiedID>
        ): Map<String, List<WireIdentity>> {
            val usersIds = users.map {
                it.value
            }
            return context.getUserIdentities(groupId.decodeBase64Bytes().toGroupId(), usersIds)
                .mapValues {
                    it.value.mapNotNull { identity -> identity.toCryptography() }
                }
        }

        override suspend fun removeStaleKeyPackages() {
            return context.deleteStaleKeyPackages(defaultCipherSuite)
        }

        override suspend fun saveX509Credential(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
            return context.saveX509Credential((enrollment as E2EIClientImpl).wireE2eIdentity, certificateChain)

        }

        override suspend fun e2eiRotateGroups(groupList: List<MLSGroupId>) {
            groupList.forEach { groupId ->
                context.e2eiRotate(groupId.decodeBase64Bytes().toGroupId())
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration = try {
            context.e2eiRegisterCRL(url, crl).toCryptography()
        } catch (exception: Exception) {
            kaliumLogger.w("Registering Crl failed, exception: $exception")
            CrlRegistration(
                dirty = false,
                expiration = null
            )
        }
    }

    private suspend fun credentialType(context: CoreCryptoContext): CredentialType {
        return if (context.e2eiIsEnabled(defaultCipherSuite)) {
            CredentialType.X509
        } else {
            CredentialType.DEFAULT
        }
    }
}
