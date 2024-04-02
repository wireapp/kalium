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

import com.wire.crypto.BufferedDecryptedMessage
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CustomConfiguration
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.E2eiConversationState
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
    override suspend fun close() {
        coreCrypto.close()
    }

    override suspend fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey(defaultCiphersuite, toCredentialType(getMLSCredentials()))
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(defaultCiphersuite, toCredentialType(getMLSCredentials()), amount.toUInt())
    }

    override suspend fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount(defaultCiphersuite, toCredentialType(getMLSCredentials()))
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
            credentialType = toCredentialType(getMLSCredentials())
        )
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(
            coreCrypto.joinByExternalCommit(
                publicGroupState,
                defaultGroupConfiguration,
                toCredentialType(getMLSCredentials())
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
            defaultGroupConfiguration
        )

        coreCrypto.createConversation(groupId.decodeBase64Bytes(), toCredentialType(getMLSCredentials()), conf)
    }

    override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
        return toExternalSenderKey(coreCrypto.getExternalSender(groupId.decodeBase64Bytes()))
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(groupId.decodeBase64Bytes())
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage) =
        toWelcomeBundle(coreCrypto.processWelcomeMessage(message, defaultGroupConfiguration))

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
        membersKeyPackages: List<MLSKeyPackage>
    ): CommitBundle? {
        if (membersKeyPackages.isEmpty()) {
            return null
        }

        return toCommitBundle(coreCrypto.addClientsToConversation(groupId.decodeBase64Bytes(), membersKeyPackages))
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

    override suspend fun e2eiNewActivationEnrollment(
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration
    ): E2EIClient {
        return E2EIClientImpl(
            coreCrypto.e2eiNewActivationEnrollment(
                displayName,
                handle,
                teamId,
                expiry.inWholeSeconds.toUInt(),
                defaultCiphersuite
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
            coreCrypto.e2eiNewRotateEnrollment(
                displayName,
                handle,
                teamId,
                expiry.inWholeSeconds.toUInt(),
                defaultCiphersuite
            )
        )
    }

    override suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
        return coreCrypto.e2eiMlsInitOnly((enrollment as E2EIClientImpl).wireE2eIdentity, certificateChain, null)
    }

    override suspend fun isE2EIEnabled(): Boolean {
        return coreCrypto.e2eiIsEnabled(defaultCiphersuite)
    }

    override suspend fun getMLSCredentials(): CredentialType {
        return if (isE2EIEnabled()) return CredentialType.X509 else CredentialType.DEFAULT
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

    override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
        val clientIds = clients.map {
            it.toString().encodeToByteArray()
        }
        return coreCrypto.getDeviceIdentities(groupId.decodeBase64Bytes(), clientIds).mapNotNull {
            toIdentity(it)
        }
    }

    override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> {
        val usersIds = users.map {
            it.value
        }
        return coreCrypto.getUserIdentities(groupId.decodeBase64Bytes(), usersIds).mapValues {
            it.value.mapNotNull { identity -> toIdentity(identity) }
        }
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()

        fun toWelcomeBundle(value: com.wire.crypto.WelcomeBundle) = WelcomeBundle(
            groupId = value.id.encodeBase64(),
            crlNewDistributionPoints = value.crlNewDistributionPoints
        )

        fun toExternalSenderKey(value: ByteArray) = ExternalSenderKey(value)

        fun toCommitBundle(value: com.wire.crypto.MemberAddedMessages) = CommitBundle(
            value.commit,
            value.welcome,
            toGroupInfoBundle(value.groupInfo),
            value.crlNewDistributionPoints
        )

        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            value.commit,
            value.welcome,
            toGroupInfoBundle(value.groupInfo),
            null
        )

        fun toCommitBundle(value: com.wire.crypto.ConversationInitBundle) = CommitBundle(
            value.commit,
            null,
            toGroupInfoBundle(value.groupInfo),
            value.crlNewDistributionPoints
        )

        fun toRotateBundle(value: com.wire.crypto.RotateBundle) = RotateBundle(
            value.commits.map { (groupId, commitBundle) ->
                toGroupId(groupId) to toCommitBundle(commitBundle)
            }.toMap(),
            value.newKeyPackages,
            value.keyPackageRefsToRemove,
            value.crlNewDistributionPoints
        )

        fun toIdentity(value: com.wire.crypto.WireIdentity): WireIdentity? {
            val clientId = CryptoQualifiedClientId.fromEncodedString(value.clientId)
            return clientId?.let {
                WireIdentity(
                    CryptoQualifiedClientId.fromEncodedString(value.clientId)!!,
                    value.x509Identity?.handle ?: "",
                    value.x509Identity?.displayName ?: "",
                    value.x509Identity?.domain ?: "",
                    value.x509Identity?.certificate ?: "",
                    toDeviceStatus(value.status),
                    value.thumbprint,
                    value.x509Identity?.serialNumber ?: "",
                    value.x509Identity?.notAfter?.toLong() ?: 0L
                )
            }
        }

        fun toDeviceStatus(value: com.wire.crypto.DeviceStatus) = when (value) {
            com.wire.crypto.DeviceStatus.VALID -> CryptoCertificateStatus.VALID
            com.wire.crypto.DeviceStatus.EXPIRED -> CryptoCertificateStatus.EXPIRED
            com.wire.crypto.DeviceStatus.REVOKED -> CryptoCertificateStatus.REVOKED
        }

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
            E2eiConversationState.NOT_VERIFIED -> E2EIConversationState.NOT_VERIFIED
            E2eiConversationState.NOT_ENABLED -> E2EIConversationState.NOT_ENABLED
        }

        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let { toIdentity(it) },
            value.crlNewDistributionPoints
        )

        fun toDecryptedMessageBundle(value: BufferedDecryptedMessage) = DecryptedMessageBundle(
            value.message,
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(it)) },
            value.hasEpochChanged,
            value.identity?.let { toIdentity(it) },
            value.crlNewDistributionPoints
        )

        fun toCredentialType(value: CredentialType) = when (value) {
            CredentialType.Basic -> MlsCredentialType.BASIC
            CredentialType.X509 -> MlsCredentialType.X509
        }

        fun toCrlRegistration(value: com.wire.crypto.CrlRegistration) = CrlRegistration(
            value.dirty,
            value.expiration
        )
    }
}
