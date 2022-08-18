package com.wire.kalium.cryptography

import com.wire.crypto.CiphersuiteName
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.CoreCrypto
import com.wire.crypto.Invitee
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.io.File
import java.time.Duration

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

    override fun updateKeyingMaterial(groupId: MLSGroupId): Pair<HandshakeMessage, WelcomeMessage?> {
        return coreCrypto.updateKeyingMaterial(toUByteList(groupId.decodeBase64Bytes())).let { commitBundle ->
            Pair(toByteArray(commitBundle.commit), commitBundle.welcome?.let { toByteArray(it) })
        }
    }

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.conversationExists(toUByteList(groupId.decodeBase64Bytes()))
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
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>? {
        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        val conf = ConversationConfiguration(
            emptyList(),
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            keyRotationDuration,
            emptyList()
        )

        val groupIdAsBytes = toUByteList(groupId.decodeBase64Bytes())
        coreCrypto.createConversation(groupIdAsBytes, conf)

        return if (members.isEmpty()) {
            null
        } else {
            val messages = coreCrypto.addClientsToConversation(groupIdAsBytes, invitees)
            messages.let { Pair(toByteArray(it.commit), toByteArray(it.welcome)) }
        }
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message))
        return toByteArray(conversationId).encodeBase64()
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage = coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): PlainMessage? {
        return coreCrypto.decryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message)).message?.let { toByteArray(it) }
    }

    override fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage> {
        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        val messages = coreCrypto.addClientsToConversation(toUByteList(groupId.decodeBase64Bytes()), invitees)
        return messages.let { Pair(toByteArray(it.commit), toByteArray(it.welcome)) }
    }

    override fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): HandshakeMessage {
        val clientIds = members.map {
            toUByteList(it.toString())
        }

        val handshake = coreCrypto.removeClientsFromConversation(toUByteList(groupId.decodeBase64Bytes()), clientIds)
        return toByteArray(handshake.commit)
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()
    }

}
