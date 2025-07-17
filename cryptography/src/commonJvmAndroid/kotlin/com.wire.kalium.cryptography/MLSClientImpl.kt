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
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.GroupInfo
import com.wire.crypto.MlsException
import com.wire.crypto.MlsMessage
import com.wire.crypto.PlaintextMessage
import com.wire.crypto.Welcome
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
            cc.getPublicKey(defaultCipherSuite, mlsCredentialType.toCrypto()).value to defaultCipherSuite.toCryptography()
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
            return context.generateKeyPackages(amount.toUInt(), defaultCipherSuite, mlsCredentialType.toCrypto()).map { it.value }
        }

        override suspend fun validKeyPackageCount(): ULong {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.validKeyPackageCount(defaultCipherSuite, mlsCredentialType.toCrypto())
        }

        override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
            return context.updateKeyingMaterial(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }

        override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
            val mlsCredentialType = if (context.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            return context.joinByExternalCommit(
                GroupInfo(publicGroupState),
                mlsCredentialType.toCrypto(),
            ).toCryptography()
        }

        override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
            return context.conversationExists(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }

        override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
            return context.conversationEpoch(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }

        override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
            kaliumLogger.d("createConversation: using defaultCipherSuite=$defaultCipherSuite")
            val mlsCredentialType = credentialType(context)
            return context.createConversation(
                com.wire.crypto.MLSGroupId(
                    groupId.decodeBase64Bytes()
                ),
                defaultCipherSuite,
                mlsCredentialType.toCrypto(),
                listOf(com.wire.crypto.ExternalSenderKey(externalSenders))
            )
        }

        override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
            return toExternalSenderKey(
                context.getExternalSender(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())
                ).value
            )
        }

        override suspend fun wipeConversation(groupId: MLSGroupId) {
            return context.wipeConversation(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }

        override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
            return context.processWelcomeMessage(Welcome(message)).toCryptography()
        }

        override suspend fun commitPendingProposals(groupId: MLSGroupId) {
            return context.commitPendingProposals(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }

        override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
            return context.encryptMessage(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), PlaintextMessage(message)).value
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun decryptMessage(
            groupId: MLSGroupId,
            message: ByteArray,
        ): List<DecryptedMessageBundle> {
            var decryptedMessage: DecryptedMessage? = null
            try {
                val result = context.decryptMessage(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                    MlsMessage(message)
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
            return context.members(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())).mapNotNull {
                CryptoQualifiedClientId.fromEncodedString(it.value)
            }
        }

        override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): List<String>? {
            return if (membersKeyPackages.isNotEmpty()) {
                context.addMember(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                    membersKeyPackages.map { keyPackage -> com.wire.crypto.MLSKeyPackage(keyPackage) }
                )
            } else {
                null
            }
        }

        override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
            if (members.isNotEmpty()) {
                context.removeMember(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                    members.map { ClientId(it.toString()) }
                )
            }
        }

        override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
            return context.deriveAvsSecret(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), keyLength).value
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
            return context.e2eiConversationState(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())).toCryptography()
        }

        override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
            return context.getDeviceIdentities(
                com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                clients.map {
                    ClientId(it.toString())
                }
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
            return context.getUserIdentities(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), usersIds)
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
                context.e2eiRotate(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
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
