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

    override fun joinByExternalCommit(publicGroupState: ByteArray): CommitBundle {
        TODO("Not yet implemented")
    }

    override fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun clearPendingGroupExternalCommit(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override fun conversationExists(groupId: MLSGroupId): Boolean {
        TODO("Not yet implemented")
    }

    override fun conversationEpoch(groupId: MLSGroupId): ULong {
        TODO("Not yet implemented")
    }

    override fun createConversation(groupId: MLSGroupId, externalSenders: List<Ed22519Key>) {
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

    override fun commitPendingProposals(groupId: MLSGroupId): CommitBundle? {
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

    override fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        TODO("Not yet implemented")
    }

    override fun addMember(groupId: MLSGroupId, members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>): CommitBundle? {
        TODO("Not yet implemented")
    }

    override fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>): CommitBundle {
        TODO("Not yet implemented")
    }

    override fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        TODO("Not yet implemented")
    }

    override fun newAcmeEnrollment(clientId: E2EIQualifiedClientId, displayName: String, handle: String): E2EIClient {
        TODO("Not yet implemented")
    }

    override fun e2eiNewActivationEnrollment(displayName: String, handle: String): E2EIClient {
        TODO("Not yet implemented")
    }

    override fun e2eiNewRotateEnrollment(displayName: String?, handle: String?): E2EIClient {
        TODO("Not yet implemented")
    }

    override fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain) {
        TODO("Not yet implemented")
    }

    override fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ) {
        TODO("Not yet implemented")
    }

    override fun isGroupVerified(groupId: MLSGroupId): Boolean {
        TODO("Not supported on js")
    }
}
