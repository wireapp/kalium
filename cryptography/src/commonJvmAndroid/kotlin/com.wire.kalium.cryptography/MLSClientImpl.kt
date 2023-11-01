/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.crypto.BufferedDecryptedMessage
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CustomConfiguration
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.E2eiConversationState
import com.wire.crypto.Invitee
import com.wire.crypto.MlsCredentialType
import com.wire.crypto.MlsGroupInfoEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.MlsWirePolicy
import com.wire.crypto.client.Ciphersuites
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

typealias ConversationId = ByteArray

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
class MLSClientImpl(
    private val coreCrypto: CoreCrypto
) : MLSClient {
    private val keyRotationDuration: Duration = 30.toDuration(DurationUnit.DAYS)
    private val defaultGroupConfiguration = CustomConfiguration(keyRotationDuration.toJavaDuration(), MlsWirePolicy.PLAINTEXT)
    private val defaultCiphersuite = Ciphersuites.DEFAULT.lower().first()
    private val defaultE2EIExpiry: UInt = 90U
    private val defaultMLSCredentialType: MlsCredentialType = MlsCredentialType.BASIC
    override suspend fun close() {
        coreCrypto.close()
    }

    override suspend fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey(defaultCiphersuite)
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(defaultCiphersuite, defaultMLSCredentialType, amount.toUInt())
    }

    override suspend fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount(defaultCiphersuite, defaultMLSCredentialType)
    }

    override suspend fun updateKeyingMaterial(groupId: MLSGroupId): CommitBundle {
        return toCommitBundle(coreCrypto.updateKeyingMaterial(groupId.decodeBase64Bytes()))
    }

    override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.conversationExists(groupId.decodeBase64Bytes())
    }

    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
        return coreCrypto.conversationEpoch(groupId.decodeBase64Bytes())
    }

    override suspend fun joinConversation(groupId: MLSGroupId, epoch: ULong): HandshakeMessage {
        return coreCrypto.newExternalAddProposal(
            conversationId = groupId.decodeBase64Bytes(),
            epoch = epoch,
            ciphersuite = defaultCiphersuite,
            credentialType = MlsCredentialType.BASIC
        )
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(
            coreCrypto.joinByExternalCommit(
                publicGroupState,
                defaultGroupConfiguration,
                MlsCredentialType.BASIC
            )
        )
    }

    override suspend fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId) {
        coreCrypto.mergePendingGroupFromExternalCommit(groupId.decodeBase64Bytes())
    }

    override suspend fun clearPendingGroupExternalCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingGroupFromExternalCommit(groupId.decodeBase64Bytes())
    }

    override suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: List<Ed22519Key>
    ) {
        val conf = ConversationConfiguration(
            defaultCiphersuite,
            externalSenders.map { it.value },
            defaultGroupConfiguration,
            emptyList()
        )

        coreCrypto.createConversation(groupId.decodeBase64Bytes(), MlsCredentialType.BASIC, conf)
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(groupId.decodeBase64Bytes())
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        val conversationId = coreCrypto.processWelcomeMessage(message, defaultGroupConfiguration)
        return conversationId.encodeBase64()
    }

    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage =
            coreCrypto.encryptMessage(groupId.decodeBase64Bytes(), message)
        return applicationMessage
    }

    override suspend fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): List<DecryptedMessageBundle> {
        val decryptedMessage = coreCrypto.decryptMessage(
            groupId.decodeBase64Bytes(),
            message
        )

        val messageBundle = listOf(
            toDecryptedMessageBundle(
                decryptedMessage
            )
        )
        val bufferedMessages = decryptedMessage.bufferedMessages?.map {
            toDecryptedMessageBundle(it)
        } ?: emptyList()

        return messageBundle + bufferedMessages
    }

    override suspend fun commitAccepted(groupId: MLSGroupId) {
        coreCrypto.commitAccepted(groupId.decodeBase64Bytes())
    }

    override suspend fun commitPendingProposals(groupId: MLSGroupId): CommitBundle? {
        return coreCrypto.commitPendingProposals(groupId.decodeBase64Bytes())?.let { toCommitBundle(it) }
    }

    override suspend fun clearPendingCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingCommit(groupId.decodeBase64Bytes())
    }

    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        return coreCrypto.getClientIds(groupId.decodeBase64Bytes()).mapNotNull {
            CryptoQualifiedClientId.fromEncodedString(String(it))
        }
    }

    override suspend fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): CommitBundle? {
        if (members.isEmpty()) {
            return null
        }

        val invitees = members.map {
            Invitee(it.first.toString().encodeToByteArray(), it.second)
        }

        return toCommitBundle(coreCrypto.addClientsToConversation(groupId.decodeBase64Bytes(), invitees))
    }

    override suspend fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): CommitBundle {
        val clientIds = members.map {
            it.toString().encodeToByteArray()
        }

        return toCommitBundle(
            coreCrypto.removeClientsFromConversation(
                groupId.decodeBase64Bytes(),
                clientIds
            )
        )
    }

    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        return coreCrypto.exportSecretKey(groupId.decodeBase64Bytes(), keyLength)
    }

    override suspend fun newAcmeEnrollment(clientId: E2EIQualifiedClientId, displayName: String, handle: String): E2EIClient {
        return E2EIClientImpl(
            coreCrypto.e2eiNewEnrollment(
                clientId.toString(),
                displayName,
                handle,
                defaultE2EIExpiry,
                defaultCiphersuite
            )
        )
    }

    override suspend fun e2eiNewActivationEnrollment(
        clientId: E2EIQualifiedClientId,
        displayName: String,
        handle: String
    ): E2EIClient {
        return E2EIClientImpl(
            coreCrypto.e2eiNewActivationEnrollment(
                clientId.toString(),
                displayName,
                handle,
                defaultE2EIExpiry,
                defaultCiphersuite
            )
        )
    }

    override suspend fun e2eiNewRotateEnrollment(
        clientId: E2EIQualifiedClientId,
        displayName: String?,
        handle: String?
    ): E2EIClient {
        return E2EIClientImpl(
            coreCrypto.e2eiNewRotateEnrollment(
                clientId.toString(),
                displayName,
                handle,
                defaultE2EIExpiry,
                defaultCiphersuite
            )
        )
    }

    override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain) {
        coreCrypto.e2eiMlsInitOnly((enrollment as E2EIClientImpl).wireE2eIdentity, certificateChain, null)
    }

    override suspend fun isE2EIEnabled(): Boolean {
        return coreCrypto.e2eiIsEnabled(defaultCiphersuite)
    }

    override suspend fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ): RotateBundle {
        return toRotateBundle(
            coreCrypto.e2eiRotateAll(
                (enrollment as E2EIClientImpl).wireE2eIdentity,
                certificateChain,
                newMLSKeyPackageCount
            )
        )
    }

    override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState =
        toE2EIConversationState(coreCrypto.e2eiConversationState(groupId.decodeBase64Bytes()))

    override suspend fun getUserIdentities(groupId: MLSGroupId, clients: List<E2EIQualifiedClientId>): List<WireIdentity> {
        val clientIds = clients.map {
            it.toString().encodeToByteArray()
        }
        return coreCrypto.getUserIdentities(groupId.decodeBase64Bytes(), clientIds).map {
            toIdentity(it)
        }
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()

        fun toCommitBundle(value: com.wire.crypto.MemberAddedMessages) = CommitBundle(
            value.commit,
            value.welcome,
            toGroupInfoBundle(value.groupInfo)
        )

        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            value.commit,
            value.welcome,
            toGroupInfoBundle(value.groupInfo)
        )

        fun toCommitBundle(value: com.wire.crypto.ConversationInitBundle) = CommitBundle(
            value.commit,
            null,
            toGroupInfoBundle(value.groupInfo)
        )

        fun toRotateBundle(value: com.wire.crypto.RotateBundle) = RotateBundle(
            value.commits.map { (groupId, commitBundle) ->
                toGroupId(groupId) to toCommitBundle(commitBundle)
            }.toMap(),
            value.newKeyPackages,
            value.keyPackageRefsToRemove
        )

        fun toIdentity(value: com.wire.crypto.WireIdentity) = WireIdentity(
            value.clientId,
            value.handle,
            value.displayName,
            value.domain,
            value.certificate
        )

        // TODO: remove later, when CoreCrypto return the groupId instead of Hex value
        @Suppress("MagicNumber")
        fun toGroupId(hexValue: String): MLSGroupId {
            val byteArrayValue = hexValue.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return toByteArray(toUByteList(byteArrayValue)).encodeBase64()
        }

        fun toGroupInfoBundle(value: com.wire.crypto.GroupInfoBundle) = GroupInfoBundle(
            toEncryptionType(value.encryptionType),
            toRatchetTreeType(value.ratchetTreeType),
            value.payload
        )

        fun toEncryptionType(value: MlsGroupInfoEncryptionType) = when (value) {
            MlsGroupInfoEncryptionType.PLAINTEXT -> GroupInfoEncryptionType.PLAINTEXT
            MlsGroupInfoEncryptionType.JWE_ENCRYPTED -> GroupInfoEncryptionType.JWE_ENCRYPTED
        }

        fun toRatchetTreeType(value: MlsRatchetTreeType) = when (value) {
            MlsRatchetTreeType.FULL -> RatchetTreeType.FULL
            MlsRatchetTreeType.DELTA -> RatchetTreeType.DELTA
            MlsRatchetTreeType.BY_REF -> RatchetTreeType.BY_REF
        }

        fun toE2EIConversationState(value: com.wire.crypto.E2eiConversationState) = when (value) {
            E2eiConversationState.VERIFIED -> E2EIConversationState.VERIFIED
            // TODO: this value is wrong on CoreCrypto, it will be renamed to NOT_VERIFIED
            E2eiConversationState.DEGRADED -> E2EIConversationState.NOT_VERIFIED
            E2eiConversationState.NOT_ENABLED -> E2EIConversationState.NOT_ENABLED
        }

        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let {
                WireIdentity(it.clientId, it.handle, it.displayName, it.domain, it.certificate)
            }
        )

        fun toDecryptedMessageBundle(value: BufferedDecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let {
                WireIdentity(it.clientId, it.handle, it.displayName, it.domain, it.certificate)
            }
        )
    }
}
