/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.messagecontent

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent

/** Stable, platform-neutral content that an NSE may use to evaluate a notification. */
public sealed interface NotificationContent {
    public val messageUid: String

    public data class Text(
        override val messageUid: String,
        public val value: String,
        public val quotedMessageId: String?,
        public val mentionsSelf: Boolean
    ) : NotificationContent

    public data class Asset(
        override val messageUid: String,
        public val name: String?,
        public val mimeType: String,
        public val sizeInBytes: Long
    ) : NotificationContent

    public data class Multipart(
        override val messageUid: String,
        public val text: String?,
        public val quotedMessageId: String?,
        public val attachments: List<Attachment>,
        public val mentionsSelf: Boolean
    ) : NotificationContent

    public data class Edit(
        override val messageUid: String,
        public val targetMessageId: String,
        public val replacementText: String?,
        public val attachments: List<Attachment>,
        public val mentionsSelf: Boolean
    ) : NotificationContent

    public data class Delete(
        override val messageUid: String,
        public val targetMessageId: String
    ) : NotificationContent

    public data class Reaction(
        override val messageUid: String,
        public val targetMessageId: String,
        public val emojiSet: Set<String>
    ) : NotificationContent

    public data class Calling(
        override val messageUid: String,
        public val payload: String,
        public val conversationId: ConversationId?
    ) : NotificationContent

    public data class Knock(
        override val messageUid: String,
        public val isPriority: Boolean
    ) : NotificationContent

    public data class Location(
        override val messageUid: String,
        public val latitude: Float,
        public val longitude: Float,
        public val name: String?
    ) : NotificationContent

    public data class Attachment(
        public val name: String?,
        public val mimeType: String,
        public val sizeInBytes: Long?
    )
}

/** A safe extraction outcome. Only [Candidate] may be rendered with content details. */
public sealed interface NotificationContentExtractionResult {
    public data class Candidate(
        public val content: NotificationContent,
        public val legalHoldStatus: LegalHoldStatus,
        public val expiresAfterMillis: Long?
    ) : NotificationContentExtractionResult

    public enum class LegalHoldStatus {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    public data class KnownNotNotifiable(public val messageUid: String) : NotificationContentExtractionResult

    public data class ExternalRequiresResolution(public val messageUid: String) : NotificationContentExtractionResult

    public data class Unsupported(public val messageUid: String) : NotificationContentExtractionResult
}

/** Pure extraction boundary. Notification policy and platform presentation remain outside it. */
public fun interface NotificationContentExtractor {
    public fun extract(content: DecodedProtobufContent): NotificationContentExtractionResult
}

public class NotificationContentExtractorImpl : NotificationContentExtractor {
    override fun extract(content: DecodedProtobufContent): NotificationContentExtractionResult =
        when (content.classification) {
            DecodedProtobufContent.Classification.EXTERNAL_INSTRUCTIONS ->
                NotificationContentExtractionResult.ExternalRequiresResolution(content.content.messageUid)

            DecodedProtobufContent.Classification.UNSUPPORTED ->
                NotificationContentExtractionResult.Unsupported(content.content.messageUid)

            DecodedProtobufContent.Classification.APPLICATION -> when (val protoContent = content.content) {
                is ProtoContent.ExternalMessageInstructions ->
                    NotificationContentExtractionResult.ExternalRequiresResolution(protoContent.messageUid)

                is ProtoContent.Readable -> extractReadable(protoContent)
            }
        }

