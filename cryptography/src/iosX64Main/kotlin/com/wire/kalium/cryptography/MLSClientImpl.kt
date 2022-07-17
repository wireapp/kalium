package com.wire.kalium.cryptography

actual class MLSClientImpl actual constructor(
    rootDir: String,
    databaseKey: MlsDBSecret,
    clientId: CryptoQualifiedClientId
) : MLSClient {
    override fun clearLocalFiles(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPublicKey(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun generateKeyPackages(amount: Int): List<ByteArray> {
        TODO("Not yet implemented")
    }

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        TODO("Not yet implemented")
    }

    override fun createConversation(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>? {
        TODO("Not yet implemented")
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        TODO("Not yet implemented")
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        TODO("Not yet implemented")
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): PlainMessage? {
        TODO("Not yet implemented")
    }

    override fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>? {
        TODO("Not yet implemented")
    }

    override fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>): HandshakeMessage? {
        TODO("Not yet implemented")
    }
}
