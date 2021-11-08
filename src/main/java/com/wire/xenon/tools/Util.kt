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
package com.wire.xenon.tools

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
import org.junit.jupiter.api.Assertions
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Mac

object Util {
    private val pattern = Pattern.compile("(?<=@)([a-zA-Z0-9\\_]{3,})")
    private val HMAC_SHA_1: String? = "HmacSHA1"
    @Throws(Exception::class)
    fun encrypt(key: ByteArray?, dataToSend: ByteArray?, iv: ByteArray?): ByteArray? {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKeySpec = SecretKeySpec(key, "AES")
        c.init(Cipher.ENCRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        val bytes = c.doFinal(dataToSend)
        val os = ByteArrayOutputStream()
        os.write(iv)
        os.write(bytes)
        return os.toByteArray()
    }

    @Throws(Exception::class)
    fun decrypt(key: ByteArray?, encrypted: ByteArray?): ByteArray? {
        val `is` = ByteArrayInputStream(encrypted)
        val iv = ByteArray(16)
        `is`.read(iv)
        val bytes = toByteArray(`is`)
        val vec = IvParameterSpec(iv)
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, vec)
        return cipher.doFinal(bytes)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun genKey(password: CharArray?, salt: ByteArray?): SecretKey? {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(password, salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    @Throws(IOException::class)
    fun readLine(file: File?): String? {
        BufferedReader(FileReader(file)).use { br -> return br.readLine() }
    }

    @Throws(IOException::class)
    fun readFile(f: File?): String? {
        FileInputStream(f).use { fis ->
            val data = ByteArray(f.length() as Int)
            fis.read(data)
            return String(data, StandardCharsets.UTF_8)
        }
    }

    @Throws(IOException::class)
    fun writeLine(line: String?, file: File?) {
        BufferedWriter(FileWriter(file)).use { bw -> bw.write(line) }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun calcMd5(bytes: ByteArray?): String? {
        val md = MessageDigest.getInstance("MD5")
        md.update(bytes, 0, bytes.size)
        val hash = md.digest()
        val byteArray = Base64.getEncoder().encode(hash)
        return String(byteArray)
    }

    fun digest(md: MessageDigest?, bytes: ByteArray?): String? {
        md.update(bytes, 0, bytes.size)
        val hash = md.digest()
        val byteArray = Base64.getEncoder().encode(hash)
        return String(byteArray)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    fun getHmacSHA1(payload: String?, secret: String?): String? {
        val hmac = Mac.getInstance(HMAC_SHA_1)
        hmac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA_1))
        val bytes = hmac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return String.format("%040x", BigInteger(1, bytes))
    }

    @Throws(IOException::class)
    fun toByteArray(input: InputStream?): ByteArray? {
        ByteArrayOutputStream().use { output ->
            var n: Int
            val buffer = ByteArray(1024 * 4)
            while (-1 != input.read(buffer).also { n = it }) {
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        }
    }

    fun compareAuthorizations(auth1: String?, auth2: String?): Boolean {
        if (auth1 == null || auth2 == null) return false
        val token1 = extractToken(auth1)
        val token2 = extractToken(auth2)
        return token1 == token2
    }

    fun extractToken(auth: String?): String? {
        val split: Array<String?> = auth.split(" ").toTypedArray()
        return if (split.size == 1) split[0] else split[1]
    }

    @Throws(IOException::class)
    fun extractMimeType(imageData: ByteArray?): String? {
        ByteArrayInputStream(imageData).use { input ->
            val contentType = URLConnection.guessContentTypeFromStream(input)
            return contentType ?: "image/xyz"
        }
    }

    @Throws(IOException::class)
    fun getResource(name: String?): ByteArray? {
        val classLoader = Util::class.java.classLoader
        classLoader.getResourceAsStream(name).use { resourceAsStream -> return toByteArray(resourceAsStream) }
    }

    fun mentionLen(txt: String?): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.group().length + 1
        } else 0
    }

    fun mentionStart(txt: String?): Int {
        val matcher = pattern.matcher(txt)
        return if (matcher.find()) {
            matcher.start() - 1
        } else 0
    }
}
