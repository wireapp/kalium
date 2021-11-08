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
import com.wire.xenon.DatabaseTestBase
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import java.sql.DriverManager
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.junit.jupiter.api.AfterAll
import com.wire.xenon.GenericMessageProcessorTest
import com.wire.xenon.backend.models.Member
import com.wire.xenon.backend.models.Payload
import com.wire.xenon.tools.Logger
import org.junit.jupiter.api.Assertions
import java.lang.Exception
import java.util.Base64

abstract class MessageResourceBase(protected val handler: MessageHandlerBase?) {
    @Throws(Exception::class)
    protected fun handleMessage(eventId: UUID?, payload: Payload?, client: WireClient?) {
        val data = payload.data
        val botId = client.getId()
        when (payload.type) {
            "conversation.otr-message-add" -> {
                val from = payload.from
                Logger.debug("conversation.otr-message-add: bot: %s from: %s:%s", botId, from, data.sender)
                val processor = GenericMessageProcessor(client, handler)
                val genericMessage = decrypt(client, payload)
                val messageId = UUID.fromString(genericMessage.getMessageId())
                val msgBase = MessageBase(eventId, messageId, payload.convId, data.sender, from, payload.time)
                processor.process(msgBase, genericMessage)
                handler.onEvent(client, from, genericMessage)
            }
            "conversation.member-join" -> {
                Logger.debug("conversation.member-join: bot: %s", botId)

                // Check if this bot got added to the conversation
                val participants = data.userIds
                if (participants.remove(botId)) {
                    val systemMessage = getSystemMessage(eventId, payload)
                    systemMessage.conversation = client.getConversation()
                    systemMessage.type = "conversation.create" //hack the type
                    handler.onNewConversation(client, systemMessage)
                    return
                }

                // Check if we still have some prekeys available. Upload new prekeys if needed
                handler.validatePreKeys(client, participants.size)
                val systemMessage = getSystemMessage(eventId, payload)
                systemMessage.users = data.userIds
                handler.onMemberJoin(client, systemMessage)
            }
            "conversation.member-leave" -> {
                Logger.debug("conversation.member-leave: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                systemMessage.users = data.userIds

                // Check if this bot got removed from the conversation
                participants = data.userIds
                if (participants.remove(botId)) {
                    handler.onBotRemoved(botId, systemMessage)
                    return
                }
                if (!participants.isEmpty()) {
                    handler.onMemberLeave(client, systemMessage)
                }
            }
            "conversation.delete" -> {
                Logger.debug("conversation.delete: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)

                // Cleanup
                handler.onBotRemoved(botId, systemMessage)
            }
            "conversation.create" -> {
                Logger.debug("conversation.create: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                if (systemMessage.conversation.members != null) {
                    val self = Member()
                    self.id = botId
                    systemMessage.conversation.members.add(self)
                }
                handler.onNewConversation(client, systemMessage)
            }
            "conversation.rename" -> {
                Logger.debug("conversation.rename: bot: %s", botId)
                systemMessage = getSystemMessage(eventId, payload)
                handler.onConversationRename(client, systemMessage)
            }
            "user.connection" -> {
                val connection = payload.connection
                Logger.debug(
                    "user.connection: bot: %s, from: %s to: %s status: %s",
                    botId,
                    connection.from,
                    connection.to,
                    connection.status
                )
                val accepted = handler.onConnectRequest(client, connection.from, connection.to, connection.status)
                if (accepted) {
                    val conversation = Conversation()
                    conversation.id = connection.convId
                    systemMessage = SystemMessage()
                    systemMessage.id = eventId
                    systemMessage.from = connection.from
                    systemMessage.type = payload.type
                    systemMessage.conversation = conversation
                    handler.onNewConversation(client, systemMessage)
                }
            }
            else -> Logger.debug("Unknown event: %s", payload.type)
        }
    }

    private fun getSystemMessage(eventId: UUID?, payload: Payload?): SystemMessage? {
        val systemMessage = SystemMessage()
        systemMessage.id = eventId
        systemMessage.from = payload.from
        systemMessage.time = payload.time
        systemMessage.type = payload.type
        systemMessage.convId = payload.convId
        systemMessage.conversation = Conversation()
        systemMessage.conversation.id = payload.convId
        systemMessage.conversation.creator = payload.data.creator
        systemMessage.conversation.name = payload.data.name
        if (payload.data.members != null) systemMessage.conversation.members = payload.data.members.others
        return systemMessage
    }

    @Throws(CryptoException::class, InvalidProtocolBufferException::class)
    private fun decrypt(client: WireClient?, payload: Payload?): GenericMessage? {
        val from = payload.from
        val sender = payload.data.sender
        val cipher = payload.data.text
        val encoded = client.decrypt(from, sender, cipher)
        val decoded = Base64.getDecoder().decode(encoded)
        return GenericMessage.parseFrom(decoded)
    }
}