    @Suppress("LongMethod")
    private fun extractReadable(content: ProtoContent.Readable): NotificationContentExtractionResult =
        when (val messageContent = content.messageContent) {
            is MessageContent.Text -> candidate(
                NotificationContent.Text(
                    messageUid = content.messageUid,
                    value = messageContent.value,
                    quotedMessageId = messageContent.quotedMessageReference?.quotedMessageId,
                    mentionsSelf = messageContent.mentions.any { it.isSelfMention }
                ),
                content
            )

            is MessageContent.Asset -> candidate(
                NotificationContent.Asset(
                    messageUid = content.messageUid,
                    name = messageContent.value.name,
                    mimeType = messageContent.value.mimeType,
                    sizeInBytes = messageContent.value.sizeInBytes
                ),
                content
            )

            is MessageContent.Multipart -> candidate(
                NotificationContent.Multipart(
                    messageUid = content.messageUid,
                    text = messageContent.value,
                    quotedMessageId = messageContent.quotedMessageReference?.quotedMessageId,
                    attachments = messageContent.attachments.map(::mapAttachment),
                    mentionsSelf = messageContent.mentions.any { it.isSelfMention }
                ),
                content
            )

            is MessageContent.TextEdited -> candidate(
                NotificationContent.Edit(
                    messageUid = content.messageUid,
                    targetMessageId = messageContent.editMessageId,
                    replacementText = messageContent.newContent,
                    attachments = emptyList(),
                    mentionsSelf = messageContent.newMentions.any { it.isSelfMention }
                ),
                content
            )

            is MessageContent.CompositeEdited -> candidate(
                NotificationContent.Edit(
                    messageUid = content.messageUid,
                    targetMessageId = messageContent.editMessageId,
                    replacementText = messageContent.newTextContent?.value,
                    attachments = emptyList(),
                    mentionsSelf = messageContent.newTextContent?.mentions?.any { it.isSelfMention } == true
                ),
                content
            )

            is MessageContent.MultipartEdited -> candidate(
                NotificationContent.Edit(
                    messageUid = content.messageUid,
                    targetMessageId = messageContent.editMessageId,
                    replacementText = messageContent.newTextContent,
                    attachments = messageContent.newAttachments.map(::mapAttachment),
                    mentionsSelf = messageContent.newMentions.any { it.isSelfMention }
                ),
                content
            )

            is MessageContent.DeleteMessage -> candidate(
                NotificationContent.Delete(content.messageUid, messageContent.messageId),
                content
            )

            is MessageContent.Reaction -> candidate(
                NotificationContent.Reaction(
                    messageUid = content.messageUid,
                    targetMessageId = messageContent.messageId,
                    emojiSet = messageContent.emojiSet.toSet()
                ),
                content
            )

            is MessageContent.Calling -> candidate(
                NotificationContent.Calling(content.messageUid, messageContent.value, messageContent.conversationId),
                content
            )

            is MessageContent.Knock -> candidate(
                NotificationContent.Knock(
                    messageUid = content.messageUid,
                    isPriority = messageContent.hotKnock
                ),
                content
            )

            is MessageContent.Location -> candidate(
                NotificationContent.Location(
                    messageUid = content.messageUid,
                    latitude = messageContent.latitude,
                    longitude = messageContent.longitude,
                    name = messageContent.name
                ),
                content
            )

            is MessageContent.Unknown -> NotificationContentExtractionResult.Unsupported(content.messageUid)

            else -> NotificationContentExtractionResult.KnownNotNotifiable(content.messageUid)
        }

    private fun mapAttachment(attachment: MessageAttachment): NotificationContent.Attachment = when (attachment) {
        is AssetContent -> NotificationContent.Attachment(
            name = attachment.name,
            mimeType = attachment.mimeType,
            sizeInBytes = attachment.sizeInBytes
        )

        is CellAssetContent -> NotificationContent.Attachment(
            name = attachment.assetPath,
            mimeType = attachment.mimeType,
            sizeInBytes = attachment.assetSize
        )
    }

    private fun candidate(
        notificationContent: NotificationContent,
        protoContent: ProtoContent.Readable
    ): NotificationContentExtractionResult = NotificationContentExtractionResult.Candidate(
        content = notificationContent,
        legalHoldStatus = when (protoContent.legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED -> NotificationContentExtractionResult.LegalHoldStatus.ENABLED
            Conversation.LegalHoldStatus.DISABLED -> NotificationContentExtractionResult.LegalHoldStatus.DISABLED
            Conversation.LegalHoldStatus.DEGRADED,
            Conversation.LegalHoldStatus.UNKNOWN -> NotificationContentExtractionResult.LegalHoldStatus.UNKNOWN
        },
        expiresAfterMillis = protoContent.expiresAfterMillis
    )
}
