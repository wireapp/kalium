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
import org.junit.jupiter.api.Assertions
import java.lang.Exception
import java.util.ArrayList

/**
 * Thread safe class for postings into this conversation
 */
interface WireClient : Closeable {
    /**
     * Post a generic message into conversation
     *
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun send(message: IGeneric?)

    /**
     * @param message generic message (Text, Image, File, Reply, Mention, ...)
     * @param userId  ignore all other participants except this user
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun send(message: IGeneric?, userId: UUID?)

    /**
     * This method downloads asset from the Backend.
     *
     * @param assetKey        Unique asset identifier (UUID)
     * @param assetToken      Asset token (null in case of public assets)
     * @param sha256Challenge SHA256 hash code for this asset
     * @param otrKey          Encryption key to be used to decrypt the data
     * @return Decrypted asset data
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun downloadAsset(assetKey: String?, assetToken: String?, sha256Challenge: ByteArray?, otrKey: ByteArray?): ByteArray?

    /**
     * @return Bot ID as UUID
     */
    open fun getId(): UUID?

    /**
     * Fetch the bot's own user profile information. A bot's profile has the following attributes:
     *
     *
     * id (String): The bot's user ID.
     * name (String): The bot's name.
     * accent_id (Number): The bot's accent colour.
     * assets (Array): The bot's public profile assets (e.g. images).
     *
     * @return
     */
    @Throws(HttpException::class)
    open fun getSelf(): User?

    /**
     * @return Conversation ID as UUID
     */
    open fun getConversationId(): UUID?

    /**
     * @return Device ID as returned by the Wire Backend
     */
    open fun getDeviceId(): String?

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userIds User IDs (UUID) that are being requested
     * @return Collection of user profiles (name, accent colour,...)
     * @throws HttpException
     */
    @Throws(HttpException::class)
    open fun getUsers(userIds: MutableCollection<UUID?>?): MutableCollection<User?>?

    /**
     * Fetch users' profiles from the Backend
     *
     * @param userId User ID (UUID) that are being requested
     * @return User profile (name, accent colour,...)
     * @throws HttpException
     */
    @Throws(HttpException::class)
    open fun getUser(userId: UUID?): User?

    /**
     * Fetch conversation details from the Backend
     *
     * @return Conversation details including Conversation ID, Conversation name, List of participants
     * @throws IOException
     */
    @Throws(IOException::class)
    open fun getConversation(): Conversation?

    /**
     * Bots cannot send/receive/accept connect requests. This method can be used when
     * running the sdk as a regular user and you need to
     * accept/reject a connect request.
     *
     * @param user User ID as UUID
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun acceptConnection(user: UUID?)

    /**
     * Decrypt cipher either using existing session or it creates new session from this cipher and decrypts
     *
     * @param userId   Sender's User id
     * @param clientId Sender's Client id
     * @param cypher   Encrypted, Base64 encoded string
     * @return Base64 encoded decrypted text
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    open fun decrypt(userId: UUID?, clientId: String?, cypher: String?): String?

    /**
     * Invoked by the sdk. Called once when the conversation is created
     *
     * @return Last prekey
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    open fun newLastPreKey(): PreKey?

    /**
     * Invoked by the sdk. Called once when the conversation is created and then occasionally when number of available
     * keys drops too low
     *
     * @param from  Starting offset
     * @param count Number of keys to generate
     * @return List of prekeys
     * @throws CryptoException
     */
    @Throws(CryptoException::class)
    open fun newPreKeys(from: Int, count: Int): ArrayList<PreKey?>?

    /**
     * Uploads previously generated prekeys to BE
     *
     * @param preKeys Pre keys to be uploaded
     * @throws IOException
     */
    @Throws(IOException::class)
    open fun uploadPreKeys(preKeys: ArrayList<PreKey?>?)

    /**
     * Returns the list of available prekeys.
     * If the number is too low (less than 8) you should generate new prekeys and upload them to BE
     *
     * @return List of available prekeys' ids
     */
    open fun getAvailablePrekeys(): ArrayList<Int?>?

    /**
     * Checks if CryptoBox is closed
     *
     * @return True if crypto box is closed
     */
    open fun isClosed(): Boolean

    /**
     * Download publicly available profile picture for the given asset key. This asset is not encrypted
     *
     * @param assetKey Asset key
     * @return Profile picture binary data
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun downloadProfilePicture(assetKey: String?): ByteArray?

    /**
     * Uploads assert to backend. This method is used in conjunction with sendPicture(IGeneric)
     *
     * @param asset Asset to be uploaded
     * @return Assert Key and Asset token in case of private assets
     * @throws Exception
     */
    @Throws(Exception::class)
    open fun uploadAsset(asset: IAsset?): AssetKey?
    @Throws(HttpException::class)
    open fun getTeam(): UUID?
    @Throws(HttpException::class)
    open fun createConversation(name: String?, teamId: UUID?, users: MutableList<UUID?>?): Conversation?
    @Throws(HttpException::class)
    open fun createOne2One(teamId: UUID?, userId: UUID?): Conversation?
    @Throws(HttpException::class)
    open fun leaveConversation(userId: UUID?)
    @Throws(HttpException::class)
    open fun addParticipants(vararg userIds: UUID?): User?
    @Throws(HttpException::class)
    open fun addService(serviceId: UUID?, providerId: UUID?): User?
    @Throws(HttpException::class)
    open fun deleteConversation(teamId: UUID?): Boolean
}
