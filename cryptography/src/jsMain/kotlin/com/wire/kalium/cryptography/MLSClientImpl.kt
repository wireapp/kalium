package com.wire.kalium.cryptography

@Suppress("TooManyFunctions")
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

    override fun validKeyPackageCount(): ULong {
        TODO("Not yet implemented")
    }

    override fun updateKeyingMaterial(groupId: MLSGroupId): CommitBundle {
        TODO("Not yet implemented")
    }

    override fun joinConversation(groupId: MLSGroupId, epoch: ULong): HandshakeMessage {
        TODO("Not yet implemented")
    }

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        TODO("Not yet implemented")
    }

    override fun conversationEpoch(groupId: MLSGroupId): ULong {
        TODO("Not yet implemented")
    }

    override fun createConversation(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun wipeConversation(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId {
        TODO("Not yet implemented")
    }

    override fun commitAccepted(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun commitPendingProposals(groupId: MLSGroupId): CommitBundle {
        TODO("Not yet implemented")
    }

    override fun clearPendingCommit(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        TODO("Not yet implemented")
    }

    override fun decryptMessage(groupId: MLSGroupId, message: ApplicationMessage): DecryptedMessageBundle {
        TODO("Not yet implemented")
    }

    override fun addMember(groupId: MLSGroupId, members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>): AddMemberCommitBundle? {
        TODO("Not yet implemented")
    }

    override fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>): CommitBundle {
        TODO("Not yet implemented")
    }
}
