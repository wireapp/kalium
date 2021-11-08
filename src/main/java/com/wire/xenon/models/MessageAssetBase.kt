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
package com.wire.xenon.models

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

@Deprecated("")
open class MessageAssetBase : MessageBase {
    // Remote data
    private var assetKey: String? = null
    private var assetToken: String? = null
    private var otrKey: ByteArray?
    private var sha256: ByteArray?

    // Origin
    private var mimeType: String? = null
    private var name: String? = null
    private var size: Long = 0

    constructor(
        eventId: UUID?,
        msgId: UUID?,
        convId: UUID?,
        clientId: String?,
        userId: UUID?,
        time: String?,
        assetKey: String?,
        assetToken: String?,
        otrKey: ByteArray?,
        mimeType: String?,
        size: Long,
        sha256: ByteArray?,
        name: String?
    ) : super(eventId, msgId, convId, clientId, userId, time) {
        this.assetKey = assetKey
        this.assetToken = assetToken
        this.otrKey = otrKey
        this.mimeType = mimeType
        this.size = size
        this.sha256 = sha256
        this.name = name
    }

    constructor(eventID: UUID?, msgId: UUID?, convId: UUID?, clientId: String?, userId: UUID?, time: String?) : super(
        eventID,
        msgId,
        convId,
        clientId,
        userId,
        time
    ) {
    }

    internal constructor(base: MessageAssetBase?) : super(
        base.eventId,
        base.messageId,
        base.conversationId,
        base.clientId,
        base.userId,
        base.time
    ) {
        assetKey = base.assetKey
        assetToken = base.assetToken
        otrKey = base.otrKey
        mimeType = base.mimeType
        size = base.size
        sha256 = base.sha256
        name = base.name
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun setSize(size: Long) {
        this.size = size
    }

    fun getSize(): Long {
        return size
    }

    fun getMimeType(): String? {
        return mimeType
    }

    fun setMimeType(mimeType: String?) {
        this.mimeType = mimeType
    }

    fun getAssetToken(): String? {
        return assetToken
    }

    fun setAssetToken(assetToken: String?) {
        this.assetToken = assetToken
    }

    fun setOtrKey(otrKey: ByteArray?) {
        this.otrKey = otrKey
    }

    fun getOtrKey(): ByteArray? {
        return otrKey
    }

    fun getAssetKey(): String? {
        return assetKey
    }

    fun setAssetKey(assetKey: String?) {
        this.assetKey = assetKey
    }

    fun setSha256(sha256: ByteArray?) {
        this.sha256 = sha256
    }

    fun getSha256(): ByteArray? {
        return sha256
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun fromRemote(remoteData: RemoteData?) {
        if (remoteData != null) {
            setAssetKey(remoteData.assetId)
            setAssetToken(if (remoteData.hasAssetToken()) remoteData.assetToken else null)
            setOtrKey(remoteData.otrKey.toByteArray())
            setSha256(remoteData.sha256.toByteArray())
        }
    }

    fun fromOrigin(original: Original?) {
        if (original != null) {
            setMimeType(original.mimeType)
            setSize(original.size)
            setName(if (original.hasName()) original.name else null)
        }
    }
}
