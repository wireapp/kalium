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
package com.wire.kalium.backend

import com.waz.model.Messages
import com.wire.kalium.MessageHandlerBase
import com.wire.kalium.WireClient
import com.wire.kalium.models.*
import com.wire.kalium.tools.Logger
import java.util.*

class GenericMessageProcessor(private val client: WireClient, private val handler: MessageHandlerBase) {
    fun process(msgBase: MessageBase, generic: Messages.GenericMessage): Boolean {
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
                    msg.setExpireAfterMillis(ephemeral.expireAfterMillis)
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
                    msg.setReplacingMessageId(replacingMessageId)
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
                message.setContent(calling.content)
                handler.onCalling(client, message)
            }
            return true
        }
        if (generic.hasDeleted()) {
            val msg = DeletedTextMessage(msgBase)
            val delMsgId = UUID.fromString(generic.getDeleted().messageId)
            msg.setDeletedMessageId(delMsgId)
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

    private fun fromText(textMessage: TextMessage, text: Messages.Text): TextMessage? {
        textMessage.setText(text.getContent())
        if (text.hasQuote()) {
            val quotedMessageId = text.getQuote().quotedMessageId
            textMessage.setQuotedMessageId(UUID.fromString(quotedMessageId))
        }
        for (mention in text.getMentionsList()) textMessage.addMention(mention.userId, mention.start, mention.length)
        return textMessage
    }

    private fun handleAsset(msgBase: MessageBase, asset: Messages.Asset): Boolean {
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

    private fun handleConfirmation(confirmation: Messages.Confirmation, msg: ConfirmationMessage): Boolean {
        val firstMessageId = confirmation.getFirstMessageId()
        val type = confirmation.getType()
        msg.setConfirmationMessageId(UUID.fromString(firstMessageId))
        msg.setType(if (type.number == Messages.Confirmation.Type.DELIVERED_VALUE) ConfirmationMessage.Type.DELIVERED else ConfirmationMessage.Type.READ)
        handler.onConfirmation(client, msg)
        return true
    }

    private fun handleLinkPreview(text: Messages.Text, msg: LinkPreviewMessage): Boolean {
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

    private fun handleReaction(reaction: Messages.Reaction, msg: ReactionMessage): Boolean {
        if (reaction.hasEmoji()) {
            msg.setEmoji(reaction.getEmoji())
            msg.setReactionMessageId(UUID.fromString(reaction.getMessageId()))
            handler.onReaction(client, msg)
        }
        return true
    }
}
