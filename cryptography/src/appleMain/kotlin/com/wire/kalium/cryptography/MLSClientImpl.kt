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

import com.wire.crypto.CiphersuiteName
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CustomConfiguration
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.Invitee
import com.wire.crypto.MlsPublicGroupStateEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.MlsWirePolicy
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.String
import okio.internal.commonToUtf8String
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
class MLSClientImpl(
    private val coreCrypto: CoreCrypto
) : MLSClient {

    private val keyRotationDuration: Duration = 30.toDuration(DurationUnit.DAYS)
    private val defaultGroupConfiguration = CustomConfiguration(keyRotationDuration, MlsWirePolicy.PLAINTEXT)
    override fun getDefaultCipherSuite(): UShort {
        return defaultCipherSuite
    }

    @Suppress("EmptyFunctionBlock")
    override suspend fun close() {
    }

    override suspend fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey().toUByteArray().asByteArray()
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(amount.toUInt()).map { it.toUByteArray().asByteArray() }
    }

    override suspend fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount()
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
                epoch = epoch
            )
        )
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(coreCrypto.joinByExternalCommit(toUByteList(publicGroupState), defaultGroupConfiguration))
    }

    override suspend fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId) {
        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.mergePendingGroupFromExternalCommit(groupIdAsBytes)
    }

    override suspend fun clearPendingGroupExternalCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingGroupFromExternalCommit(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: ByteArray
    ) {
        val conf = ConversationConfiguration(
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            listOf(toUByteList(externalSenders)),
            defaultGroupConfiguration
        )

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, conf)
    }

    override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
        TODO("Not yet implemented")
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(toUByteList(groupId.decodeBase64Bytes()))
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message), defaultGroupConfiguration)
        return WelcomeBundle(groupId = toByteArray(conversationId).encodeBase64(), null)
    }

    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage =
            coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override suspend fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): List<DecryptedMessageBundle> {
        return listOf(toDecryptedMessageBundle(coreCrypto.decryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))))
    }

    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        return coreCrypto.getClientIds(toUByteList(groupId.decodeBase64Bytes())).mapNotNull {
            CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it)))
        }
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

    override suspend fun addMember(
        groupId: MLSGroupId,
        membersKeyPackages: List<MLSKeyPackage>
    ): CommitBundle? {
        if (membersKeyPackages.isEmpty()) {
            return null
        }
        // todo: fix later when the code is fixed for jvm
        val invitees = membersKeyPackages.map {
            Invitee(toUByteList("it.first.toString()"), toUByteList(it))
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

        return toCommitBundle(coreCrypto.removeClientsFromConversation(toUByteList(groupId.decodeBase64Bytes()), clientIds))
    }

    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        return toByteArray(coreCrypto.exportSecretKey(toUByteList(groupId.decodeBase64Bytes()), keyLength))
    }

    override suspend fun e2eiNewActivationEnrollment(
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration
    ): E2EIClient {
        TODO("Not yet implemented")
    }

    override suspend fun e2eiNewRotateEnrollment(
        displayName: String?,
        handle: String?,
        teamId: String?,
        expiry: Duration
    ): E2EIClient {
        TODO("Not yet implemented")
    }

    override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
        TODO("Not yet implemented")
    }

    override suspend fun isE2EIEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getMLSCredentials(): CredentialType {
        TODO("Not yet implemented")
    }

    override suspend fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ): RotateBundle {
        TODO("Not yet implemented")
    }

    override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState {
        TODO("Not supported on apple devices")
    }

    override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
        TODO("Not yet implemented")
    }

    override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> {
        TODO("Not yet implemented")
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()

        fun toCommitBundle(value: com.wire.crypto.MemberAddedMessages) = CommitBundle(
            toByteArray(value.commit),
            toByteArray(value.welcome),
            toPublicGroupStateBundle(value.publicGroupState),
            null
        )

        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            toByteArray(value.commit),
            value.welcome?.let { toByteArray(it) },
            toPublicGroupStateBundle(value.publicGroupState),
            null
        )

        fun toCommitBundle(value: com.wire.crypto.ConversationInitBundle) = CommitBundle(
            toByteArray(value.commit),
            null,
            toPublicGroupStateBundle(value.publicGroupState),
            null
        )

        fun toPublicGroupStateBundle(value: com.wire.crypto.PublicGroupStateBundle) = GroupInfoBundle(
            toEncryptionType(value.encryptionType),
            toRatchetTreeType(value.ratchetTreeType),
            toByteArray(value.payload)
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
            value.message?.let { toByteArray(it) },
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString((toByteArray(it).commonToUtf8String())) },
            value.hasEpochChanged,
            identity = null,
            crlNewDistributionPoints = null
        )
    }

}
