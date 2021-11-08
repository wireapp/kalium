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
import java.util.Date
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger

object Logger {
    fun getLOGGER(): Logger? {
        return LOGGER
    }

    private val LOGGER = Logger.getLogger("com.wire.bots.logger")
    private val errorCount: AtomicInteger? = AtomicInteger()
    private val warningCount: AtomicInteger? = AtomicInteger()
    fun debug(msg: String?) {
        LOGGER.fine(msg)
    }

    fun debug(format: String?, vararg args: Any?) {
        LOGGER.fine(String.format(format, *args))
    }

    fun info(msg: String?) {
        LOGGER.info(msg)
    }

    fun info(format: String?, vararg args: Any?) {
        LOGGER.info(String.format(format, *args))
    }

    fun error(msg: String?) {
        errorCount.incrementAndGet()
        LOGGER.severe(msg)
    }

    fun error(format: String?, vararg args: Any?) {
        // check if there's an exception in the objects
        for (arg in args) {
            if (arg is Throwable) {
                // if so log it as exception instead of a common format
                exception(arg as Throwable?, format, *args)
                // break as counter and logger is handled in the exception
                return
            }
        }
        errorCount.incrementAndGet()
        LOGGER.severe(String.format(format, *args))
    }

    @Deprecated("")
    fun exception(message: String?, throwable: Throwable?, vararg args: Any?) {
        errorCount.incrementAndGet()
        LOGGER.log(Level.SEVERE, String.format(message, *args), throwable)
    }

    fun exception(throwable: Throwable?, message: String?, vararg args: Any?) {
        errorCount.incrementAndGet()
        LOGGER.log(Level.SEVERE, String.format(message, *args), throwable)
    }

    fun warning(msg: String?) {
        warningCount.incrementAndGet()
        LOGGER.warning(msg)
    }

    fun warning(format: String?, vararg args: Any?) {
        warningCount.incrementAndGet()
        LOGGER.warning(String.format(format, *args))
    }

    fun getErrorCount(): Int {
        return errorCount.get()
    }

    fun getWarningCount(): Int {
        return warningCount.get()
    }

    fun getLevel(): Level? {
        return LOGGER.level
    }

    internal class BotFormatter : Formatter() {
        override fun format(record: LogRecord?): String? {
            val builder = StringBuilder()
            builder.append(df.format(Date(record.getMillis()))).append(" - ")
            // builder.append("[").append(record.getSourceClassName()).append(".");
            // builder.append(record.getSourceMethodName()).append("] - ");
            builder.append("[").append(record.getLevel()).append("] - ")
            builder.append(formatMessage(record))
            builder.append("\n")
            return builder.toString()
        }

        override fun getHead(h: Handler?): String? {
            return super.getHead(h)
        }

        override fun getTail(h: Handler?): String? {
            return super.getTail(h)
        }

        companion object {
            private val df: DateFormat? = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        }
    }

    init {
        for (handler in LOGGER.handlers) {
            com.wire.xenon.tools.handler.setFormatter(BotFormatter())
        }
    }
}
