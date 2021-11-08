//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
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
package com.wire.xenon.crypto

import kotlin.Throws
import java.io.IOException
import com.wire.xenon.backend.models.NewBot
import java.util.UUID
import com.fasterxml.jackson.databind.ObjectMapper
import com.wire.xenon.exceptions.MissingStateException
import com.wire.xenon.state.FileState
import org.jdbi.v3.core.Jdbi
import com.wire.xenon.state.StatesDAO
import com.wire.xenon.state.JdbiState
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import java.security.spec.KeySpec
import javax.crypto.spec.PBEKeySpec
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileInputStream
import java.io.BufferedWriter
import java.io.FileWriter
import java.security.MessageDigest
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.LogRecord
import java.lang.StringBuilder
import com.wire.xenon.tools.Logger.BotFormatter
import java.text.DateFormat
import java.text.SimpleDateFormat
import com.wire.xenon.assets.IGeneric
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages.Knock
import kotlin.jvm.JvmOverloads
import com.wire.xenon.assets.Poll
import com.wire.xenon.assets.MessageText
import com.wire.xenon.assets.AssetBase
import java.awt.image.BufferedImage
import com.waz.model.Messages.Asset.ImageMetaData
import com.waz.model.Messages.Asset.Original
import com.waz.model.Messages.Asset.RemoteData
import com.google.protobuf.ByteString
import com.waz.model.Messages.Ephemeral
import javax.imageio.ImageIO
import com.wire.xenon.assets.IAsset
import com.wire.xenon.assets.FileAsset
import com.wire.xenon.assets.AudioPreview
import com.wire.xenon.assets.Picture
import com.waz.model.Messages.Article
import com.waz.model.Messages.Quote
import com.waz.model.Messages.Asset.AudioMetaData
import com.waz.model.Messages.Asset.VideoMetaData
import com.waz.model.Messages.Confirmation
import com.wire.xenon.assets.MessageEphemeral
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper
import java.sql.SQLException
import java.sql.ResultSet
import org.jdbi.v3.core.statement.StatementContext
import com.wire.bots.cryptobox.IStorage
import com.wire.xenon.crypto.storage.SessionsDAO
import com.wire.xenon.crypto.storage.IdentitiesDAO
import com.wire.xenon.crypto.storage.PrekeysDAO
import com.wire.bots.cryptobox.IRecord
import com.wire.xenon.crypto.storage.IdentitiesDAO._Identity
import java.io.Closeable
import com.wire.bots.cryptobox.CryptoException
import com.wire.xenon.models.otr.PreKeys
import com.wire.xenon.models.otr.Recipients
import com.wire.xenon.models.otr.Missing
import com.wire.xenon.crypto.Crypto
import com.wire.bots.cryptobox.ICryptobox
import com.wire.xenon.crypto.CryptoBase
import java.util.HashMap
import com.wire.bots.cryptobox.CryptoBox
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.FileVisitOption
import com.wire.bots.cryptobox.CryptoDb
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.concurrent.ConcurrentHashMap
import com.wire.xenon.models.otr.ClientCipher
import com.wire.xenon.models.MessageBase
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.wire.xenon.models.MessageAssetBase
import com.wire.xenon.models.TextMessage
import com.wire.xenon.models.OriginMessage
import com.wire.xenon.models.ImageMessage
import com.wire.xenon.backend.models.Conversation
import com.wire.xenon.backend.models.Payload.Members
import com.wire.xenon.WireClient
import com.wire.xenon.MessageHandlerBase
import com.wire.xenon.models.LinkPreviewMessage
import com.wire.xenon.models.EphemeralTextMessage
import com.wire.xenon.models.EditedTextMessage
import com.wire.xenon.models.ConfirmationMessage
import com.wire.xenon.models.CallingMessage
import com.wire.xenon.models.DeletedTextMessage
import com.wire.xenon.models.ReactionMessage
import com.wire.xenon.models.PingMessage
import com.wire.xenon.models.PhotoPreviewMessage
import com.wire.xenon.models.AudioPreviewMessage
import com.wire.xenon.models.VideoPreviewMessage
import com.wire.xenon.models.FilePreviewMessage
import com.wire.xenon.models.RemoteMessage
import com.wire.xenon.exceptions.HttpException
import com.wire.xenon.models.otr.OtrMessage
import com.wire.xenon.models.otr.Devices
import com.wire.xenon.models.AssetKey
import com.wire.xenon.WireAPI
import com.wire.xenon.backend.models.SystemMessage
import java.util.Collections
import com.wire.xenon.backend.GenericMessageProcessor
import com.google.protobuf.InvalidProtocolBufferException
import com.wire.xenon.DatabaseTestBase
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import java.sql.DriverManager
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.junit.jupiter.api.AfterAll
import com.wire.xenon.GenericMessageProcessorTest
import com.wire.xenon.models.otr.PreKey
import org.junit.jupiter.api.Assertions
import java.util.ArrayList
import java.util.Base64

/**
 * Wrapper for the Crypto Box. This class is thread safe.
 */
internal abstract class CryptoBase : Crypto {
    abstract fun box(): ICryptobox?
    @Throws(CryptoException::class)
    override fun getIdentity(): ByteArray? {
        return box().getIdentity()
    }

    @Throws(CryptoException::class)
    override fun getLocalFingerprint(): ByteArray? {
        return box().getLocalFingerprint()
    }

    /**
     * Generate a new last prekey.
     */
    @Throws(CryptoException::class)
    override fun newLastPreKey(): PreKey? {
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
    override fun newPreKeys(from: Int, count: Int): ArrayList<PreKey?>? {
        val ret = ArrayList<PreKey?>(count)
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
    override fun encrypt(preKeys: PreKeys?, content: ByteArray?): Recipients? {
        val recipients = Recipients()
        for (userId in preKeys.keys) {
            val clients = preKeys.get(userId)
            for (clientId in clients.keys) {
                val pk = clients.get(clientId)
                if (pk != null && pk.key != null) {
                    val id = createId(userId, clientId)
                    val cipher = box().encryptFromPreKeys(id, toPreKey(pk), content)
                    val s = Base64.getEncoder().encodeToString(cipher)
                    recipients.add(userId, clientId, s)
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
    override fun encrypt(missing: Missing?, content: ByteArray?): Recipients? {
        val recipients = Recipients()
        for (userId in missing.toUserIds()) {
            for (clientId in missing.toClients(userId)) {
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
    override fun decrypt(userId: UUID?, clientId: String?, cypher: String?): String? {
        val decode = Base64.getDecoder().decode(cypher)
        val id = createId(userId, clientId)
        val cryptobox = box()
        val decrypt = cryptobox.decrypt(id, decode)
        return Base64.getEncoder().encodeToString(decrypt)
    }

    /**
     * Closes CryptoBox object. After this method is invoked no more operations on this object can be done
     */
    override fun close() {
        box().close()
    }

    override fun isClosed(): Boolean {
        return box().isClosed()
    }

    companion object {
        private fun toPreKey(preKey: PreKey?): com.wire.bots.cryptobox.PreKey? {
            return com.wire.bots.cryptobox.PreKey(preKey.id, Base64.getDecoder().decode(preKey.key))
        }

        private fun toPreKey(preKey: com.wire.bots.cryptobox.PreKey?): PreKey? {
            val ret = PreKey()
            ret.id = preKey.id
            ret.key = Base64.getEncoder().encodeToString(preKey.data)
            return ret
        }

        private fun createId(userId: UUID?, clientId: String?): String? {
            return String.format("%s_%s", userId, clientId)
        }
    }
}
