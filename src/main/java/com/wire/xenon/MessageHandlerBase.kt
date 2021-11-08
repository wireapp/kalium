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
import com.wire.xenon.tools.Logger
import org.junit.jupiter.api.Assertions
import java.lang.Exception

abstract class MessageHandlerBase {
    /**
     * @param newBot       Initialization object for new Bot instance
     * -  id          : The unique user ID for the bot.
     * -  client      : The client ID for the bot.
     * -  origin      : The profile of the user who requested the bot, as it is returned from GET /bot/users.
     * -  conversation: The convId as seen by the bot and as returned from GET /bot/convId.
     * -  token       : The bearer token that the bot must use on inbound requests.
     * -  locale      : The preferred locale for the bot to use, in form of an IETF language tag.
     * @param serviceToken Service token obtained from the Wire BE when the service was created
     * @return If TRUE is returned new bot instance is created for this conversation
     * If FALSE is returned this service declines to create new bot instance for this conversation
     */
    fun onNewBot(newBot: NewBot?, serviceToken: String?): Boolean {
        return true
    }

    /**
     * This callback is invoked by the framework when the bot is added into a conversation
     *
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message SystemMessage object. message.conversation is never null
     */
    fun onNewConversation(client: WireClient?, message: SystemMessage?) {}

    /**
     * This callback is invoked by the framework every time connection request is received
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param from   UserId of the connection request source user
     * @param to     UserId of the connection request destination user
     * @param status Relation status of the connection request
     * @return TRUE if connection was accepted
     */
    fun onConnectRequest(client: WireClient?, from: UUID?, to: UUID?, status: String?): Boolean {
        // Bot received connect request and we want to accept it immediately
        if (status == "pending") {
            return try {
                client.acceptConnection(to)
                true
            } catch (e: Exception) {
                Logger.error("MessageHandlerBase:onConnectRequest: %s", e)
                false
            }
        }
        // Connect request sent by the bot got accepted
        return if (status == "accepted") {
            true
        } else false
    }

    /**
     * This callback is invoked by the framework every time new participant joins this conversation
     *
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message System message object with message.users as List of UserIds that just joined this conversation
     */
    fun onMemberJoin(client: WireClient?, message: SystemMessage?) {}

    /**
     * @param client  Thread safe wire client that can be used to post back to this conversation
     * @param message System message object with message.users as List of UserIds that just joined this conversation
     */
    fun onMemberLeave(client: WireClient?, message: SystemMessage?) {}

    /**
     * This callback is called when this bot gets removed from the conversation
     *
     * @param botId Id of the Bot that got removed
     * @param msg   System message
     */
    fun onBotRemoved(botId: UUID?, msg: SystemMessage?) {}

    /**
     * @param newBot
     * @return Bot name that will be used for this conversation. If NULL is returned the Default Bot Name will be used
     */
    fun getName(newBot: NewBot?): String? {
        return null
    }

    /**
     * @return Bot's Accent Colour index (from [1 - 7]) that will be used for this conversation. If 0 is returned the
     * default one will be used
     */
    fun getAccentColour(): Int {
        return 0
    }

    /**
     * @return Asset key for the small profile picture. If NULL is returned the default key will be used
     */
    fun getSmallProfilePicture(): String? {
        return null
    }

    /**
     * @return Asset key for the big profile picture. If NULL is returned the default key will be used
     */
    fun getBigProfilePicture(): String? {
        return null
    }

    /**
     * This method is called when a text is posted into the conversation
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    Message containing text
     */
    fun onText(client: WireClient?, msg: TextMessage?) {}

    /**
     * This is generic method that is called every time something is posted to this conversation.
     *
     * @param client         Thread safe wire client that can be used to post back to this conversation
     * @param userId         User Id for the sender
     * @param genericMessage Generic message as it comes from the BE
     */
    fun onEvent(client: WireClient?, userId: UUID?, genericMessage: GenericMessage?) {}

    /**
     * Called when user edits previously sent message
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    New Message containing replacing messageId
     */
    fun onEditText(client: WireClient?, msg: EditedTextMessage?) {}
    fun onCalling(client: WireClient?, msg: CallingMessage?) {}
    fun onConversationRename(client: WireClient?, systemMessage: SystemMessage?) {}
    fun onDelete(client: WireClient?, msg: DeletedTextMessage?) {}
    fun onReaction(client: WireClient?, msg: ReactionMessage?) {}
    fun onNewTeamMember(userClient: WireClient?, userId: UUID?) {}
    fun onUserUpdate(id: UUID?, userId: UUID?) {}
    open fun onLinkPreview(client: WireClient?, msg: LinkPreviewMessage?) {}
    fun onPing(client: WireClient?, msg: PingMessage?) {}

    /**
     * This method is called when ephemeral text is posted into the conversation
     *
     * @param client Thread safe wire client that can be used to post back to this conversation
     * @param msg    Message containing text and expiration time
     */
    fun onText(client: WireClient?, msg: EphemeralTextMessage?) {}
    fun onConfirmation(client: WireClient?, msg: ConfirmationMessage?) {}
    fun validatePreKeys(client: WireClient?, size: Int) {
        try {
            val minAvailable = 8 * size
            if (minAvailable > 0) {
                val availablePrekeys = client!!.getAvailablePrekeys()
                availablePrekeys!!.remove(65535) //remove the last prekey
                if (!availablePrekeys.isEmpty() && availablePrekeys.size < minAvailable) {
                    val lastKeyOffset = Collections.max(availablePrekeys!!)
                    val keys = client.newPreKeys(lastKeyOffset!! + 1, minAvailable)
                    client.uploadPreKeys(keys)
                    Logger.info("Uploaded " + keys.size + " prekeys")
                }
            }
        } catch (e: Exception) {
            Logger.error("validatePreKeys: bot: %s %s", client.getId(), e)
        }
    }

    fun onPhotoPreview(client: WireClient?, msg: PhotoPreviewMessage?) {}
    fun onAssetData(client: WireClient?, msg: RemoteMessage?) {}
    fun onFilePreview(client: WireClient?, msg: FilePreviewMessage?) {}
    open fun onAudioPreview(client: WireClient?, msg: AudioPreviewMessage?) {}
    fun onVideoPreview(client: WireClient?, msg: VideoPreviewMessage?) {}
}
