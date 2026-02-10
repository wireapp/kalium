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

import io.mockative.Mockable
import kotlin.jvm.JvmInline

typealias WelcomeMessage = ByteArray
typealias HandshakeMessage = ByteArray
typealias ApplicationMessage = ByteArray
typealias PlainMessage = ByteArray
typealias MLSKeyPackage = ByteArray
typealias CertificateChain = String

enum class GroupInfoEncryptionType {
    PLAINTEXT,
    JWE_ENCRYPTED
}

enum class RatchetTreeType {
    FULL,
    DELTA,
    BY_REF
}

enum class E2EIConversationState {
    VERIFIED, NOT_VERIFIED, NOT_ENABLED
}

open class GroupInfoBundle(
    var encryptionType: GroupInfoEncryptionType,
    var ratchetTreeType: RatchetTreeType,
    var payload: ByteArray
)

data class CommitBundle(
    val commit: ByteArray,
    val welcome: ByteArray?,
    val groupInfoBundle: GroupInfoBundle
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CommitBundle

        if (!commit.contentEquals(other.commit)) return false
        if (welcome != null) {
            if (other.welcome == null) return false
            if (!welcome.contentEquals(other.welcome)) return false
        } else if (other.welcome != null) return false
        return groupInfoBundle == other.groupInfoBundle
    }

    override fun hashCode(): Int {
        var result = commit.contentHashCode()
        result = 31 * result + (welcome?.contentHashCode() ?: 0)
        result = 31 * result + groupInfoBundle.hashCode()
        return result
    }
}

data class WelcomeBundle(
    val groupId: MLSGroupId,
    val crlNewDistributionPoints: List<String>?
)

data class RotateBundle(
    var newKeyPackages: List<ByteArray>,
    val crlNewDistributionPoints: List<String>?
)

data class DecryptedMessageBundle(
    val message: ByteArray?,
    val commitDelay: Long?,
    val senderClientId: CryptoQualifiedClientId?,
    val hasEpochChanged: Boolean,
    val identity: WireIdentity?,
    val crlNewDistributionPoints: List<String>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DecryptedMessageBundle

        if (message != null) {
            if (other.message == null) return false
            if (!message.contentEquals(other.message)) return false
        } else if (other.message != null) return false
        if (commitDelay != other.commitDelay) return false
        if (senderClientId != other.senderClientId) return false
        if (hasEpochChanged != other.hasEpochChanged) return false
        if (identity != other.identity) return false
        return crlNewDistributionPoints == other.crlNewDistributionPoints
    }

    override fun hashCode(): Int {
        var result = message?.contentHashCode() ?: 0
        result = 31 * result + (commitDelay?.hashCode() ?: 0)
        result = 31 * result + (senderClientId?.hashCode() ?: 0)
        result = 31 * result + hasEpochChanged.hashCode()
        result = 31 * result + (identity?.hashCode() ?: 0)
        result = 31 * result + (crlNewDistributionPoints?.hashCode() ?: 0)
        return result
    }
}

@JvmInline
value class ExternalSenderKey(
    val value: ByteArray
)

enum class CredentialType {
    Basic,
    X509;

    companion object {
        val DEFAULT = Basic
    }
}

data class CrlRegistration(
    var dirty: Boolean,
    var expiration: ULong?
)

@Suppress("TooManyFunctions")
@Mockable
interface MLSClient {
    /**
     * Get the default ciphersuite for the client.
     * the Default ciphersuite is set when creating the mls client.
     */
    fun getDefaultCipherSuite(): MLSCiphersuite

    /**
     * Free up any resources and shutdown the client.
     *
     * It's illegal to perform any operation after calling closing a client.
     */
    suspend fun close()

    /**
     * Public key of the client's identity.
     *
     * @return public key of the client
     * @return ciphersuite used for the public key
     */
    suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite>

    /**
     * Conversation E2EI verification status.
     *
     * Read-only operation that does not require an explicit transaction context.
     */
    suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState

    /**
     * Get user identities in a conversation.
     *
     * Read-only operation that does not require an explicit transaction context.
     */
    suspend fun getUserIdentities(
        groupId: MLSGroupId,
        users: List<CryptoQualifiedID>
    ): Map<String, List<WireIdentity>>

    /**
     * Runs a block of code inside a CoreCrypto transaction.
     *
     * @param name optional name of the transaction (used for logging)
     * @param block transaction block executed with CoreCryptoContext
     * @return result of the block
     */
    suspend fun <R> transaction(name: String = "mls-transaction", block: suspend (context: MlsCoreCryptoContext) -> R): R
}
