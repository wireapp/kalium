package com.wire.kalium.cryptography

import kotlin.jvm.JvmInline

typealias WelcomeMessage = ByteArray
typealias HandshakeMessage = ByteArray
typealias ApplicationMessage = ByteArray
typealias PlainMessage = ByteArray
typealias MLSKeyPackage = ByteArray

interface MLSClient {

    /**
     * Release any locks the C code have on the MLS resources and
     * delete local MLS DB and files
     *
     * @return true when delete is successful and false otherwise
     */
    fun clearLocalFiles(): Boolean

    /**
     * Public key of the client's identity.
     *
     * @return public key of the client
     */
    fun getPublicKey(): ByteArray

    /**
     * Generate a fresh set of key packages.
     *
     * @return list of generated key packages. NOTE: can be more than the requested amount.
     */
    fun generateKeyPackages(amount: Int): List<ByteArray>

    /**
     * Query if a conversation exists
     *
     * @param groupId MLS group ID provided by BE
     *
     * @return true if conversation exists in store
     */
    fun conversationExists(groupId: MLSGroupId): Boolean

    /**
     * Create a new MLS conversation
     *
     * @param groupId MLS group ID provided by BE
     * @param members list of clients with a claimed key package for each client.
     *
     * @return handshake & welcome message
     */
    fun createConversation(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>?

    /**
     * Process an incoming welcome message
     *
     * @param message the incoming welcome message
     * @return MLS group ID
     */
    fun processWelcomeMessage(message: WelcomeMessage): MLSGroupId

    /**
     * Encrypt a message for distribution in a group
     *
     * @param groupId MLS group ID provided by BE
     * @param message plain text message
     *
     * @return encrypted ApplicationMessage
     */
    fun encryptMessage(
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
     * @return decrypted message in case of an application message.
     */
    fun decryptMessage(
        groupId: MLSGroupId,
        message: ApplicationMessage
    ): PlainMessage?

    /**
     * Add a user/client to an existing MLS group
     *
     * @param groupId MLS group
     * @param members list of clients with a claimed key package for each client.
     *
     * * @return handshake & welcome message
     */
    fun addMember(
        groupId: MLSGroupId,
        members: List<Pair<CryptoQualifiedClientId, MLSKeyPackage>>
    ): Pair<HandshakeMessage, WelcomeMessage>?

    /**
     * Remove a user/client from an existing MLS group
     *
     * @param groupId MLS group
     * @param members list of clients
     *
     * @return handshake message
     */
    fun removeMember(
        groupId: MLSGroupId,
        members: List<CryptoQualifiedClientId>
    ): HandshakeMessage?
}

@JvmInline
value class MlsDBSecret(val value: String)

expect class MLSClientImpl(rootDir: String, databaseKey: MlsDBSecret, clientId: CryptoQualifiedClientId) : MLSClient
