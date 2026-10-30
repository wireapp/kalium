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
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.toMessageContent
import com.wire.kalium.logic.data.message.toModel
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.AvailabilityStatusMapperImpl
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.ConnectionStateMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapperImpl
import com.wire.kalium.persistence.TimelineRows
import com.wire.kalium.persistence.TimelineUsers
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessageTimelinePageEntity
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import kotlin.time.Duration.Companion.milliseconds

internal class MessageListItemMapper(private val selfUserId: UserId) {
    private val userTypeMapper = DomainUserTypeMapperImpl()
    private val availabilityMapper = AvailabilityStatusMapperImpl()
    private val connectionMapper = ConnectionStateMapperImpl()

    fun map(page: MessageTimelinePageEntity): MessageTimelinePage {
        val users = page.users.mapValues { (id, row) -> user(id, row) }.toMutableMap()
        val user: (UserIDEntity) -> MessageListUser = { id -> users.getOrPut(id) { user(id, null) } }
        val items = page.rows.map { row ->
            val failures = page.failures[row.id].orEmpty().associate { it.recipient_failure_type to it.recipient_failure_list }
            MessageListItem(
                id = row.id,
                conversationId = row.conversation_id.toModel(),
                date = row.creation_date,
                sender = user(row.sender_user_id),
                senderClientId = row.sender_client_id,
                status = row.status.toModel(page.readCounts[row.id] ?: 0),
                editDate = row.last_edit_date,
                expirationData = row.expire_after_millis?.let {
                    Message.ExpirationData(
                        it.milliseconds,
                        row.self_deletion_end_date?.let(Message.ExpirationData.SelfDeletionStatus::Started)
                            ?: Message.ExpirationData.SelfDeletionStatus.NotStarted,
                    )
                },
                expectsReadConfirmation = row.expects_read_confirmation,
                content = content(row, page, user),
                reactions = page.reactions[row.id].orEmpty().map {
                    MessageListReaction(it.emoji, it.reaction_count.toInt(), it.is_self == 1L)
                },
                deliveryFailures = MessageListDeliveryFailures(
                    noClients = failures[RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER].orEmpty().map(user),
                    failedDelivery = failures[RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED].orEmpty().map(user),
                ),
                cursor = MessageTimelineCursor(page.cursors.getValue(row.id)),
            )
        }
        return MessageTimelinePage(
            items,
            page.olderCursor?.let(::MessageTimelineCursor),
            page.newerCursor?.let(::MessageTimelineCursor),
            page.anchorMessageId,
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "DEPRECATION")
    private fun content(
        row: TimelineRows,
        page: MessageTimelinePageEntity,
        user: (UserIDEntity) -> MessageListUser,
    ): MessageListContent = if (row.visibility == MessageEntity.Visibility.DELETED) {
        MessageListContent.Deleted
    } else when (row.content_type) {
        MessageEntity.ContentType.TEXT -> MessageListContent.Text(text(row, page, user).copy(body = row.text_body.orEmpty()))
        MessageEntity.ContentType.COMPOSITE -> MessageListContent.Composite(
            row.text_body?.let { text(row, page, user) },
            page.buttons[row.id].orEmpty().map { Button(it.text, it.id, it.is_selected) },
        )
        MessageEntity.ContentType.MULTIPART -> MessageListContent.Multipart(
            text(row, page, user),
            page.attachments[row.id].orEmpty().filter { it.cellAsset == 1L }.map {
                MessageListAttachment(
                    id = it.asset_id,
                    versionId = it.assetVersionId,
                    mimeType = it.assetMimeType.orEmpty(),
                    path = it.assetPath,
                    sizeInBytes = it.size,
                    localPath = it.localPath?.takeIf(String::isNotEmpty),
                    previewUrl = it.previewUrl?.takeIf(String::isNotEmpty),
                    width = it.assetWidth?.toInt(),
                    height = it.assetHeight?.toInt(),
                    durationMs = it.assetDurationMs,
                    transferStatus = AssetTransferStatus.valueOf(it.assetTransferStatus),
                    isEditSupported = it.editSupported == 1L,
                )
            },
        )
        MessageEntity.ContentType.ASSET -> MessageListContent.Asset(
            MessageListAsset(
                AssetId(requireNotNull(row.asset_id), row.asset_domain.orEmpty()),
                row.asset_name,
                requireNotNull(row.asset_mime_type),
                requireNotNull(row.asset_size),
                row.asset_width,
                row.asset_height,
                row.asset_duration_ms,
                row.asset_complete,
                row.preview_asset_id?.let { AssetId(it, row.preview_asset_domain.orEmpty()) },
            )
        )
        MessageEntity.ContentType.RESTRICTED_ASSET -> MessageListContent.RestrictedAsset(
            requireNotNull(row.restricted_name),
            requireNotNull(row.restricted_mime_type),
            requireNotNull(row.restricted_size)
        )
        MessageEntity.ContentType.LOCATION -> MessageListContent.Location(
            requireNotNull(row.latitude),
            requireNotNull(row.longitude),
            row.location_name,
            row.location_zoom
        )
        MessageEntity.ContentType.POLL -> {
            val poll = page.polls.getValue(row.id)
            MessageListContent.Poll(
                poll = MessageContent.Poll(
                    question = poll.question,
                    options = poll.options.map { MessageContent.Poll.Option(it.id, it.text) },
                    allowMultipleAnswers = poll.allowMultipleAnswers,
                    votes = poll.votes.map { it.toModel() },
                ),
                voters = poll.votes.map { user(it.voterId) },
            )
        }
        MessageEntity.ContentType.KNOCK -> MessageListContent.Knock
        MessageEntity.ContentType.UNKNOWN -> MessageListContent.Unknown(row.unknown_type_name)
        MessageEntity.ContentType.FAILED_DECRYPTION -> MessageListContent.FailedDecryption(
            row.system_integer?.toInt(),
            row.system_boolean ?: false
        )
        else -> {
            val event = page.systemContents.getValue(row.id)
            val memberIds = when (event) {
                is MessageEntityContent.MemberChange -> event.memberUserIdList
                is MessageEntityContent.LegalHold -> event.memberUserIdList
                else -> emptyList()
            }
            val content = if (event is MessageEntityContent.TeamMemberRemoved) {
                MessageContent.TeamMemberRemoved(user(row.sender_user_id).name.orEmpty())
            } else {
                event.toMessageContent()
            }
            MessageListContent.System(content, memberIds.map(user))
        }
    }

    private fun text(row: TimelineRows, page: MessageTimelinePageEntity, user: (UserIDEntity) -> MessageListUser) = MessageListText(
        body = row.text_body,
        mentions = page.mentions[row.id].orEmpty().map {
            MessageMention(it.start, it.length, it.user_id.toModel(), it.user_id.toModel() == selfUserId)
        },
        linkPreviews = page.previews[row.id].orEmpty().map {
            MessageListLinkPreview(
                it.url, it.url_offset, it.permanent_url ?: it.url, it.title, it.summary,
                it.image_local_path?.takeIf(String::isNotEmpty), it.image_width, it.image_height, it.image_mime_type,
            )
        },
        quotedMessageId = row.quoted_message_id,
        quote = quote(row, user),
    )

    private fun quote(row: TimelineRows, user: (UserIDEntity) -> MessageListUser): MessageContent.QuotedMessageDetails? {
        val senderId = row.quote_sender_id ?: return null
        val sender = user(senderId)
        return MessageContent.QuotedMessageDetails(
            senderId = sender.id,
            senderName = sender.name,
            accentId = sender.accentId.takeUnless { sender.isMissing },
            isQuotingSelfUser = row.is_quoting_self == true,
            isVerified = row.is_quote_verified == true,
            messageId = requireNotNull(row.quoted_message_id),
            timeInstant = requireNotNull(row.quote_date),
            editInstant = row.quote_edit_date,
            quotedContent = quoteContent(row),
        )
    }

    private fun quoteContent(row: TimelineRows): MessageContent.QuotedMessageDetails.Content = when {
        row.is_quote_verified != true -> MessageContent.QuotedMessageDetails.Invalid
        row.quote_visibility != MessageEntity.Visibility.VISIBLE -> MessageContent.QuotedMessageDetails.Deleted
        row.quote_content_type == MessageEntity.ContentType.TEXT -> MessageContent.QuotedMessageDetails.Text(row.quote_text.orEmpty())
        row.quote_content_type == MessageEntity.ContentType.ASSET -> MessageContent.QuotedMessageDetails.Asset(
            row.quote_asset_name,
            requireNotNull(row.quote_asset_mime_type)
        )
        row.quote_content_type == MessageEntity.ContentType.LOCATION ->
            MessageContent.QuotedMessageDetails.Location(row.quote_location_name)
        row.quote_content_type == MessageEntity.ContentType.MULTIPART -> MessageContent.QuotedMessageDetails.Multipart(row.quote_text)
        row.quote_content_type == MessageEntity.ContentType.POLL -> MessageContent.QuotedMessageDetails.Poll(row.quote_text.orEmpty())
        else -> MessageContent.QuotedMessageDetails.Invalid
    }

    private fun user(id: UserIDEntity, row: TimelineUsers?): MessageListUser {
        val isSelf = id.toModel() == selfUserId
        return MessageListUser(
            id = id.toModel(),
            name = row?.name,
            accentId = row?.accent_id ?: 0,
            previewAvatar = row?.preview_asset_id?.toModel(),
            availability = availabilityMapper.fromDaoAvailabilityStatusToModel(row?.user_availability_status),
            userType = userTypeMapper.fromUserTypeEntity(row?.user_type),
            connectionState = connectionMapper.fromDaoConnectionStateToUser(row?.connection_status ?: ConnectionEntity.State.NOT_CONNECTED),
            isSelf = isSelf,
            isDeleted = !isSelf && row?.deleted == true,
            isUnavailable = !isSelf && row?.unavailable == true,
            isDefederated = row?.defederated == true,
            isUnderLegalHold = row?.legal_hold == true,
            guestExpiresAt = row?.expires_at,
            botService = row?.bot_service?.let { BotService(it.id, it.provider) },
            isMissing = row == null,
            textStatus = row?.text_status?.takeUnless { it.isBlank() },
        )
    }
}
