/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import kotlin.time.Duration

/**
 * Provides a composite context that can be used inside a crypto transaction.
 *
 * This context encapsulates access to both cryptographic engines:
 * - [mls]: The MLS cryptography context (can be `null` if not supported yet).
 * - [proteus]: The Proteus cryptography context.
 *
 * It is typically passed into blocks executed by [CryptoTransactionProvider.transaction].
 */
@Mockable
interface CryptoTransactionContext {
    val mls: MlsCoreCryptoContext?
    val proteus: ProteusCoreCryptoContext
}

@Mockable
@Suppress("TooManyFunctions")
interface MlsCoreCryptoContext {

    /**
     * Get the default ciphersuite for the client.
     * the Default ciphersuite is set when creating the mls client.
     */
    fun getDefaultCipherSuite(): MLSCiphersuite

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
     * @return nothing, because commit is handled by [MLSTransporter]
     */
    suspend fun updateKeyingMaterial(groupId: MLSGroupId)

    /**
     * Request to join an existing conversation by external commit
     *
     * @param publicGroupState MLS group state for an existing conversation
     *
     * @return welcome bundle, which needs to be sent to the distribution service.
     */
    suspend fun joinByExternalCommit(
        publicGroupState: ByteArray
    ): WelcomeBundle

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
        externalSenders: ByteArray
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
     * Create a commit for any pending proposals
     *
     * @return nothing, because commit is handled by [MLSTransporter]
     */
    suspend fun commitPendingProposals(groupId: MLSGroupId)

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
       groupId: String,
       message: ByteArray
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
     * @return potentially newly discovered certificate revocation list distribution points
     */
    suspend fun addMember(
        groupId: MLSGroupId,
        membersKeyPackages: List<MLSKeyPackage>
    ): List<String>?

    /**
     * Remove a user/client from an existing MLS group
     *
     * @param groupId MLS group
     * @param members list of clients
     *
     * @return nothing, because commit is handled by [MLSTransporter]
     */
    suspend fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    )

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
     * Enroll Wire E2EIdentity Client for E2EI when MLSClient already initialized
     *
     * @return wire end to end identity client
     */
    suspend fun e2eiNewActivationEnrollment(
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration
    ): E2EIClient

    /**
     * Enroll Wire E2EI Enrollment Client for renewing certificate
     *
     * @return wire end to end identity client
     */
    suspend fun e2eiNewRotateEnrollment(
        displayName: String?,
        handle: String?,
        teamId: String?,
        expiry: Duration
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
     * Deletes the stale key packages locally
     */
    suspend fun removeStaleKeyPackages()

    /**
     * Save the X509 Credential for the given enrollment
     *
     * @param enrollment the enrollment for which the credential is saved
     * @param certificateChain the certificate chain to be saved
     *
     * @return potentially newly discovered certificate revocation list distribution points
     */
    suspend fun saveX509Credential(enrollment: E2EIClient, certificateChain: CertificateChain): List<String>?

    /**
     * Rotates credentials for each conversation
     */
    suspend fun e2eiRotateGroups(groupList: List<MLSGroupId>)

    /**
     * Register Certificate Revocations List for an url for E2EI
     * @param url that the CRL downloaded from
     * @param crl fetched crl from the url
     */
    suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration
}

@Mockable
interface ProteusCoreCryptoContext {
    suspend fun <T : Any> decryptMessage(
        sessionId: CryptoSessionId,
        message: ByteArray,
        handleDecryptedMessage: suspend (decryptedMessage: ByteArray) -> T
    ): T

    suspend fun getLocalFingerprint(): String
    suspend fun remoteFingerPrint(sessionId: CryptoSessionId): String
    suspend fun getFingerprintFromPreKey(preKey: PreKeyCrypto): String
    suspend fun newLastResortPreKey(): PreKeyCrypto
    suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean
    suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId)

    suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray
    suspend fun encryptBatched(
        message: ByteArray,
        sessionIds: List<CryptoSessionId>
    ): Map<CryptoSessionId, ByteArray>

    suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray

    suspend fun deleteSession(sessionId: CryptoSessionId)
}

suspend fun ProteusCoreCryptoContext.createSessions(
    preKeysCrypto: Map<String, Map<String, Map<String, PreKeyCrypto>>>
) {
    preKeysCrypto.forEach { domainMap ->
        val domain = domainMap.key
        domainMap.value.forEach { userToClientsMap ->
            val userId = userToClientsMap.key
            userToClientsMap.value.forEach { clientsToPreKeyMap ->
                val clientId = clientsToPreKeyMap.key
                val id = CryptoSessionId(CryptoUserID(userId, domain), CryptoClientId(clientId))
                createSession(clientsToPreKeyMap.value, id)
            }
        }
    }
}
