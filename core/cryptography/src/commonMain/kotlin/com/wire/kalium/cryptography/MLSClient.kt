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
    val groupInfoBundle: GroupInfoBundle,
    val encryptedMessage: ByteArray? = null
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
        if (encryptedMessage != null) {
            if (other.encryptedMessage == null) return false
            if (!encryptedMessage.contentEquals(other.encryptedMessage)) return false
        } else if (other.encryptedMessage != null) return false
        return groupInfoBundle == other.groupInfoBundle
    }

    override fun hashCode(): Int {
        var result = commit.contentHashCode()
        result = 31 * result + (welcome?.contentHashCode() ?: 0)
        result = 31 * result + groupInfoBundle.hashCode()
        result = 31 * result + (encryptedMessage?.contentHashCode() ?: 0)
        return result
    }
}

sealed interface DecryptedMessageBundle {
    val identity: WireIdentity?

    data class Text(
        val message: ByteArray,
        val senderClientId: CryptoQualifiedClientId,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Text

            if (!message.contentEquals(other.message)) return false
            if (senderClientId != other.senderClientId) return false
            return identity == other.identity
        }

        override fun hashCode(): Int {
            var result = message.contentHashCode()
            result = 31 * result + senderClientId.hashCode()
            result = 31 * result + (identity?.hashCode() ?: 0)
            return result
        }
    }

    data class Commit(
        /** False when a removal commit made the local client inactive. */
        val isActive: Boolean,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle

    data class Proposal(
        val commitDelay: Long?,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle
}

sealed interface MLSDecryptResult {
    data class Success(val messages: List<DecryptedMessageBundle>) : MLSDecryptResult
    data object BufferedFutureMessage : MLSDecryptResult
    data object BufferedCommit : MLSDecryptResult
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

@Suppress("TooManyFunctions")
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
     * Adds a Basic credential when this client has no credential for its default cipher suite.
     *
     * Calling this more than once does not add another credential or replace an installed credential.
     */
    suspend fun initializeBasicCredential()

    /**
     * Public key of the client's identity.
     *
     * @return public key of the client
     * @return ciphersuite used for the public key
     */
    suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite>

    /** Return an owned reference to the newest installed credential of the requested type. */
    suspend fun getCredentialRef(credentialType: CredentialType): CryptoCredentialRef?

    /** Return owned references to all installed credentials of the requested type, newest first. */
    suspend fun getCredentialRefs(credentialType: CredentialType): List<CryptoCredentialRef>

    /**
     * Conversation E2EI verification status.
     *
     * Read-only operation that does not require an explicit transaction context.
     */
    suspend fun getGroupState(groupId: MLSGroupId): E2EIConversationState

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
