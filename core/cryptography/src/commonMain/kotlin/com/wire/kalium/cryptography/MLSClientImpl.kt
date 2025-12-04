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

import com.wire.crypto.CUSTOM_CONFIGURATION_DEFAULT
import com.wire.crypto.Ciphersuite
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCryptoClient
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.MlsException
import com.wire.crypto.toClientId
import com.wire.crypto.toExternalSenderKey
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toMLSKeyPackage
import com.wire.crypto.toWelcome
import com.wire.kalium.cryptography.utils.toBundle
import com.wire.kalium.cryptography.utils.toCrypto
import com.wire.kalium.cryptography.utils.toCryptography
import com.wire.kalium.cryptography.utils.toExternalSenderKey
import kotlin.time.Duration

typealias ConversationId = ByteArray

@Suppress("TooManyFunctions")
class MLSClientImpl(
    private val coreCrypto: CoreCryptoClient,
    private val defaultCipherSuite: Ciphersuite
) : MLSClient {

    override suspend fun close() {
        coreCrypto.close()
    }

    override fun getDefaultCipherSuite(): MLSCiphersuite {
        return defaultCipherSuite.toCryptography()
    }

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> {
        return coreCrypto.transaction { cc ->
            val mlsCredentialType = if (cc.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            cc.clientPublicKey(defaultCipherSuite, mlsCredentialType.toCrypto()) to defaultCipherSuite.toCryptography()
        }
    }

    override suspend fun <R> transaction(name: String, block: suspend (context: MlsCoreCryptoContext) -> R): R {
        return coreCrypto.transaction { context ->
            block(mlsCoreCryptoContext(context))
        }
    }

    private fun mlsCoreCryptoContext(context: CoreCryptoContext) = object : MlsCoreCryptoContext {
        override fun getDefaultCipherSuite(): MLSCiphersuite {
            return defaultCipherSuite.toCryptography()
        }

        override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.clientKeypackages(defaultCipherSuite, mlsCredentialType.toCrypto(), amount.toUInt())
                .map { it.copyBytes() }
        }

        override suspend fun validKeyPackageCount(): ULong {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.clientValidKeypackagesCount(defaultCipherSuite, mlsCredentialType.toCrypto())
        }

        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
            return context.updateKeyingMaterial(groupId.toCrypto())
        }

        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.joinByExternalCommit(
                groupInfo = publicGroupState.toGroupInfo(),
                customConfiguration = CUSTOM_CONFIGURATION_DEFAULT,
                credentialType = mlsCredentialType.toCrypto(),
            ).toCryptography()
        }

        override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
            return context.conversationExists(groupId.toCrypto())
        }

        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
            return context.conversationEpoch(groupId.toCrypto())
        }

        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
            kaliumLogger.d("createConversation: using defaultCipherSuite=$defaultCipherSuite")
            val mlsCredentialType = credentialType(context)
            val config = ConversationConfiguration(
                ciphersuite = defaultCipherSuite,
                externalSenders = listOf(externalSenders.toExternalSenderKey()),
                custom = CUSTOM_CONFIGURATION_DEFAULT
            )
            return context.createConversation(
                conversationId = groupId.toCrypto(),
                creatorCredentialType = mlsCredentialType.toCrypto(),
                config = config,
            )
        }

        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
            return toExternalSenderKey(
                context.getExternalSender(groupId.toCrypto()).copyBytes()
            )
        }

        override suspend fun wipeConversation(groupId: MLSGroupId) {
            return context.wipeConversation(groupId.toCrypto())
        }

        override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
            return context.processWelcomeMessage(message.toWelcome(), CUSTOM_CONFIGURATION_DEFAULT).toCryptography()
        }

        override suspend fun commitPendingProposals(groupId: MLSGroupId) {
            return context.commitPendingProposals(groupId.toCrypto())
        }

        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
            return context.encryptMessage(groupId.toCrypto(), message)
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun decryptMessage(
            groupId: MLSGroupId,
            message: ByteArray,
        ): List<DecryptedMessageBundle> {
            var decryptedMessage: DecryptedMessage? = null
            try {
                val result = context.decryptMessage(
                    groupId.toCrypto(),
                    message
                )
                decryptedMessage = result

            } catch (throwable: Throwable) {
                val isBufferedFutureError = throwable is CoreCryptoException.Mls
                        && (
                        throwable.mlsError is MlsException.BufferedFutureMessage
                                || throwable.mlsError is MlsException.BufferedCommit
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
            return context.getClientIds(groupId.toCrypto()).mapNotNull {
                CryptoQualifiedClientId.fromEncodedString(it.copyBytes().decodeToString())
            }
        }

        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): List<String>? {
            return if (membersKeyPackages.isNotEmpty()) {
                context.addClientsToConversation(
                    groupId.toCrypto(),
                    membersKeyPackages.map { keyPackage -> keyPackage.toMLSKeyPackage() }
                )
            } else {
                null
            }
        }

        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
            if (members.isNotEmpty()) {
                context.removeClientsFromConversation(
                    groupId.toCrypto(),
                    members.map { it.toString().toClientId() }
                )
            }
        }

        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
            return context.exportSecretKey(groupId.toCrypto(), keyLength).copyBytes()
        }

        override suspend fun e2eiNewActivationEnrollment(
            displayName: String,
            handle: String,
            teamId: String?,
            expiry: Duration
        ): E2EIClient {
            return E2EIClientImpl(
                context.e2eiNewActivationEnrollment(
                    displayName = displayName,
                    handle = handle,
                    team = teamId,
                    expirySec = expiry.inWholeSeconds.toUInt(),
                    ciphersuite = defaultCipherSuite,
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
                    displayName = displayName,
                    handle = handle,
                    team = teamId,
                    expirySec = expiry.inWholeSeconds.toUInt(),
                    ciphersuite = defaultCipherSuite,
                )
            )
        }

        override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
            return context.e2eiMlsInitOnly(
                (enrollment as E2EIClientImpl).wireE2eIdentity,
                certificateChain,
                null
            )
        }

        override suspend fun isE2EIEnabled(): Boolean {
            return context.e2eiIsEnabled(defaultCipherSuite)
        }

        override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState {
            return context.e2eiConversationState(groupId.toCrypto()).toCryptography()
        }

        override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
            return context.getDeviceIdentities(
                groupId.toCrypto(),
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
            return context.getUserIdentities(groupId.toCrypto(), usersIds)
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
                context.e2eiRotate(groupId.toCrypto())
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration = try {
            context.e2eiRegisterCrl(url, crl).toCryptography()
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
