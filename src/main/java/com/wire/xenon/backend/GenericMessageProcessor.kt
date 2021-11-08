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
package com.wire.xenon.backend

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
import com.wire.xenon.tools.Logger
import org.junit.jupiter.api.Assertions

class GenericMessageProcessor(private val client: WireClient?, private val handler: MessageHandlerBase?) {
    fun process(msgBase: MessageBase?, generic: GenericMessage?): Boolean {
        Logger.debug("proto: { %s }", generic)

        // Text
        if (generic.hasText()) {
            val text = generic.getText()
            if (!text.linkPreviewList.isEmpty()) {
                return handleLinkPreview(text, LinkPreviewMessage(msgBase))
            }
            if (text.hasContent()) {
                val msg = fromText(TextMessage(msgBase), text)
                handler.onText(client, msg)
                return true
            }
        }

        // Ephemeral messages
        if (generic.hasEphemeral()) {
            val ephemeral = generic.getEphemeral()
            if (ephemeral.hasText()) {
                val text = ephemeral.text
                if (text.hasContent()) {
                    val msg = EphemeralTextMessage(msgBase)
                    fromText(msg, text)
                    msg.expireAfterMillis = ephemeral.expireAfterMillis
                    handler.onText(client, msg)
                    return true
                }
            }
            if (ephemeral.hasAsset()) {
                return handleAsset(msgBase, ephemeral.asset)
            }
        }

        // Edit message
        if (generic.hasEdited()) {
            val edited = generic.getEdited()
            if (edited.hasText()) {
                val text = edited.text
                if (text.hasContent()) {
                    val msg = EditedTextMessage(msgBase)
                    fromText(msg, text)
                    val replacingMessageId = UUID.fromString(edited.replacingMessageId)
                    msg.replacingMessageId = replacingMessageId
                    handler.onEditText(client, msg)
                    return true
                }
            }
        }
        if (generic.hasConfirmation()) {
            val confirmation = generic.getConfirmation()
            val msg = ConfirmationMessage(msgBase)
            return handleConfirmation(confirmation, msg)
        }
        if (generic.hasCalling()) {
            val calling = generic.getCalling()
            if (calling.hasContent()) {
                val message = CallingMessage(msgBase)
                message.content = calling.content
                handler.onCalling(client, message)
            }
            return true
        }
        if (generic.hasDeleted()) {
            val msg = DeletedTextMessage(msgBase)
            val delMsgId = UUID.fromString(generic.getDeleted().messageId)
            msg.deletedMessageId = delMsgId
            handler.onDelete(client, msg)
            return true
        }
        if (generic.hasReaction()) {
            val reaction = generic.getReaction()
            val msg = ReactionMessage(msgBase)
            return handleReaction(reaction, msg)
        }
        if (generic.hasKnock()) {
            val msg = PingMessage(msgBase)
            handler.onPing(client, msg)
            return true
        }
        return if (generic.hasAsset()) {
            handleAsset(msgBase, generic.getAsset())
        } else false
    }

    private fun fromText(textMessage: TextMessage?, text: Messages.Text?): TextMessage? {
        textMessage.setText(text.getContent())
        if (text.hasQuote()) {
            val quotedMessageId = text.getQuote().quotedMessageId
            textMessage.setQuotedMessageId(UUID.fromString(quotedMessageId))
        }
        for (mention in text.getMentionsList()) textMessage.addMention(mention.userId, mention.start, mention.length)
        return textMessage
    }

    private fun handleAsset(msgBase: MessageBase?, asset: Messages.Asset?): Boolean {
        if (asset.hasOriginal()) {
            val original = asset.getOriginal()
            if (original.hasImage()) {
                handler.onPhotoPreview(client, PhotoPreviewMessage(msgBase, original))
            } else if (original.hasAudio()) {
                handler.onAudioPreview(client, AudioPreviewMessage(msgBase, original))
            } else if (original.hasVideo()) {
                handler.onVideoPreview(client, VideoPreviewMessage(msgBase, original))
            } else {
                handler.onFilePreview(client, FilePreviewMessage(msgBase, original))
            }
        }
        if (asset.hasUploaded()) {
            handler.onAssetData(client, RemoteMessage(msgBase, asset.getUploaded()))
        }
        return true
    }

    private fun handleConfirmation(confirmation: Confirmation?, msg: ConfirmationMessage?): Boolean {
        val firstMessageId = confirmation.getFirstMessageId()
        val type = confirmation.getType()
        msg.setConfirmationMessageId(UUID.fromString(firstMessageId))
        msg.setType(if (type.number == Confirmation.Type.DELIVERED_VALUE) ConfirmationMessage.Type.DELIVERED else ConfirmationMessage.Type.READ)
        handler.onConfirmation(client, msg)
        return true
    }

    private fun handleLinkPreview(text: Messages.Text?, msg: LinkPreviewMessage?): Boolean {
        for (link in text.getLinkPreviewList()) {
            if (text.hasContent()) {
                val image = link.image
                msg.fromOrigin(image.original)
                msg.fromRemote(image.uploaded)
                val imageMetaData = image.original.image
                msg.setHeight(imageMetaData.height)
                msg.setWidth(imageMetaData.width)
                msg.setSummary(link.summary)
                msg.setTitle(link.title)
                msg.setUrl(link.url)
                msg.setUrlOffset(link.urlOffset)
                msg.setText(text.getContent())
                handler.onLinkPreview(client, msg)
            }
        }
        return true
    }

    private fun handleReaction(reaction: Messages.Reaction?, msg: ReactionMessage?): Boolean {
        if (reaction.hasEmoji()) {
            msg.setEmoji(reaction.getEmoji())
            msg.setReactionMessageId(UUID.fromString(reaction.getMessageId()))
            handler.onReaction(client, msg)
        }
        return true
    }
}
