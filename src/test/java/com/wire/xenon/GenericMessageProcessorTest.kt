package com.wire.xenon

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
import com.waz.model.Messages
import com.wire.xenon.DatabaseTestBase
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import java.sql.DriverManager
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.junit.jupiter.api.AfterAll
import com.wire.xenon.GenericMessageProcessorTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.Random

class GenericMessageProcessorTest {
    @Test
    fun testLinkPreview() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(null, handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val image = ImageMetaData.newBuilder()
            .setHeight(HEIGHT)
            .setWidth(WIDTH)
        val original = Original.newBuilder()
            .setSize(SIZE.toLong())
            .setMimeType(MIME_TYPE)
            .setImage(image)
        val uploaded = RemoteData.newBuilder()
            .setAssetId(ASSET_KEY)
            .setAssetToken(ASSET_TOKEN)
            .setOtrKey(ByteString.EMPTY)
            .setSha256(ByteString.EMPTY)
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original)
            .setUploaded(uploaded)
        val linkPreview = Messages.LinkPreview.newBuilder()
            .setTitle(TITLE)
            .setSummary(SUMMARY)
            .setUrl(URL)
            .setUrlOffset(URL_OFFSET)
            .setImage(asset)
        val text = Messages.Text.newBuilder()
            .setContent(CONTENT)
            .addLinkPreview(linkPreview)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setText(text)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    @Test
    fun testAudioOrigin() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(null, handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val levels = ByteArray(100)
        Random().nextBytes(levels)
        val audioMeta = AudioMetaData.newBuilder()
            .setDurationInMillis(DURATION.toLong())
            .setNormalizedLoudness(ByteString.copyFrom(levels))
        val original = Original.newBuilder()
            .setSize(SIZE.toLong())
            .setName(NAME)
            .setMimeType(AUDIO_MIME_TYPE)
            .setAudio(audioMeta)
        val asset = Messages.Asset.newBuilder()
            .setOriginal(original)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    @Test
    fun testAudioUploaded() {
        val handler = MessageHandler()
        val processor = GenericMessageProcessor(null, handler)
        val eventId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val from = UUID.randomUUID()
        val convId = UUID.randomUUID()
        val sender = UUID.randomUUID().toString()
        val time = Date().toString()
        val levels = ByteArray(100)
        Random().nextBytes(levels)
        val uploaded = RemoteData.newBuilder()
            .setAssetId(ASSET_KEY)
            .setAssetToken(ASSET_TOKEN)
            .setOtrKey(ByteString.EMPTY)
            .setSha256(ByteString.EMPTY)
        val asset = Messages.Asset.newBuilder()
            .setUploaded(uploaded)
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setAsset(asset)
        val msgBase = MessageBase(eventId, messageId, convId, sender, from, time)
        processor.process(msgBase, builder.build())
    }

    private class MessageHandler : MessageHandlerBase() {
        override fun onLinkPreview(client: WireClient?, msg: LinkPreviewMessage?) {
            Assertions.assertEquals(TITLE, msg.getTitle())
            Assertions.assertEquals(SUMMARY, msg.getSummary())
            Assertions.assertEquals(URL, msg.getUrl())
            Assertions.assertEquals(URL_OFFSET, msg.getUrlOffset())
            Assertions.assertEquals(CONTENT, msg.getText())
            Assertions.assertEquals(WIDTH, msg.getWidth())
            Assertions.assertEquals(HEIGHT, msg.getHeight())
            Assertions.assertEquals(SIZE.toLong(), msg.getSize())
            Assertions.assertEquals(MIME_TYPE, msg.getMimeType())
            Assertions.assertEquals(ASSET_TOKEN, msg.getAssetToken())
        }

        override fun onAudioPreview(client: WireClient?, msg: AudioPreviewMessage?) {
            Assertions.assertEquals(AUDIO_MIME_TYPE, msg.getMimeType())
        }
    }

    companion object {
        val AUDIO_MIME_TYPE: String? = "audio/x-m4a"
        val NAME: String? = "audio.m4a"
        const val DURATION = 27000
        private val TITLE: String? = "title"
        private val SUMMARY: String? = "summary"
        private val URL: String? = "https://wire.com"
        private val CONTENT: String? = "This is https://wire.com"
        private const val URL_OFFSET = 8
        private val ASSET_KEY: String? = "key"
        private val ASSET_TOKEN: String? = "token"
        private const val HEIGHT = 43
        private const val WIDTH = 84
        private const val SIZE = 123
        private val MIME_TYPE: String? = "image/png"
    }
}
