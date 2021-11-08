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
import com.wire.xenon.backend.models.User
import com.wire.xenon.models.otr.PreKey
import com.wire.xenon.tools.Logger
import com.wire.xenon.tools.Util
import org.junit.jupiter.api.Assertions
import java.lang.Exception
import java.util.ArrayList
import java.util.Arrays

abstract class WireClientBase protected constructor(
    protected val api: WireAPI?,
    protected val crypto: Crypto?,
    protected val state: NewBot?
) : WireClient {
    protected var devices: Devices? = null
    @Throws(Exception::class)
    override fun send(message: IGeneric?) {
        postGenericMessage(message)
    }

    @Throws(Exception::class)
    override fun send(message: IGeneric?, userId: UUID?) {
        postGenericMessage(message, userId)
    }

    override fun getId(): UUID? {
        return state.id
    }

    override fun getDeviceId(): String? {
        return state.client
    }

    override fun getConversationId(): UUID? {
        return state.conversation.id
    }

    @Throws(IOException::class)
    override fun close() {
        crypto.close()
    }

    override fun isClosed(): Boolean {
        return crypto.isClosed()
    }

    /**
     * Encrypt whole message for participants in the conversation.
     * Implements the fallback for the 412 error code and missing
     * devices.
     *
     * @param generic generic message to be sent
     * @throws Exception CryptoBox exception
     */
    @Throws(Exception::class)
    protected fun postGenericMessage(generic: IGeneric?) {
        val content = generic.createGenericMsg().toByteArray()

        // Try to encrypt the msg for those devices that we have the session already
        var encrypt = encrypt(content, getAllDevices())
        val msg = OtrMessage(deviceId, encrypt)
        var res = api.sendMessage(msg, false)
        if (!res.hasMissing()) {
            // Fetch preKeys for the missing devices from the Backend
            val preKeys = api.getPreKeys(res.missing)
            Logger.debug("Fetched %d preKeys for %d devices. Bot: %s", preKeys.count(), res.size(), id)

            // Encrypt msg for those devices that were missing. This time using preKeys
            encrypt = crypto.encrypt(preKeys, content)
            msg.add(encrypt)

            // reset devices so they could be pulled next time
            devices = null
            res = api.sendMessage(msg, true)
            if (!res.hasMissing()) {
                Logger.error(
                    String.format(
                        "Failed to send otr message to %d devices. Bot: %s",
                        res.size(),
                        id
                    )
                )
            }
        }
    }

    @Throws(Exception::class)
    protected fun postGenericMessage(generic: IGeneric?, userId: UUID?) {
        // Try to encrypt the msg for those devices that we have the session already
        val all = getAllDevices()
        val missing = Missing()
        for (u in all.toUserIds()) {
            if (userId == u) {
                val clients = all.toClients(u)
                missing.add(u, clients)
            }
        }
        val content = generic.createGenericMsg().toByteArray()
        var encrypt = encrypt(content, missing)
        val msg = OtrMessage(deviceId, encrypt)
        var res = api.sendPartialMessage(msg, userId)
        if (!res.hasMissing()) {
            // Fetch preKeys for the missing devices from the Backend
            val preKeys = api.getPreKeys(res.missing)
            Logger.debug("Fetched %d preKeys for %d devices. Bot: %s", preKeys.count(), res.size(), id)

            // Encrypt msg for those devices that were missing. This time using preKeys
            encrypt = crypto.encrypt(preKeys, content)
            msg.add(encrypt)

            // reset devices so they could be pulled next time
            devices = null
            res = api.sendMessage(msg, true)
            if (!res.hasMissing()) {
                Logger.error(
                    String.format(
                        "Failed to send otr message to %d devices. Bot: %s",
                        res.size(),
                        id
                    )
                )
            }
        }
    }

    override fun getSelf(): User? {
        return api.getSelf()
    }

    override fun getUsers(userIds: MutableCollection<UUID?>?): MutableCollection<User?>? {
        return api.getUsers(userIds)
    }

    override fun getUser(userId: UUID?): User? {
        val users = api.getUsers(setOf(userId))
        return users.iterator().next()
    }

    override fun getConversation(): Conversation? {
        return api.getConversation()
    }

    @Throws(Exception::class)
    override fun acceptConnection(user: UUID?) {
        api.acceptConnection(user)
    }

    @Throws(IOException::class)
    override fun uploadPreKeys(preKeys: ArrayList<PreKey?>?) {
        api.uploadPreKeys(preKeys)
    }

    override fun getAvailablePrekeys(): ArrayList<Int?>? {
        return api.getAvailablePrekeys(state.client)
    }

    @Throws(HttpException::class)
    override fun downloadProfilePicture(assetKey: String?): ByteArray? {
        return api.downloadAsset(assetKey, null)
    }

    @Throws(Exception::class)
    override fun uploadAsset(asset: IAsset?): AssetKey? {
        return api.uploadAsset(asset)
    }

    @Throws(CryptoException::class)
    fun encrypt(content: ByteArray?, missing: Missing?): Recipients? {
        return crypto.encrypt(missing, content)
    }

    @Throws(CryptoException::class)
    override fun decrypt(userId: UUID?, clientId: String?, cypher: String?): String? {
        return crypto.decrypt(userId, clientId, cypher)
    }

    @Throws(CryptoException::class)
    override fun newLastPreKey(): PreKey? {
        return crypto.newLastPreKey()
    }

    @Throws(CryptoException::class)
    override fun newPreKeys(from: Int, count: Int): ArrayList<PreKey?>? {
        return crypto.newPreKeys(from, count)
    }

    @Throws(Exception::class)
    override fun downloadAsset(assetId: String?, assetToken: String?, sha256Challenge: ByteArray?, otrKey: ByteArray?): ByteArray? {
        val cipher = api.downloadAsset(assetId, assetToken)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cipher)
        if (!Arrays.equals(sha256, sha256Challenge)) throw Exception("Failed sha256 check")
        return Util.decrypt(otrKey, cipher)
    }

    @Throws(HttpException::class)
    override fun getTeam(): UUID? {
        return api.getTeam()
    }

    @Throws(HttpException::class)
    override fun createConversation(name: String?, teamId: UUID?, users: MutableList<UUID?>?): Conversation? {
        return api.createConversation(name, teamId, users)
    }

    @Throws(HttpException::class)
    override fun createOne2One(teamId: UUID?, userId: UUID?): Conversation? {
        return api.createOne2One(teamId, userId)
    }

    @Throws(HttpException::class)
    override fun leaveConversation(userId: UUID?) {
        api.leaveConversation(userId)
    }

    @Throws(HttpException::class)
    override fun addParticipants(vararg userIds: UUID?): User? {
        return api.addParticipants(*userIds)
    }

    @Throws(HttpException::class)
    override fun addService(serviceId: UUID?, providerId: UUID?): User? {
        return api.addService(serviceId, providerId)
    }

    @Throws(HttpException::class)
    override fun deleteConversation(teamId: UUID?): Boolean {
        return api.deleteConversation(teamId)
    }

    @Throws(HttpException::class)
    private fun getAllDevices(): Missing? {
        return getDevices().missing
    }

    /**
     * This method will send an empty message to BE and collect the list of missing client ids
     * When empty message is sent the Backend will respond with error 412 and a list of missing clients.
     *
     * @return List of all participants in this conversation and their clientIds
     */
    @Throws(HttpException::class)
    private fun getDevices(): Devices? {
        if (devices == null || devices.hasMissing()) {
            val deviceId = deviceId
            val msg = OtrMessage(deviceId, Recipients())
            devices = api.sendMessage(msg)
        }
        return if (devices != null) devices else Devices()
    }
}
