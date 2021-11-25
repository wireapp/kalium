//
// Wire
// Copyright (C) 2021 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.crypto

import com.wire.bots.cryptobox.CryptoException
import com.wire.bots.cryptobox.ICryptobox
import com.wire.kalium.api.prekey.MapUserClientsToPreKey
import com.wire.kalium.models.outbound.otr.Missing
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.models.outbound.otr.PreKeys
import com.wire.kalium.models.outbound.otr.Recipients
import java.util.*
import kotlin.collections.HashMap

/**
 * Wrapper for the Crypto Box. This class is thread safe.
 */
abstract class CryptoBase : Crypto {
    abstract fun box(): ICryptobox

    @Throws(CryptoException::class)
    override fun getIdentity(): ByteArray {
        return box().getIdentity()
    }

    @Throws(CryptoException::class)
    override fun getLocalFingerprint(): ByteArray {
        return box().getLocalFingerprint()
    }

    /**
     * Generate a new last prekey.
     */
    @Throws(CryptoException::class)
    override fun newLastPreKey(): PreKey {
        return toPreKey(box().newLastPreKey())
    }

    /**
     *
     *
     * Generate a new batch of ephemeral prekeys.
     *
     * If `start + num { >} 0xFFFE` the IDs wrap around and start
     * over at 0. Thus after any valid invocation of this method, the last generated
     * prekey ID is always `(start + num) % (0xFFFE + 1)`. The caller
     * can remember that ID and feed it back into this method as the start
     * ID when the next batch of ephemeral keys needs to be generated.
     *
     * @param from  The ID (&gt;= 0 and &lt;= 0xFFFE) of the first prekey to generate.
     * @param count The total number of prekeys to generate (&gt; 0 and &lt;= 0xFFFE).
     */
    @Throws(CryptoException::class)
    override fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        val ret = ArrayList<PreKey>(count)
        for (k in box().newPreKeys(from, count)) {
            val prekey = toPreKey(k)
            ret.add(prekey)
        }
        return ret
    }

    /**
     * For each prekey encrypt the content that is in the OtrMessage
     *
     * @param preKeys Prekeys
     * @param content Plain text content
     * @throws CryptoException throws Exception
     */
    @Throws(CryptoException::class)
    override fun encrypt(preKeys: PreKeys, content: ByteArray): Recipients {
        val recipients = Recipients()
        for (userId in preKeys.keys) {
            val clients = preKeys.getValue(userId)
            for (clientId in clients.keys) {
                val pk = clients[clientId]
                if (pk?.key != null) {
                    val id = createId(userId, clientId)
                    val cipher = box().encryptFromPreKeys(id, toPreKey(pk), content)
                    val s = Base64.getEncoder().encodeToString(cipher)
                    recipients.add(userId.toString(), clientId, s)
                }
            }
        }
        return recipients
    }

    /**
     * Append cipher to `msg` for each device using crypto box session. Ciphers for those devices that still
     * don't have the session will be skipped and those must be encrypted using prekeys:
     *
     * @param missing List of device that are missing
     * @param content Plain text content to be encrypted
     */
    @Throws(CryptoException::class)
    override fun encrypt(missing: Missing, content: ByteArray): Recipients {
        val recipients = Recipients()
        for (userId in missing.toUserIds()) {
            for (clientId in missing.ofUser(userId)!!) {
                val id = createId(userId, clientId)
                val cipher = box().encryptFromSession(id, content)
                if (cipher != null) {
                    val s = Base64.getEncoder().encodeToString(cipher)
                    recipients.add(userId.toString(), clientId, s)
                }
            }
        }
        return recipients
    }


    @Throws(CryptoException::class)
    override fun encrypt(missing: HashMap<String, List<String>>, content: ByteArray): Recipients {
        val recipients = Recipients()
        for (userId in missing.keys) {
            for (clientId in missing[userId]!!) {
                val id = createId(userId, clientId)
                val cipher = box().encryptFromSession(id, content)
                if (cipher != null) {
                    val s = Base64.getEncoder().encodeToString(cipher)
                    recipients.add(userId, clientId, s)
                }
            }
        }
        return recipients
    }


    override fun encryptPre(preKeys: MapUserClientsToPreKey, content: ByteArray): Recipients {
        val recipients = Recipients()
        for (userId in preKeys.keys) {
            val clients = preKeys.getValue(userId)
            for (clientId in clients.keys) {
                val pk = clients[clientId]
                if (pk?.key != null) {
                    val id = createId(userId, clientId)
                    val cipher = box().encryptFromPreKeys(id, toPreKey(pk), content)
                    val s = Base64.getEncoder().encodeToString(cipher)
                    recipients.add(userId.toString(), clientId, s)
                }
            }
        }
        return recipients
    }

    /**
     * Decrypt cipher either using existing session or it creates new session from this cipher and decrypts
     *
     * @param userId   Sender's User id
     * @param clientId Sender's Client id
     * @param cypher   Encrypted, Base64 encoded string
     * @return Decrypted Base64 encoded string
     * @throws CryptoException throws Exception
     */
    @Throws(CryptoException::class)
    override fun decrypt(userId: UUID, clientId: String, cypher: String): String {
        val decode = Base64.getDecoder().decode(cypher)
        val id = createId(userId, clientId)
        val cryptobox = box()
        val decrypt = cryptobox.decrypt(id, decode)
        return Base64.getEncoder().encodeToString(decrypt)
    }

    /**
     * Closes CryptoBox object. After this method is invoked no more operations on this object can be done
     */
    override fun close() = box().close()

    override fun isClosed(): Boolean = box().isClosed

    companion object {
        private fun toPreKey(preKey: PreKey): com.wire.bots.cryptobox.PreKey =
            com.wire.bots.cryptobox.PreKey(preKey.id, Base64.getDecoder().decode(preKey.key))


        private fun toPreKey(preKey: com.wire.bots.cryptobox.PreKey): PreKey =
            PreKey(preKey.id,Base64.getEncoder().encodeToString(preKey.data))

        private fun createId(userId: UUID?, clientId: String?): String? {
            return String.format("%s_%s", userId, clientId)
        }

        private fun createId(userId: String, clientId: String): String {
            return String.format("%s_%s", userId, clientId)
        }
    }
}
