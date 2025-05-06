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

import kotlinx.datetime.Instant
import kotlin.time.Duration

@Suppress("TooManyFunctions")
class MLSClientImpl : MLSClient {
    override fun getDefaultCipherSuite(): MLSCiphersuite {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> {
        TODO("Not yet implemented")
    }

    override suspend fun generateKeyPackages(amount: Int): List<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun validKeyPackageCount(): ULong {
        TODO("Not yet implemented")
    }

    override suspend fun updateKeyingMaterial(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): WelcomeBundle {
        TODO("Not yet implemented")
    }

    override suspend fun conversationExists(groupId: MLSGroupId): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
        TODO("Not yet implemented")
    }

    override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray) {
        TODO("Not yet implemented")
    }

    override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey {
        TODO("Not yet implemented")
    }

    override suspend fun wipeConversation(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle {
        TODO("Not yet implemented")
    }

    override suspend fun commitPendingProposals(groupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage {
        TODO("Not yet implemented")
    }

    override suspend fun decryptMessage(
        groupId: MLSGroupId,
        message: ApplicationMessage,
        messageInstant: Instant
    ): List<DecryptedMessageBundle> {
        TODO("Not yet implemented")
    }

    override suspend fun decryptMessages(groupId: MLSGroupId, messages: List<EncryptedMessage>): DecryptedBatch {
        TODO("Not yet implemented")
    }

    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> {
        TODO("Not yet implemented")
    }

    override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): List<String>? {
        TODO("Not yet implemented")
    }

    override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>) {
        TODO("Not yet implemented")
    }

    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray {
        TODO("Not yet implemented")
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

    override suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState {
        TODO("Not supported on js")
    }

    override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> {
        TODO("Not yet implemented")
    }

    override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> {
        TODO("Not yet implemented")
    }

    override suspend fun removeStaleKeyPackages() {
        TODO("Not yet implemented")
    }

    override suspend fun saveX509Credential(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>? {
        TODO("Not yet implemented")
    }

    override suspend fun e2eiRotateGroups(groupList: List<MLSGroupId>) {
        TODO("Not yet implemented")
    }
}
