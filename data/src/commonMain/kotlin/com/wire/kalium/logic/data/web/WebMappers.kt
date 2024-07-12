/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.time.UNIX_FIRST_DATE
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
                senderClientId = ClientId(fromClientId.orEmpty()),
                timestamp = Instant.parse(time).toEpochMilliseconds(),
                content = "",
                unencryptedProto = ProtoContent.Readable(
                    id,
                    MessageContent.Text(
                        data.text,
                    ),
                    data.expectsReadConfirmation ?: false,
                    legalHoldStatus = if (data.legalHoldStatus == 2) Conversation.LegalHoldStatus.ENABLED
                    else Conversation.LegalHoldStatus.DISABLED
                ),
                encryptedProto = null,
                null,
                null,
                null
            )
        }

        is WebEventContent.Conversation.AssetMessage -> {
            val otrKey = data.otrKey?.values?.map { it.toByte() }?.toByteArray()
            val sha256 = data.sha256?.values?.map { it.toByte() }?.toByteArray()
            val mimeType = data.contentType ?: ""
            if (otrKey != null && sha256 != null) {
                MigratedMessage(
                    conversationId = qualifiedConversation,
                    senderUserId = qualifiedFrom ?: QualifiedID(from, selfUserDomain),
                    senderClientId = ClientId(fromClientId.orEmpty()),
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
                                    otrKey = otrKey,
                                    sha256 = sha256,
                                    assetId = data.key ?: "",
                                    assetToken = data.token,
                                    assetDomain = data.domain,
                                    encryptionAlgorithm = null
                                ),
                                metadata = when {
                                    mimeType.contains("image/") && data.info?.width != null -> AssetContent.AssetMetadata.Image(
                                        width = data.info.width.replace("px", "").toInt(),
                                        height = data.info.height!!.toInt()
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
                            ),
                        ),
                        data.expectsReadConfirmation,
                        legalHoldStatus = if (data.legalHoldStatus == 2) Conversation.LegalHoldStatus.ENABLED
                        else Conversation.LegalHoldStatus.DISABLED
                    ),
                    encryptedProto = null,
                    null,
                    null,
                    null
                )
            } else {
                null
            }
        }

        else -> null // TODO handle other cases
    }
}

private fun toQualifiedId(remoteId: String, domain: String?, selfUserId: UserId): QualifiedID =
    QualifiedID(remoteId, domain ?: selfUserId.domain)

fun WebConversationContent.toConversation(selfUserId: UserId): Conversation? {
    return mapConversationType(type)?.let {
        val lastEventTime = if (lastEventTimestamp == null || lastEventTimestamp == 0L) {
            Instant.UNIX_FIRST_DATE
        } else {
            Instant.fromEpochMilliseconds(lastEventTimestamp)
        }

        val conversationLastReadTime = if (lastReadTimestamp == null || lastReadTimestamp == 0L) {
            Instant.UNIX_FIRST_DATE
        } else {
            Instant.fromEpochMilliseconds(lastReadTimestamp)
        }
        val conversationArchivedTimestamp: Instant? = archivedTimestamp?.let { timestamp ->
            Instant.fromEpochMilliseconds(timestamp)
        }

        Conversation(
            id = toQualifiedId(id, domain, selfUserId),
            name = name,
            type = it,
            teamId = teamId?.let { teamId -> TeamId(teamId) },
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = mapMutedStatus(mutedState),
            access = access.mapAccess(),
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
            userMessageTimer = null,
            archived = archivedState ?: false,
            archivedDateTime = conversationArchivedTimestamp,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = if (legalHoldStatus == 2) Conversation.LegalHoldStatus.ENABLED
            else Conversation.LegalHoldStatus.DISABLED
        )
    }
}

fun Conversation.toWebConversationContent(): WebConversationContent {
    val lastEventTime = if (lastModifiedDate == null || lastModifiedDate == Instant.UNIX_FIRST_DATE) {
        0L
    } else {
        lastModifiedDate.toEpochMilliseconds()
    }
    val lastReadTime = if (lastReadDate == Instant.UNIX_FIRST_DATE) 0L else lastReadDate.toEpochMilliseconds()
    val archivedTime = archivedDateTime?.toEpochMilliseconds()

    return WebConversationContent(
        id = id.value,
        domain = id.domain,
        type = mapConversationTypeToInt(type),
        name = name,
        mutedState = mapMutedStatusToInt(mutedStatus),
        accessRole = accessRole.map { it.name.lowercase() },
        access = access.mapAccessToString(),
        archivedState = archived,
        archivedTimestamp = archivedTime,
        clearedTimestamp = null, // Not provided in Conversation
        creator = creatorId,
        epoch = null, // Not provided in Conversation
        receiptMode = mapReceiptModeToInt(receiptMode),
        isGuest = accessRole.contains(Conversation.AccessRole.GUEST),
        isManaged = accessRole.contains(Conversation.AccessRole.TEAM_MEMBER), // Example condition
        lastEventTimestamp = lastEventTime,
        lastReadTimestamp = lastReadTime,
        lastServerTimestamp = null, // Not provided in Conversation
        legalHoldStatus = when (legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED -> 2
            else -> 0
        },
        mutedTimestamp = null, // Not provided in Conversation
        others = null, // Not provided in Conversation
        protocol = protocol.name(),
        status = 0, // Not provided in Conversation
        teamId = teamId?.value,
        messageTimer = messageTimer?.toLong(DurationUnit.MILLISECONDS)
    )
}

private fun mapMutedStatus(status: Int?): MutedConversationStatus = when (status) {
    0 -> MutedConversationStatus.AllAllowed
    1 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    2 -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
    3 -> MutedConversationStatus.AllMuted
    else -> MutedConversationStatus.AllAllowed
}

fun mapMutedStatusToInt(status: MutedConversationStatus): Int = when (status) {
    MutedConversationStatus.AllAllowed -> 0
    MutedConversationStatus.OnlyMentionsAndRepliesAllowed -> 1
    MutedConversationStatus.AllMuted -> 3
}

private fun List<String>?.mapAccess(): List<Conversation.Access> {
    return this?.let { list ->
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

fun List<Conversation.Access>?.mapAccessToString(): List<String>? {
    return this?.map {
        when (it) {
            Conversation.Access.PRIVATE -> "private"
            Conversation.Access.INVITE -> "invite"
            Conversation.Access.LINK -> "link"
            Conversation.Access.CODE -> "code"
            else -> "private"
        }
    }
}

private fun fromScalaReceiptMode(receiptMode: Int?): Conversation.ReceiptMode = receiptMode?.let {
    if (receiptMode > 0) {
        Conversation.ReceiptMode.ENABLED
    } else {
        Conversation.ReceiptMode.DISABLED
    }
} ?: Conversation.ReceiptMode.DISABLED

fun mapReceiptModeToInt(receiptMode: Conversation.ReceiptMode): Int = when (receiptMode) {
    Conversation.ReceiptMode.ENABLED -> 1
    Conversation.ReceiptMode.DISABLED -> 0
}

private fun mapConversationType(type: Int): Conversation.Type? = when (type) {
    0 -> Conversation.Type.GROUP
    1 -> Conversation.Type.SELF
    2 -> Conversation.Type.ONE_ON_ONE
    3, 4 -> Conversation.Type.CONNECTION_PENDING
    else -> null
}

fun mapConversationTypeToInt(type: Conversation.Type): Int = when (type) {
    Conversation.Type.GROUP -> 0
    Conversation.Type.SELF -> 1
    Conversation.Type.ONE_ON_ONE -> 2
    Conversation.Type.CONNECTION_PENDING -> 3
}
