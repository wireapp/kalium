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

import com.wire.crypto.*
import com.wire.crypto.client.CoreCryptoCentral.Companion.lower
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

private class Callbacks : CoreCryptoCallbacks {

    override fun authorize(conversationId: List<UByte>, clientId: List<UByte>): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun clientIsExistingGroupUser(
        conversationId: ConversationId,
        clientId: ClientId,
        existingClients: List<ClientId>,
        parentConversationClients: List<ClientId>?
    ): Boolean {
        // TODO disabled until we have subconversation support in CC
//         val userId = toClientID(clientId)?.userId ?: return false
//         return existingClients.find {
//             toClientID(it)?.userId == userId
//         } != null
        return true
    }

    override fun userAuthorize(
        conversationId: ConversationId,
        externalClientId: ClientId,
        existingClients: List<ClientId>
    ): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    companion object {
        fun toClientID(rawClientId: List<UByte>): CryptoQualifiedClientId? =
            CryptoQualifiedClientId.fromEncodedString(String(MLSClientImpl.toByteArray(rawClientId)))
    }

}

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
actual class MLSClientImpl actual constructor(
    private val rootDir: String,
    databaseKey: MlsDBSecret,
    clientId: CryptoQualifiedClientId
) : MLSClient {

    private val coreCrypto: CoreCrypto
    private val keyRotationDuration: Duration = 30.toDuration(DurationUnit.DAYS)
    private val defaultGroupConfiguration =
        CustomConfiguration(keyRotationDuration.toJavaDuration(), MlsWirePolicy.PLAINTEXT)
    private val defaultCiphersuiteName = CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519.lower()
    private val defaultCredentialType = MlsCredentialType.BASIC
    private val defaultE2EIExpiry: UInt = 90U

    init {
        coreCrypto = CoreCrypto.deferredInit(
            rootDir,
            databaseKey.value,
            listOf(defaultCiphersuiteName)
        )
        coreCrypto.setCallbacks(Callbacks())
    }

    override fun clearLocalFiles(): Boolean {
        return File(rootDir).deleteRecursively()
    }

    override fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey(defaultCiphersuiteName).toUByteArray().asByteArray()
    }

    override fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(defaultCiphersuiteName, amount.toUInt())
            .map { it.toUByteArray().asByteArray() }
    }

    override fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount(defaultCiphersuiteName)
    }

    override fun updateKeyingMaterial(groupId: MLSGroupId): CommitBundle {
        return toCommitBundle(coreCrypto.updateKeyingMaterial(toUByteList(groupId.decodeBase64Bytes())))
    }

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.conversationExists(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun conversationEpoch(groupId: MLSGroupId): ULong {
        return coreCrypto.conversationEpoch(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun joinConversation(groupId: MLSGroupId, epoch: ULong): HandshakeMessage {
        return toByteArray(
            coreCrypto.newExternalAddProposal(
                conversationId = toUByteList(groupId.decodeBase64Bytes()),
                epoch = epoch,
                ciphersuite = defaultCiphersuiteName,
                credentialType = defaultCredentialType
            )
        )
    }

    override fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        return toCommitBundle(
            coreCrypto.joinByExternalCommit(
                toUByteList(publicGroupState),
                defaultGroupConfiguration,
                defaultCredentialType
            )
        )
    }

    override fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId) {
        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.mergePendingGroupFromExternalCommit(groupIdAsBytes)
    }

    override fun clearPendingGroupExternalCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingGroupFromExternalCommit(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun createConversation(
        groupId: MLSGroupId,
        externalSenders: List<Ed22519Key>
    ) {
        val conf = ConversationConfiguration(
            defaultCiphersuiteName,
            externalSenders.map { toUByteList(it.value) },
            defaultGroupConfiguration
        )

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, defaultCredentialType, conf)
    }

    override fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message), defaultGroupConfiguration)
        return toByteArray(conversationId).encodeBase64()
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage =
            coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): DecryptedMessageBundle {
        return toDecryptedMessageBundle(
            coreCrypto.decryptMessage(
                toUByteList(groupId.decodeBase64Bytes()),
                toUByteList(message)
            )
        )
    }

    override fun commitAccepted(groupId: MLSGroupId) {
        coreCrypto.commitAccepted(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun commitPendingProposals(groupId: MLSGroupId): CommitBundle? {
        return coreCrypto.commitPendingProposals(toUByteList(groupId.decodeBase64Bytes()))?.let { toCommitBundle(it) }
    }

    override fun clearPendingCommit(groupId: MLSGroupId) {
        coreCrypto.clearPendingCommit(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        return coreCrypto.getClientIds(toUByteList(groupId.decodeBase64Bytes())).mapNotNull {
            CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it)))
        }
    }

    override fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): CommitBundle? {
        if (members.isEmpty()) {
            return null
        }

        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        return toCommitBundle(coreCrypto.addClientsToConversation(toUByteList(groupId.decodeBase64Bytes()), invitees))
    }

    override fun removeMember(
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

    override fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        return toByteArray(coreCrypto.exportSecretKey(toUByteList(groupId.decodeBase64Bytes()), keyLength))
    }

    override fun newAcmeEnrollment(clientId: E2EIQualifiedClientId, displayName: String, handle: String): E2EIClient {
        return E2EIClientImpl(
            coreCrypto.e2eiNewEnrollment(
                clientId.toString(),
                displayName,
                handle,
                defaultE2EIExpiry,
                defaultCiphersuiteName
            )
        )
    }

    override fun initMLSWithE2EI(e2eiClient: E2EIClient, certificateChain: String) {
        coreCrypto.e2eiMlsInit((e2eiClient as E2EIClientImpl).wireE2eIdentity, certificateChain)
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()

        fun toCommitBundle(value: com.wire.crypto.MemberAddedMessages) = CommitBundle(
            toByteArray(value.commit),
            toByteArray(value.welcome),
            toPublicGroupStateBundle(value.publicGroupState)
        )

        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            toByteArray(value.commit),
            value.welcome?.let { toByteArray(it) },
            toPublicGroupStateBundle(value.publicGroupState)
        )

        fun toCommitBundle(value: com.wire.crypto.ConversationInitBundle) = CommitBundle(
            toByteArray(value.commit),
            null,
            toPublicGroupStateBundle(value.publicGroupState)
        )

        fun toPublicGroupStateBundle(value: com.wire.crypto.PublicGroupStateBundle) = PublicGroupStateBundle(
            toEncryptionType(value.encryptionType),
            toRatchetTreeType(value.ratchetTreeType),
            toByteArray(value.payload)
        )

        fun toEncryptionType(value: MlsPublicGroupStateEncryptionType) = when (value) {
            MlsPublicGroupStateEncryptionType.PLAINTEXT -> PublicGroupStateEncryptionType.PLAINTEXT
            MlsPublicGroupStateEncryptionType.JWE_ENCRYPTED -> PublicGroupStateEncryptionType.JWE_ENCRYPTED
        }

        fun toRatchetTreeType(value: MlsRatchetTreeType) = when (value) {
            MlsRatchetTreeType.FULL -> RatchetTreeType.FULL
            MlsRatchetTreeType.DELTA -> RatchetTreeType.DELTA
            MlsRatchetTreeType.BY_REF -> RatchetTreeType.BY_REF
        }

        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message?.let { toByteArray(it) },
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it))) },
            value.hasEpochChanged,
            value.identity?.let {
                E2EIdentity(it.clientId, it.handle, it.displayName, it.domain)
            }
        )
    }

}
