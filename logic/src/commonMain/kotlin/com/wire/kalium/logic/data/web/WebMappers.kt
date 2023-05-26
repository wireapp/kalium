/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

@file:Suppress("MagicNumber")

package com.wire.kalium.logic.data.web

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.DateTimeUtil
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("LongMethod", "ComplexMethod")
fun WebEventContent.toMigratedMessage(selfUserDomain: String): MigratedMessage? {
    return when (this) {
        is WebEventContent.Conversation.TextMessage -> {
            MigratedMessage(
                conversationId = qualifiedConversation,
                senderUserId = qualifiedFrom ?: QualifiedID(from, selfUserDomain),
                senderClientId = ClientId(fromClientId),
                timestamp = Instant.parse(time).toEpochMilliseconds(),
                content = "",
                unencryptedProto = ProtoContent.Readable(
                    id,
                    MessageContent.Text(
                        data.text,
                    ),
                    data.expectsReadConfirmation
                ),
                encryptedProto = null,
                null,
                null,
                null
            )
        }
        is WebEventContent.Conversation.AssetMessage -> {
            val mimeType = data.contentType ?: ""
            MigratedMessage(
                conversationId = qualifiedConversation,
                senderUserId = qualifiedFrom ?: QualifiedID(from, selfUserDomain),
                senderClientId = ClientId(fromClientId),
                timestamp = Instant.parse(time).toEpochMilliseconds(),
                content = "",
                unencryptedProto = ProtoContent.Readable(
                    id,
                    MessageContent.Asset(
                        AssetContent(
                            sizeInBytes = data.contentLength ?: 0,
                            name = data.info?.name,
                            mimeType = mimeType,
                            remoteData = AssetContent.RemoteData(
                                otrKey = data.otrKey?.toString()?.toByteArray() ?: ByteArray(0),
                                sha256 = data.sha256?.toString()?.toByteArray() ?: ByteArray(0),
                                assetId = data.key ?: "",
                                assetToken = data.token,
                                assetDomain = data.domain,
                                encryptionAlgorithm = null
                            ),
                            metadata = when {
                                mimeType.contains("image/") -> AssetContent.AssetMetadata.Image(
                                    width = data.info!!.width!!,
                                    height = data.info.height!!
                                )
                                mimeType.contains("video/") -> AssetContent.AssetMetadata.Video(
                                    width = null,
                                    height = null,
                                    durationMs = null
                                )
                                mimeType.contains("audio/") -> AssetContent.AssetMetadata.Audio(
                                    durationMs = data.meta?.duration,
                                    normalizedLoudness = data.meta?.loudness?.toString()?.toByteArray() ?: ByteArray(0)
                                )
                                else -> null
                            },
                            uploadStatus = Message.UploadStatus.NOT_UPLOADED,
                            downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
                        ),
                    ),
                    data.expectsReadConfirmation
                ),
                encryptedProto = null,
                null,
                null,
                null
            )
        }
        else -> null // TODO handle other cases
    }
}

private fun toQualifiedId(remoteId: String, domain: String?, selfUserId: UserId): QualifiedID =
    QualifiedID(remoteId, domain ?: selfUserId.domain)

fun WebConversationContent.toConversation(selfUserId: UserId): Conversation? {
    return mapConversationType(type)?.let {
        val lastEventTime: String =
            if (lastEventTimestamp == null || lastEventTimestamp == 0L) {
                "1970-01-01T00:00:00.000Z"
            } else {
                DateTimeUtil.fromEpochMillisToIsoDateTimeString(lastEventTimestamp)
            }

        val conversationLastReadTime = if (lastReadTimestamp == null || lastReadTimestamp == 0L) {
            "1970-01-01T00:00:00.000Z"
        } else {
            DateTimeUtil.fromEpochMillisToIsoDateTimeString(lastReadTimestamp)
        }

        Conversation(
            id = toQualifiedId(id, domain, selfUserId),
            name = name,
            type = it,
            teamId = teamId?.let { teamId -> TeamId(teamId) },
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = mapMutedStatus(mutedState),
            access = mapAccess(access),
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE
            ),
            removedBy = null,
            lastReadDate = conversationLastReadTime,
            lastModifiedDate = lastEventTime,
            lastNotificationDate = lastEventTime,
            creatorId = creator,
            receiptMode = fromScalaReceiptMode(receiptMode),
            messageTimer = messageTimer?.toDuration(DurationUnit.MILLISECONDS),
            userMessageTimer = null
        )
    }
}

private fun mapMutedStatus(status: Int?): MutedConversationStatus = when (status) {
    0 -> MutedConversationStatus.AllAllowed
    1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    2 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    3 -> MutedConversationStatus.AllMuted
    else -> MutedConversationStatus.AllAllowed
}

private fun mapAccess(accessList: List<String>?): List<Conversation.Access> {
    return accessList?.let { list ->
        list.map {
            when (it) {
                "private" -> Conversation.Access.PRIVATE
                "invite" -> Conversation.Access.INVITE
                "link" -> Conversation.Access.LINK
                "code" -> Conversation.Access.CODE
                else -> Conversation.Access.PRIVATE
            }
        }
    } ?: listOf(Conversation.Access.PRIVATE)
}

private fun fromScalaReceiptMode(receiptMode: Int?): Conversation.ReceiptMode = receiptMode?.let {
    if (receiptMode > 0) {
        Conversation.ReceiptMode.ENABLED
    } else {
        Conversation.ReceiptMode.DISABLED
    }
} ?: Conversation.ReceiptMode.DISABLED

private fun mapConversationType(type: Int): Conversation.Type? = when (type) {
    0 -> Conversation.Type.GROUP
    1 -> Conversation.Type.SELF
    2 -> Conversation.Type.ONE_ON_ONE
    3, 4 -> Conversation.Type.CONNECTION_PENDING
    else -> null
}
