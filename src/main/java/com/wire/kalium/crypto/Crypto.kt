package com.wire.kalium.crypto

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.models.otr.Missing
import com.wire.kalium.models.otr.PreKey
import com.wire.kalium.models.otr.PreKeys
import com.wire.kalium.models.otr.Recipients
import java.io.Closeable
import java.io.IOException
import java.util.*

interface Crypto : Closeable {
    @Throws(CryptoException::class)
    open fun getIdentity(): ByteArray

    @Throws(CryptoException::class)
    open fun getLocalFingerprint(): ByteArray

    @Throws(CryptoException::class)
    open fun newLastPreKey(): PreKey

    @Throws(CryptoException::class)
    open fun newPreKeys(from: Int, count: Int): ArrayList<PreKey>

    @Throws(CryptoException::class)
    open fun encrypt(preKeys: PreKeys, content: ByteArray): Recipients

    /**
     * Append cipher to `msg` for each device using crypto box session. Ciphers for those devices that still
     * don't have the session will be skipped and those must be encrypted using prekeys:
     *
     * @param missing List of device that are missing
     * @param content Plain text content to be encrypted
     */
    @Throws(CryptoException::class)
    open fun encrypt(missing: Missing, content: ByteArray): Recipients

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
    open fun decrypt(userId: UUID, clientId: String, cypher: String): String
    open fun isClosed(): Boolean
    @Throws(IOException::class)
    open fun purge()
}
