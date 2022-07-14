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
        coreCrypto = CoreCrypto(rootDir, databaseKey.value, clientId.toString())
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

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        return coreCrypto.conversationExists(toUByteList(groupId.decodeBase64Bytes()))
    }

    override fun createConversation(
        conversationId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>? {
        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        val conf = ConversationConfiguration(
            invitees,
            emptyList(),
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            keyRotationDuration
        )

        val messages = coreCrypto.createConversation(toUByteList(conversationId.decodeBase64Bytes()), conf)
        return messages?.let { Pair(toByteArray(it.message), toByteArray(it.welcome)) }
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        // TODO currently generating a dummy ConversationConfiguration, this should be removed when API is updated.
        val conf = ConversationConfiguration(
            emptyList(),
            emptyList(),
            CiphersuiteName.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519,
            keyRotationDuration
        )

        val conversationId = coreCrypto.processWelcomeMessage(toUByteList(message), conf)
        return toByteArray(conversationId).encodeBase64()
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        val applicationMessage = coreCrypto.encryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))
        return toByteArray(applicationMessage)
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): PlainMessage? {
        return coreCrypto.decryptMessage(toUByteList(groupId.decodeBase64Bytes()), toUByteList(message))?.let { toByteArray(it) }
    }

    override fun addMember(
        conversationId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>? {
        val invitees = members.map {
            Invitee(toUByteList(it.first.toString()), toUByteList(it.second))
        }

        val messages = coreCrypto.addClientsToConversation(toUByteList(conversationId.decodeBase64Bytes()), invitees)
        return messages?.let { Pair(toByteArray(it.message), toByteArray(it.welcome)) }
    }

    override fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): HandshakeMessage? {

        // TODO currently generating a dummy key package, this should be removed when API is updated.
        val invitees = members.map {
            Invitee(toUByteList(it.toString()), toUByteList(generateKeyPackages(1).first()))
        }

        val handshake = coreCrypto.removeClientsFromConversation(toUByteList(groupId.decodeBase64Bytes()), invitees)
        return handshake?.let { toByteArray(it) }
    }

    companion object {
        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()
    }

}
