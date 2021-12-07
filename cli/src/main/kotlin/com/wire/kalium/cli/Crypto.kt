package com.wire.kalium.cli

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.network.api.message.UserIdToClientMap
import com.wire.kalium.network.api.model.UserIToClientIdToEncryptedMsgMap
import com.wire.kalium.network.api.prekey.PreKey
import com.wire.kalium.network.api.prekey.UserClientsToPreKeyMap
import java.io.Closeable
import java.io.IOException


interface Crypto : Closeable {
    @Throws(CryptoException::class)
    fun getIdentity(): ByteArray

    @Throws(CryptoException::class)
    fun getLocalFingerprint(): ByteArray

    @Throws(CryptoException::class)
    fun newLastPreKey(): PreKey

    @Throws(CryptoException::class)
    fun newPreKeys(from: Int, count: Int): ArrayList<PreKey>

    @Throws(CryptoException::class)
    fun encrypt(preKeys: PreKey, content: ByteArray): UserIToClientIdToEncryptedMsgMap

    /**
     * Append cipher to `msg` for each device using crypto box session. Ciphers for those devices that still
     * don't have the session will be skipped and those must be encrypted using prekeys:
     *
     * @param recipients List of device that are missing
     * @param content Plain text content to be encrypted
     */
    @Throws(CryptoException::class)
    fun encrypt(recipients: UserIdToClientMap, content: ByteArray): UserIToClientIdToEncryptedMsgMap

    @Throws(CryptoException::class)
    fun encrypt(recipients: UserClientsToPreKeyMap, content: ByteArray): UserIToClientIdToEncryptedMsgMap


    /**
     * Decrypt cipher either using existing session or it creates new session from this cipher and decrypts
     *
     * @param userId   Sender's User id
     * @param clientId Sender's Client id
     * @param cypher   Encrypted, Base64 encoded string
     * @return Decrypted Base64 encoded string
     * @throws CryptoException throws CryptoException
     */
    @Throws(CryptoException::class)
    fun decrypt(userId: String, clientId: String, cypher: String): String
    fun isClosed(): Boolean

    @Throws(IOException::class)
    fun purge()
}
