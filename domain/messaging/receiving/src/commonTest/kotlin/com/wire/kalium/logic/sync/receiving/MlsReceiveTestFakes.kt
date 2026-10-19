/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.ApplicationMessage
import com.wire.kalium.cryptography.CryptoCredentialRef
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSDecryptResult
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.MLSKeyPackage
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PlainMessage
import com.wire.kalium.cryptography.WelcomeMessage
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlin.test.assertEquals

/** In-memory metadata supporting the plain key reads and writes MLS receiving uses; counts range reads. */
internal class FakeMetadata(private val failInsert: Boolean = false) : MetadataDAO {
    val values = mutableMapOf<String, String>()
    var rangeReads = 0
    override suspend fun insertValue(value: String, key: String) {
        if (failInsert) error("Storage failed")
        values[key] = value
    }
    override suspend fun valueByKey(key: String): String? = values[key]
    override suspend fun valuesByKeyRange(fromInclusive: String, untilExclusive: String): Map<String, String> =
        values.filterKeys { it >= fromInclusive && it < untilExclusive }.also { rangeReads++ }
    override suspend fun deleteValue(key: String) = error("Unexpected delete")
    override fun valueByKeyFlow(key: String): Flow<String?> = error("Unexpected observe")
    override suspend fun clear(keysToKeep: List<String>?) = error("Unexpected clear")
    override suspend fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) = error("Unexpected put")
    override suspend fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? = error("Unexpected get")
    override fun <T> observeSerializable(key: String, kSerializer: KSerializer<T>): Flow<T?> = error("Unexpected observe")
}

/** One MLS group at [epoch] with a transaction checkpoint; counts native reads and checkpoint writes. */
internal class FakeMlsContext(private val groupId: MLSGroupId = "group") : UnexpectedMlsContext() {
    var exists = true
    var epoch = 2uL
    var reads = 0
    var checkpoint: ByteArray? = null
    var checkpointWrites = 0
    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): MLSGroupId = error("Use the explicit join callback")
    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong {
        reads++
        assertEquals(this.groupId, groupId)
        return epoch
    }
    override suspend fun readTransactionCheckpoint(): ByteArray? { reads++; return checkpoint }
    override suspend fun writeTransactionCheckpoint(checkpoint: ByteArray) {
        checkpointWrites++
        this.checkpoint = checkpoint
    }
    override suspend fun conversationExists(groupId: MLSGroupId): Boolean { reads++; return exists }
}

/** Fails every native call; fakes override the calls their tests expect. */
internal open class UnexpectedMlsContext : MlsCoreCryptoContext {
    override suspend fun joinByExternalCommit(publicGroupState: ByteArray): MLSGroupId = error("Unexpected native call")
    override suspend fun conversationEpoch(groupId: MLSGroupId): ULong = error("Unexpected native call")
    override suspend fun readTransactionCheckpoint(): ByteArray? = error("Unexpected native call")
    override suspend fun writeTransactionCheckpoint(checkpoint: ByteArray): Unit = error("Unexpected native call")
    override fun getDefaultCipherSuite(): MLSCiphersuite = error("Unexpected native call")
    override suspend fun generateKeyPackages(amount: Int, credentialRef: CryptoCredentialRef?): List<ByteArray> =
        error("Unexpected native call")
    override suspend fun validKeyPackageCount(): ULong = error("Unexpected native call")
    override suspend fun updateKeyingMaterial(groupId: MLSGroupId): Unit = error("Unexpected native call")
    override suspend fun conversationExists(groupId: MLSGroupId): Boolean = error("Unexpected native call")
    override suspend fun createConversation(groupId: MLSGroupId, externalSenders: ByteArray): Unit = error("Unexpected native call")
    override suspend fun getExternalSenders(groupId: MLSGroupId): ExternalSenderKey = error("Unexpected native call")
    override suspend fun wipeConversation(groupId: MLSGroupId): Unit = error("Unexpected native call")
    override suspend fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId = error("Unexpected native call")
    override suspend fun commitPendingProposals(groupId: MLSGroupId): Unit = error("Unexpected native call")
    override suspend fun encryptMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage = error("Unexpected native call")
    override suspend fun encryptTransientMessage(groupId: MLSGroupId, message: PlainMessage): ApplicationMessage =
        error("Unexpected native call")
    override suspend fun encryptTargetedMessage(
        groupId: MLSGroupId,
        recipient: CryptoQualifiedClientId,
        message: PlainMessage,
        transient: Boolean
    ): ApplicationMessage = error("Unexpected native call")
    override suspend fun decryptMessage(groupId: String, message: ByteArray): MLSDecryptResult = error("Unexpected native call")
    override suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId> = error("Unexpected native call")
    override suspend fun addMember(groupId: MLSGroupId, membersKeyPackages: List<MLSKeyPackage>): Unit = error("Unexpected native call")
    override suspend fun removeMember(groupId: MLSGroupId, members: List<CryptoQualifiedClientId>): Unit = error("Unexpected native call")
    override suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray = error("Unexpected native call")
    override suspend fun setConversationCredential(groupId: MLSGroupId, credentialRef: CryptoCredentialRef): Unit =
        error("Unexpected native call")
    override suspend fun getConversationCredentialRef(groupId: MLSGroupId): CryptoCredentialRef = error("Unexpected native call")
    override suspend fun removeKeyPackages(credentialRef: CryptoCredentialRef): Unit = error("Unexpected native call")
    override suspend fun removeCredential(credentialRef: CryptoCredentialRef): Unit = error("Unexpected native call")
    override suspend fun checkCredentials(): Unit = error("Unexpected native call")
    override suspend fun isE2EIEnabled(): Boolean = error("Unexpected native call")
    override suspend fun getGroupState(groupId: MLSGroupId): E2EIConversationState = error("Unexpected native call")
    override suspend fun getDeviceIdentities(groupId: MLSGroupId, clients: List<CryptoQualifiedClientId>): List<WireIdentity> =
        error("Unexpected native call")
    override suspend fun getUserIdentities(groupId: MLSGroupId, users: List<CryptoQualifiedID>): Map<String, List<WireIdentity>> =
        error("Unexpected native call")
    override suspend fun removeStaleKeyPackages(): Unit = error("Unexpected native call")
}
