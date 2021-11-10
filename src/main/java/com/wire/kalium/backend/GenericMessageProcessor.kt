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
import com.wire.kalium.MessageHandler
import com.wire.kalium.WireClient
import com.wire.kalium.models.*
import com.wire.kalium.tools.Logger
import java.util.*

class GenericMessageProcessor(private val client: WireClient, private val handler: MessageHandler) {

    fun process(msgBase: MessageBase, generic: Messages.GenericMessage): Boolean {
        Logger.debug("proto: { %s }", generic)

        // Text
        if (generic.hasText()) {
            if (generic.text.linkPreviewList.isNotEmpty()) {
                // FIXME: Fix this when we have LinkPreviews Again
//                return handleLinkPreview(text, LinkPreviewMessage(msgBase))
                return true
            }
            if (generic.text.hasContent()) {
                val msg = TextMessage(generic.text, msgBase)
                handler.onText(client, msg)
                return true
            }
        }

        // Ephemeral messages
        if (generic.hasEphemeral()) {
            val ephemeral = generic.ephemeral
            if (ephemeral.hasText()) {
                val textMessage = TextMessage(generic.text, msgBase)
                if (textMessage.text != null) {
                    val ephemeralMessage = EphemeralTextMessage(ephemeral.expireAfterMillis, textMessage)
                    handler.onText(client, ephemeralMessage)
                    return true
                }
            }
            if (ephemeral.hasAsset()) {
                return handleAsset(msgBase, ephemeral.asset)
            }
        }

        // Edit message
        if (generic.hasEdited()) {
            val edited = generic.edited
            if (edited.hasText()) {
                val textMessage = TextMessage(edited.text, msgBase)
                if (textMessage.text != null) {
                    val replacingMessageId = UUID.fromString(edited.replacingMessageId)
                    val msg = EditedTextMessage(replacingMessageId, textMessage)
                    handler.onEditText(client, msg)
                    return true
                }
            }
        }

        // Confirmation Message
        if (generic.hasConfirmation()) {
            val confirmation = generic.confirmation

            val confirmationType = if (confirmation.type.number == Messages.Confirmation.Type.DELIVERED_VALUE) {
                ConfirmationMessage.Type.DELIVERED
            } else {
                ConfirmationMessage.Type.READ
            }

            val msg = ConfirmationMessage(UUID.fromString(confirmation.firstMessageId), confirmationType, msgBase)
            handler.onConfirmation(client, msg)
            return true
        }

        // Calling Message
        if (generic.hasCalling()) {
            val calling = generic.calling
            if (calling.hasContent()) {
                val message = CallingMessage(calling.content, msgBase)
                handler.onCalling(client, message)
            }
            return true
        }

        // Delete Message
        if (generic.hasDeleted()) {
            val msg = DeletedTextMessage(UUID.fromString(generic.deleted.messageId), msgBase)
            handler.onDelete(client, msg)
            return true
        }

        // Reaction Message
        if (generic.hasReaction()) {
            val reaction = generic.reaction
            if (reaction.hasEmoji()) {
                val msg = ReactionMessage(reaction.emoji, UUID.fromString(reaction.messageId), msgBase)
                handler.onReaction(client, msg)
                return true
            }
        }

        // Knock Message
        if (generic.hasKnock()) {
            val msg = PingMessage(msgBase)
            handler.onPing(client, msg)
            return true
        }

        // Asset Message
        return if (generic.hasAsset()) {
            handleAsset(msgBase, generic.asset)
        } else false
    }

    private fun handleAsset(msgBase: MessageBase, asset: Messages.Asset): Boolean {
        if (asset.hasOriginal()) {
            val original = asset.original
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

//    private fun handleLinkPreview(text: Messages.Text, msg: LinkPreviewMessage): Boolean {
//        for (link in text.getLinkPreviewList()) {
//            if (text.hasContent()) {
//                val image = link.image
//                msg.fromOrigin(image.original)
//                msg.fromRemote(image.uploaded)
//                val imageMetaData = image.original.image
//                msg.setHeight(imageMetaData.height)
//                msg.setWidth(imageMetaData.width)
//                msg.setSummary(link.summary)
//                msg.setTitle(link.title)
//                msg.setUrl(link.url)
//                msg.setUrlOffset(link.urlOffset)
//                msg.setText(text.getContent())
//                handler.onLinkPreview(client, msg)
//            }
//        }
//        return true
//    }
}
