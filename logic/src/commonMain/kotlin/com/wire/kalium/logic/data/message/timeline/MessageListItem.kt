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

package com.wire.kalium.logic.data.message.timeline

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import kotlin.time.Instant

/** Rendering data for one timeline bubble. Resolve actions through the existing message/asset APIs. */
public data class MessageListItem(
    public val id: String,
    public val conversationId: ConversationId,
    public val date: Instant,
    public val sender: MessageListUser,
    public val senderClientId: String?,
    public val status: Message.Status,
    public val editDate: Instant?,
    public val expirationData: Message.ExpirationData?,
    public val expectsReadConfirmation: Boolean,
    public val content: MessageListContent,
    public val reactions: List<MessageListReaction>,
    public val deliveryFailures: MessageListDeliveryFailures,
    public val cursor: MessageTimelineCursor,
)

/** Display fields shared by sender, member-event and delivery-failure rendering. */
public data class MessageListUser(
    public val id: UserId,
    public val name: String?,
    public val accentId: Int,
    public val previewAvatar: QualifiedID?,
    public val availability: UserAvailabilityStatus,
    public val userType: UserTypeInfo,
    public val connectionState: ConnectionState,
    public val isSelf: Boolean,
    public val isDeleted: Boolean,
    public val isUnavailable: Boolean,
    public val isDefederated: Boolean,
    public val isUnderLegalHold: Boolean,
    public val guestExpiresAt: Instant?,
    public val botService: BotService?,
    public val isMissing: Boolean,
)

public sealed interface MessageListContent {
    public sealed interface Regular : MessageListContent
    public data object Deleted : MessageListContent
    public data class System(public val event: MessageContent.System, public val members: List<MessageListUser>) : MessageListContent
    public data class Text(public val text: MessageListText) : Regular
    public data class Composite(public val text: MessageListText?, public val buttons: List<Button>) : Regular

    /** A native poll with the latest vote of each user. [voters] resolves every voter in [poll] for display. */
    public data class Poll(public val poll: MessageContent.Poll, public val voters: List<MessageListUser>) : Regular
    public data class Multipart(public val text: MessageListText, public val attachments: List<MessageListAttachment>) : Regular
    public data class Asset(public val asset: MessageListAsset) : Regular
    public data class RestrictedAsset(public val name: String, public val mimeType: String, public val sizeInBytes: Long) : Regular
    public data class Location(
        public val latitude: Float,
        public val longitude: Float,
        public val name: String?,
        public val zoom: Int?,
    ) : Regular
    public data object Knock : Regular
    public data class Unknown(public val typeName: String?) : Regular
    public data class FailedDecryption(public val errorCode: Int?, public val isResolved: Boolean) : Regular
}

public data class MessageListText(
    public val body: String?,
    public val mentions: List<MessageMention>,
    public val linkPreviews: List<MessageListLinkPreview>,
    public val quotedMessageId: String?,
    /** Null with a non-null quotedMessageId means the original is unavailable locally. */
    public val quote: MessageContent.QuotedMessageDetails?,
)

/** Preview images can be resolved by conversation/message ID and urlOffset without exposing credentials. */
public data class MessageListLinkPreview(
    public val url: String,
    public val urlOffset: Int,
    public val permanentUrl: String,
    public val title: String,
    public val summary: String,
    public val imageLocalPath: String?,
    public val imageWidth: Int?,
    public val imageHeight: Int?,
    public val imageMimeType: String?,
)

public data class MessageListAsset(
    public val id: AssetId,
    public val name: String?,
    public val mimeType: String,
    public val sizeInBytes: Long,
    public val width: Int?,
    public val height: Int?,
    public val durationMs: Long?,
    public val hasCompleteRemoteData: Boolean,
)

public data class MessageListAttachment(
    public val id: String,
    public val versionId: String,
    public val mimeType: String,
    public val path: String?,
    public val sizeInBytes: Long?,
    public val localPath: String?,
    public val previewUrl: String?,
    public val width: Int?,
    public val height: Int?,
    public val durationMs: Long?,
    public val transferStatus: AssetTransferStatus,
    public val isEditSupported: Boolean,
)

public data class MessageListReaction(public val emoji: String, public val count: Int, public val isSelf: Boolean)

public data class MessageListDeliveryFailures(
    public val noClients: List<MessageListUser>,
    public val failedDelivery: List<MessageListUser>,
)
