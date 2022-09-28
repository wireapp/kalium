package com.wire.kalium.cryptography

import com.wire.crypto.CiphersuiteName
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCallbacks
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.Invitee
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.io.File
import java.time.Duration

private class Callbacks : CoreCryptoCallbacks {

    override fun authorize(conversationId: List<UByte>, clientId: List<UByte>): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun clientIdBelongsToOneOf(clientId: List<UByte>, otherClients: List<List<UByte>>): Boolean {
        val userId = toClientID(clientId)?.userId ?: return false
        return otherClients.find {
            toClientID(it)?.userId == userId
        } != null
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
    private val keyRotationDuration: Duration = Duration.ofDays(30)

    init {
        coreCrypto = CoreCrypto(rootDir, databaseKey.value, clientId.toString(), null)
        coreCrypto.setCallbacks(Callbacks())
    }

    override fun clearLocalFiles(): Boolean {
        coreCrypto.close()
        return File(rootDir).deleteRecursively()
    }

    override fun getPublicKey(): ByteArray {
        return coreCrypto.clientPublicKey().toUByteArray().asByteArray()
    }

    override fun generateKeyPackages(amount: Int): List<ByteArray> {
        return coreCrypto.clientKeypackages(amount.toUInt()).map { it.toUByteArray().asByteArray() }
    }

    override fun validKeyPackageCount(): ULong {
        return coreCrypto.clientValidKeypackagesCount()
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
                epoch = epoch
            )
        )
    }

    override fun createConversation(
        groupId: MLSGroupId,
    ) {
        val conf = ConversationConfiguration(
            emptyList(),
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            keyRotationDuration,
            emptyList()
        )

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, conf)
    }

    override fun wipeConversation(groupId: MLSGroupId) {
        coreCrypto.wipeConversation(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message))
        return toByteArray(conversationId).encodeBase64()
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage = coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): DecryptedMessageBundle {
        return toDecryptedMessageBundle(coreCrypto.decryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message)))
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

    override fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): AddMemberCommitBundle? {
        if (members.isEmpty()) {
            return null
        }

        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        return toAddMemberCommitBundle(coreCrypto.addClientsToConversation(toUByteList(groupId.decodeBase64Bytes()), invitees))
    }

    override fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): CommitBundle {
        val clientIds = members.map {
            toUByteList(it.toString())
        }

        return toCommitBundle(coreCrypto.removeClientsFromConversation(toUByteList(groupId.decodeBase64Bytes()), clientIds))
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()
        fun toCommitBundle(value: com.wire.crypto.CommitBundle) = CommitBundle(
            toByteArray(value.commit),
            value.welcome?.let { toByteArray(it) },
            toByteArray(value.publicGroupState)
        )
        fun toAddMemberCommitBundle(value: com.wire.crypto.MemberAddedMessages) = AddMemberCommitBundle(
            toByteArray(value.commit),
            toByteArray(value.welcome),
            toByteArray(value.publicGroupState)
        )
        fun toDecryptedMessageBundle(value: DecryptedMessage) = DecryptedMessageBundle(
            value.message?.let { toByteArray(it) },
            value.commitDelay?.toLong(),
            value.senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(String(toByteArray(it))) }
        )
    }

}
