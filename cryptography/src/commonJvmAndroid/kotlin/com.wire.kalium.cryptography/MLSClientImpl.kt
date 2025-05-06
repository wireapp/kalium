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
import kotlinx.datetime.Instant
import kotlin.time.Duration

typealias ConversationId = ByteArray

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
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
        return coreCrypto.transaction { cc ->
            val mlsCredentialType = if (cc.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            cc.getPublicKey(defaultCipherSuite, mlsCredentialType.toCrypto()).value to defaultCipherSuite.toCryptography()
        }
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.transaction {
            val mlsCredentialType = if (it.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            it.generateKeyPackages(amount.toUInt(), defaultCipherSuite, mlsCredentialType.toCrypto()).map { it.value }
        }
    }

    override suspend fun removeStaleKeyPackages() {
        return coreCrypto.transaction {
            it.deleteStaleKeyPackages(defaultCipherSuite)
        }
    }

    override suspend fun validKeyPackageCount(): ULong {
        return coreCrypto.transaction {
            val mlsCredentialType = if (it.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            it.validKeyPackageCount(defaultCipherSuite, mlsCredentialType.toCrypto())
        }
    }

    override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
        return coreCrypto.transaction {
            it.updateKeyingMaterial(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }
    }

    override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.transaction {
            it.conversationExists(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }
    }

    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
        return coreCrypto.transaction {
            it.conversationEpoch(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
        return coreCrypto.transaction {
            val mlsCredentialType = if (it.e2eiIsEnabled(defaultCipherSuite)) CredentialType.X509 else CredentialType.DEFAULT
            it.joinByExternalCommit(
                GroupInfo(publicGroupState),
                mlsCredentialType.toCrypto(),
            ).toCryptography()
        }
    }

    override suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: ByteArray
    ) {
        kaliumLogger.d("createConversation: using defaultCipherSuite=$defaultCipherSuite")
        coreCrypto.transaction {
            val mlsCredentialType = credentialType(it)

            it.createConversation(
                com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                defaultCipherSuite,
                mlsCredentialType.toCrypto(),
                listOf(com.wire.crypto.ExternalSenderKey(externalSenders))
            )
        }
    }

    override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
        return coreCrypto.transaction {
            toExternalSenderKey(it.getExternalSender(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())).value)
        }
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.transaction {
            it.wipeConversation(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage) = coreCrypto.transaction {
        it.processWelcomeMessage(Welcome(message)).toCryptography()
    }

    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        return coreCrypto.transaction {
            it.encryptMessage(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), PlaintextMessage(message)).value
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun decryptMessage(
        groupId: MLSGroupId,
        message: ApplicationMessage,
        messageInstant: Instant
    ): List<DecryptedMessageBundle> {
        var decryptedMessage: DecryptedMessage? = null

        coreCrypto.transaction {
            try {
                val result = it.decryptMessage(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), MlsMessage(message))
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
        }

        if (decryptedMessage == null) {
            return emptyList()
        }

        val mainMessageBundle = listOf(decryptedMessage!!.toBundle(messageInstant))
        val bufferedBundles = decryptedMessage!!.bufferedMessages
            ?.map { it.toBundle(messageInstant) }
            ?: emptyList()

        return mainMessageBundle + bufferedBundles
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun decryptMessages(
        groupId: MLSGroupId,
        messages: List<EncryptedMessage>
    ): DecryptedBatch {
        val results = mutableListOf<DecryptedMessageBundle>()
        var lastHandledEventId: String? = null

        coreCrypto.transaction { context ->
            messages.forEach { message ->
                try {
                    val decrypted = context.decryptMessage(
                        com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                        MlsMessage(message.content)
                    )

                    decrypted.let { dec ->
                        results += dec.toBundle(message.messageInstant)
                        results += dec.bufferedMessages?.map { buf -> buf.toBundle(message.messageInstant) }.orEmpty()

                        lastHandledEventId = message.eventId
                    }

                } catch (throwable: Throwable) {
                    if (throwable is CoreCryptoException.Mls) {
                        when (throwable.exception) {
                            is MlsException.BufferedFutureMessage,
                            is MlsException.BufferedCommit,
                            is MlsException.DuplicateMessage -> {
                                // IGNORE
                            }
                            else -> {
                                throw throwable
                            }
                        }
                    } else {
                        throw throwable
                    }
                }
            }

            lastHandledEventId?.let { id ->
                context.setData(id.encodeToByteArray())
            }
        }

        return DecryptedBatch(
            messages = results,
            groupId = groupId,
            failedMessage = null
        )
    }

    override suspend fun commitPendingProposals(groupId: MLSGroupId) {
        return coreCrypto.transaction {
            it.commitPendingProposals(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
        }
    }

    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        return coreCrypto.transaction { context ->
            context.members(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())).mapNotNull {
                CryptoQualifiedClientId.fromEncodedString(it.value)
            }
        }
    }

    override suspend fun addMember(
        groupId: MLSGroupId,
        membersKeyPackages: List<MLSKeyPackage>
    ): List<String>? {
        return if (membersKeyPackages.isNotEmpty()) {
            coreCrypto.transaction {
                it.addMember(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                    membersKeyPackages.map { keyPackage -> com.wire.crypto.MLSKeyPackage(keyPackage) }
                )
            }
        } else {
            null
        }
    }

    override suspend fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ) {
        if (members.isNotEmpty()) {
            coreCrypto.transaction { context ->
                context.removeMember(
                    com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                    members.map { com.wire.crypto.ClientId(it.toString()) }
                )
            }
        }
    }

    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        return coreCrypto.transaction {
            it.deriveAvsSecret(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), keyLength).value
        }
    }

    override suspend fun e2eiNewActivationEnrollment(
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration
    ): E2EIClient {
        return coreCrypto.transaction {
            E2EIClientImpl(
                it.e2eiNewActivationEnrollment(
                    displayName,
                    handle,
                    expiry.inWholeSeconds.toUInt(),
                    defaultCipherSuite,
                    teamId,
                )
            )
        }
    }

    override suspend fun e2eiNewRotateEnrollment(
        displayName: String?,
        handle: String?,
        teamId: String?,
        expiry: Duration
    ): E2EIClient {
        return coreCrypto.transaction {
            E2EIClientImpl(
                it.e2eiNewRotateEnrollment(
                    expiry.inWholeSeconds.toUInt(),
                    defaultCipherSuite,
                    displayName,
                    handle,
                    teamId,
                )
            )
        }
    }

    override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? = coreCrypto
        .transaction { context ->
            context.e2eiMlsInitOnly((enrollment as E2EIClientImpl).wireE2eIdentity, certificateChain, null)?.value
                ?.map { it.toString() }
        }

    override suspend fun isE2EIEnabled(): Boolean = coreCrypto.transaction {
        it.e2eiIsEnabled(defaultCipherSuite)
    }

    override suspend fun saveX509Credential(e2EIClient: E2EIClient, certificateChain: CertificateChain): List<String>? {
        return coreCrypto.transaction {
            it.saveX509Credential((e2EIClient as E2EIClientImpl).wireE2eIdentity, certificateChain)
        }
    }

    // TODO remove
    override suspend fun e2eiRotateGroups(
        groupList: List<MLSGroupId>
    ) {
        coreCrypto.transaction { cc ->
            groupList.forEach { groupId ->
                cc.e2eiRotate(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()))
            }
        }
    }

    override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState = coreCrypto.transaction {
        it.e2eiConversationState(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes())).toCryptography()
    }

    override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
        return coreCrypto.transaction { context ->
            context.getDeviceIdentities(
                com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()),
                clients.map {
                    ClientId(it.toString())
                }
            )
                .mapNotNull {
                    it.toCryptography()
                }
        }
    }

    override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> {
        val usersIds = users.map {
            it.value
        }
        return coreCrypto.transaction {
            it.getUserIdentities(com.wire.crypto.MLSGroupId(groupId.decodeBase64Bytes()), usersIds)
        }
            .mapValues {
                it.value.mapNotNull { identity -> identity.toCryptography() }
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
