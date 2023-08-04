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

import com.wire.crypto.CiphersuiteName
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CustomConfiguration
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.Invitee
import com.wire.crypto.MlsCredentialType
import com.wire.crypto.MlsGroupInfoEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.MlsWirePolicy
import com.wire.crypto.client.CoreCryptoCentral.Companion.lower
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
    private val defaultCiphersuite = CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519.lower()
    private val defaultE2EIExpiry: UInt = 90U
    private val defaultMLSCredentialType: MlsCredentialType = MlsCredentialType.BASIC
    override suspend fun close() {
        coreCrypto.close()
    }

    override suspend fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey(defaultCiphersuite).toUByteArray().asByteArray()
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(defaultCiphersuite, defaultMLSCredentialType, amount.toUInt())
            .map { it.toUByteArray().asByteArray() }
    }

    override suspend fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount(defaultCiphersuite, defaultMLSCredentialType)
    }

    override suspend fun updateKeyingMaterial(groupId: MLSGroupId): CommitBundle {
        return toCommitBundle(coreCrypto.updateKeyingMaterial(toUByteList(groupId.decodeBase64Bytes())))
    }

    override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.conversationExists(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
        return coreCrypto.conversationEpoch(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun joinConversation(groupId: MLSGroupId, epoch: ULong): HandshakeMessage {
        return toByteArray(
            coreCrypto.newExternalAddProposal(
                conversationId = toUByteList(groupId.decodeBase64Bytes()),
                epoch = epoch,
                ciphersuite = defaultCiphersuite,
                credentialType = MlsCredentialType.BASIC
            )
        )
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(coreCrypto.joinByExternalCommit(
            toUByteList(publicGroupState),
            defaultGroupConfiguration,
            MlsCredentialType.BASIC)
        )
    }

    override suspend fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId): List<DecryptedMessageBundle>? {
        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        return coreCrypto.mergePendingGroupFromExternalCommit(groupIdAsBytes)?.let { messages ->
            messages.map { toDecryptedMessageBundle(it) }
        }
    }

    override suspend fun clearPendingGroupExternalCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingGroupFromExternalCommit(toUByteList(groupId.decodeBase64Bytes()))
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

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, MlsCredentialType.BASIC, conf)
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message), defaultGroupConfiguration)
        return toByteArray(conversationId).encodeBase64()
    }

    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage =
            coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override suspend fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): DecryptedMessageBundle {
        return toDecryptedMessageBundle(
            coreCrypto.decryptMessage(
                toUByteList(groupId.decodeBase64Bytes()),
                toUByteList(message)
            )
        )
    }

    override suspend fun commitAccepted(groupId: MLSGroupId) {
        coreCrypto.commitAccepted(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun commitPendingProposals(groupId: MLSGroupId): CommitBundle? {
        return coreCrypto.commitPendingProposals(toUByteList(groupId.decodeBase64Bytes()))?.let { toCommitBundle(it) }
    }

    override suspend fun clearPendingCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingCommit(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        return coreCrypto.getClientIds(toUByteList(groupId.decodeBase64Bytes())).mapNotNull {
            CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it)))
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
            Invitee(toUByteList(it.first.toString()), it.second)
        }

        return toCommitBundle(coreCrypto.addClientsToConversation(toUByteList(groupId.decodeBase64Bytes()), invitees))
    }

    override suspend fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): CommitBundle {
        val clientIds = members.map {
            toUByteList(it.toString())
        }

        return toCommitBundle(
            coreCrypto.removeClientsFromConversation(
                toUByteList(groupId.decodeBase64Bytes()),
                clientIds
            )
        )
    }

    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        return toByteArray(coreCrypto.exportSecretKey(toUByteList(groupId.decodeBase64Bytes()), keyLength))
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
        coreCrypto.e2eiMlsInitOnly((enrollment as E2EIClientImpl).wireE2eIdentity, certificateChain)
    }

    override suspend fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ) {
        coreCrypto.e2eiRotateAll(
            (enrollment as E2EIClientImpl).wireE2eIdentity,
            certificateChain,
            newMLSKeyPackageCount
        )
    }

    override suspend fun isGroupVerified(groupId: MLSGroupId): Boolean =
        !coreCrypto.e2eiIsDegraded(toUByteList(groupId.decodeBase64Bytes()))

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

        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it))) },
            value.hasEpochChanged,
            value.identity?.let {
                E2EIdentity(it.clientId, it.handle, it.displayName, it.domain)
            }
        )
    }

}
