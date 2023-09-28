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
import com.wire.crypto.MlsPublicGroupStateEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.MlsWirePolicy
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
    private val defaultE2EIExpiry: UInt = 90U
//     private val defaultMLSCredentialType: MlsCredentialType = MlsCredentialType.BASIC

    init {
        coreCrypto = CoreCrypto(rootDir, databaseKey.value, toUByteList(clientId.toString()), null)
        coreCrypto.setCallbacks(Callbacks())
    }

    override suspend fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey(defaultCiphersuite)
    }

    override fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey().toUByteArray().asByteArray()
    }

    override fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(amount.toUInt())
            .map { it.toUByteArray().asByteArray() }
    }

    override fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount()
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

    override fun joinConversation(groupId: MLSGroupId, epoch: ULong): HandshakeMessage {
        return toByteArray(
            coreCrypto.newExternalAddProposal(
                conversationId = toUByteList(groupId.decodeBase64Bytes()),
                epoch = epoch
            )
        )
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(coreCrypto.joinByExternalCommit(
            toUByteList(publicGroupState),
            defaultGroupConfiguration
        ))
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
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            externalSenders.map { toUByteList(it.value) },
            defaultGroupConfiguration
        )

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, conf)
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

        val messageBundle = listOf(toDecryptedMessageBundle(
            decryptedMessage
        ))
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

    override fun newAcmeEnrollment(clientId: E2EIQualifiedClientId, displayName: String, handle: String): E2EIClient {
        TODO("not implemented")
    }

    override suspend fun e2eiNewActivationEnrollment(
        clientId: E2EIQualifiedClientId,
        displayName: String,
        handle: String
    ): E2EIClient {
        TODO("not implemented")
    }

    override suspend fun e2eiNewRotateEnrollment(
        clientId: E2EIQualifiedClientId,
        displayName: String?,
        handle: String?
    ): E2EIClient {
        TODO("not implemented")
    }

    override fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain) {
        TODO("not implemented")
    }

    override suspend fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ) {
        TODO("not implemented")
    }

    override fun isGroupVerified(groupId: MLSGroupId): Boolean =
        TODO("not implemented")

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()

        fun toCommitBundle(value: com.wire.crypto.MemberAddedMessages) = CommitBundle(
            toByteArray(value.commit),
            toByteArray(value.welcome),
            toGroupInfoBundle(value.publicGroupState)
        )

        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            toByteArray(value.commit),
            value.welcome?.let { toByteArray(it) },
            toGroupInfoBundle(value.publicGroupState)
        )

        fun toCommitBundle(value: com.wire.crypto.ConversationInitBundle) = CommitBundle(
            value.commit,
            null,
            toGroupInfoBundle(value.publicGroupState)
        )

        fun toGroupInfoBundle(value: com.wire.crypto.PublicGroupStateBundle) = GroupInfoBundle(
            toEncryptionType(value.encryptionType),
            toRatchetTreeType(value.ratchetTreeType),
            value.payload
        )

        fun toEncryptionType(value: MlsPublicGroupStateEncryptionType) = when (value) {
            MlsPublicGroupStateEncryptionType.PLAINTEXT -> GroupInfoEncryptionType.PLAINTEXT
            MlsPublicGroupStateEncryptionType.JWE_ENCRYPTED -> GroupInfoEncryptionType.JWE_ENCRYPTED
        }

        fun toRatchetTreeType(value: MlsRatchetTreeType) = when (value) {
            MlsRatchetTreeType.FULL -> RatchetTreeType.FULL
            MlsRatchetTreeType.DELTA -> RatchetTreeType.DELTA
            MlsRatchetTreeType.BY_REF -> RatchetTreeType.BY_REF
        }

        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let {
                E2EIdentity(it.clientId, it.handle, it.displayName, it.domain)
            }
        )

        fun toDecryptedMessageBundle(value: BufferedDecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let {
                E2EIdentity(it.clientId, it.handle, it.displayName, it.domain)
            }
        )
    }

}
