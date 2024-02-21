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
    VERIFIED, NOT_VERIFIED, NOT_ENABLED;
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
    val crlNewDistributionPoints: List<String>?
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
        if (groupInfoBundle != other.groupInfoBundle) return false
        return crlNewDistributionPoints == other.crlNewDistributionPoints
    }

    override fun hashCode(): Int {
        var result = commit.contentHashCode()
        result = 31 * result + (welcome?.contentHashCode() ?: 0)
        result = 31 * result + groupInfoBundle.hashCode()
        result = 31 * result + (crlNewDistributionPoints?.hashCode() ?: 0)
        return result
    }
}

data class WelcomeBundle(
    val groupId: MLSGroupId,
    val crlNewDistributionPoints: List<String>?
)

data class RotateBundle(
    var commits: Map<MLSGroupId, CommitBundle>,
    var newKeyPackages: List<ByteArray>,
    var keyPackageRefsToRemove: List<ByteArray>,
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
value class Ed22519Key(
    val value: ByteArray
)

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
interface MLSClient {

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
     */
    suspend fun getPublicKey(): ByteArray

    /**
     * Generate a fresh set of key packages.
     *
     * @return list of generated key packages. NOTE: can be more than the requested amount.
     */
    suspend fun generateKeyPackages(amount: Int): List<ByteArray>

    /**
     * Number of valid key packages which haven't been consumed
     *
     * @return valid key package count
     */
    suspend fun validKeyPackageCount(): ULong

    /**
     * Update your keying material for an existing conversation you're a member of.
     *
     * @param groupId MLS group ID for an existing conversation
     *
     * @return commit bundle, which needs to be sent to the distribution service.
     */
    suspend fun updateKeyingMaterial(groupId: MLSGroupId): CommitBundle

    /**
     * Request to join an existing conversation
     *
     * @param groupId MLS group ID for an existing conversation
     * @param epoch current epoch for the conversation
     *
     * @return proposal, which needs to be sent to the distribution service.
     */
    suspend fun joinConversation(
        groupId: MLSGroupId,
        epoch: ULong
    ): HandshakeMessage

    /**
     * Request to join an existing conversation by external commit
     *
     * @param publicGroupState MLS group state for an existing conversation
     *
     * @return commit bundle, which needs to be sent to the distribution service.
     */
    suspend fun joinByExternalCommit(
        publicGroupState: ByteArray
    ): CommitBundle

    /**
     * Request to merge an existing conversation by external commit
     *
     * @param groupId MLS group ID provided by BE
     */
    suspend fun mergePendingGroupFromExternalCommit(groupId: MLSGroupId)

    /**
     * Clear pending external commits
     *
     * @param groupId MLS group ID provided by BE
     */
    suspend fun clearPendingGroupExternalCommit(groupId: MLSGroupId)

    /**
     * Query if a conversation exists
     *
     * @param groupId MLS group ID provided by BE
     *
     * @return true if conversation exists in store
     */
    suspend fun conversationExists(groupId: MLSGroupId): Boolean

    /**
     * Query the current epoch of a conversation
     *
     * @return conversation epoch
     */
    suspend fun conversationEpoch(groupId: MLSGroupId): ULong

    /**
     * Create a new MLS conversation
     *
     * @param groupId MLS group ID provided by BE
     */
    suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: List<Ed22519Key> = emptyList()
    )

    /**
     * Get External Senders of an MLS conversation
     *
     * @param groupId MLS group ID provided by BE
     */
    suspend fun getExternalSenders(
        groupId: MLSGroupId
    ): ExternalSenderKey

    suspend fun wipeConversation(groupId: MLSGroupId)

    /**
     * Process an incoming welcome message
     *
     * @param message the incoming welcome message
     * @return MLS group ID
     */
    suspend fun processWelcomeMessage(message: WelcomeMessage): WelcomeBundle

    /**
     * Signal that last sent commit was accepted by the distribution service
     */
    suspend fun commitAccepted(groupId: MLSGroupId)

    /**
     * Create a commit for any pending proposals
     *
     * @return commit bundle, which needs to be sent to the distribution service. If there are no
     * pending proposals null is returned.
     */
    suspend fun commitPendingProposals(groupId: MLSGroupId): CommitBundle?

    /**
     * Clear a pending commit which has not yet been accepted by the distribution service
     */
    suspend fun clearPendingCommit(groupId: MLSGroupId)

    /**
     * Encrypt a message for distribution in a group
     *
     * @param groupId MLS group ID provided by BE
     * @param message plain text message
     *
     * @return encrypted ApplicationMessage
     */
    suspend fun encryptMessage(
        groupId: MLSGroupId,
        message: PlainMessage
    ): ApplicationMessage

    /**
     * Decrypt an application message or a handshake message
     *
     * **NOTE**: handshake messages doesn't return any decrypted message.
     *
     * @param groupId MLS group where the message was received
     * @param message application message or handshake message
     *
     * @return decrypted message bundle, which contains the decrypted message.
     */
    suspend fun decryptMessage(
        groupId: MLSGroupId,
        message: ApplicationMessage
    ): List<DecryptedMessageBundle>

    /**
     * Current members of the group.
     *
     * @param groupId MLS group
     *
     * @return list of client IDs for all current members.
     */
    suspend fun members(groupId: MLSGroupId): List<CryptoQualifiedClientId>

    /**
     * Add a user/client to an existing MLS group
     *
     * @param groupId MLS group
     * @param membersKeyPackages list of claimed key package for each member.
     *
     * @return commit bundle, which needs to be sent to the distribution service.
     */
    suspend fun addMember(
        groupId: MLSGroupId,
        membersKeyPackages: List<MLSKeyPackage>
    ): CommitBundle?

    /**
     * Remove a user/client from an existing MLS group
     *
     * @param groupId MLS group
     * @param members list of clients
     *
     * @return commit bundle, which needs to be sent to the distribution service.
     */
    suspend fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): CommitBundle

    /**
     * Derive a secret key from the current MLS group state
     *
     * @param groupId MLS group
     * @param keyLength length of derived key in bytes
     *
     * @return secret key
     */
    suspend fun deriveSecret(groupId: MLSGroupId, keyLength: UInt): ByteArray

    /**
     * Enroll Wire E2EIdentity Client for E2EI before MLSClient Initialization
     *
     * @return wire end to end identity client
     */
    suspend fun newAcmeEnrollment(
        clientId: CryptoQualifiedClientId,
        displayName: String,
        handle: String,
        teamId: String?
    ): E2EIClient

    /**
     * Enroll Wire E2EIdentity Client for E2EI when MLSClient already initialized
     *
     * @return wire end to end identity client
     */
    suspend fun e2eiNewActivationEnrollment(
        displayName: String,
        handle: String,
        teamId: String?
    ): E2EIClient

    /**
     * Enroll Wire E2EI Enrollment Client for renewing certificate
     *
     * @return wire end to end identity client
     */
    suspend fun e2eiNewRotateEnrollment(
        displayName: String?,
        handle: String?,
        teamId: String?
    ): E2EIClient

    /**
     * Init MLSClient after enrollment
     */
    suspend fun e2eiMlsInitOnly(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>?

    /**
     * The E2EI State for the current MLS Client
     *
     * @return the E2EI state for the current MLS Client
     */
    suspend fun isE2EIEnabled(): Boolean

    /**
     * The MLS Credentials based on the E2EI State
     *
     * @return the MLS Credentials for the current MLS Client
     */
    suspend fun getMLSCredentials(): CredentialType

    /**
     * Generate new keypackages after E2EI certificate issued
     */
    suspend fun e2eiRotateAll(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt
    ): RotateBundle

    /**
     * Conversation E2EI Verification Status
     *
     * @return the conversation verification status
     */
    suspend fun isGroupVerified(groupId: MLSGroupId): E2EIConversationState

    /**
     * Get the identity of given clients in the given conversation
     *
     * @param clients a list of E2EIClientId of the requested clients
     * @param groupId MLS group ID for an existing conversation
     *
     * @return the exist identities for requested clients
     */
    suspend fun getDeviceIdentities(
        groupId: MLSGroupId,
        clients: List<CryptoQualifiedClientId>
    ): List<WireIdentity>

    /**
     * Get the identity of given users in the given conversation
     *
     * @param users list of requested users
     * @param groupId MLS group ID for an existing conversation
     *
     * @return the exist identities for requested clients
     */
    suspend fun getUserIdentities(
        groupId: MLSGroupId,
        users: List<CryptoQualifiedID>
    ): Map<String, List<WireIdentity>>

    /**
     * Register ACME-CA certificates for E2EI
     * @param pem is the certificate string in pem format
     */
    suspend fun registerTrustAnchors(pem: CertificateChain)

    /**
     * Register Certificate Revocations List for a url for E2EI
     * @param url that the CRL downloaded from
     * @param crl fetched crl from the url
     */
    suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration

    /**
     * Register Intermediate CA for E2EI
     * @param pem fetched certificate chain in pem format from the CA
     */
    suspend fun registerIntermediateCa(pem: CertificateChain)
}
