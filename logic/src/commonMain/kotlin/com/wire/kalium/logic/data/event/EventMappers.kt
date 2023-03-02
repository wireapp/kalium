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
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.web.WebContent
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Instant

fun WebContent.toMigratedMessage(selfUserDomain: String): MigratedMessage? {
    return when (this) {
        is WebContent.Conversation.TextMessage -> {
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
        is WebContent.Conversation.AssetMessage ->
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
                            sizeInBytes = data.contentLength?.toLong() ?: 0,
                            name = null,
                            mimeType = data.contentType ?: "",
                            remoteData = AssetContent.RemoteData(
                                otrKey = data.otrKey?.toString()?.toByteArray() ?: ByteArray(0),
                                sha256 = data.sha256?.toString()?.toByteArray() ?: ByteArray(0),
                                assetId = data.key ?: "",
                                assetToken = data.token,
                                assetDomain = data.domain,
                                encryptionAlgorithm = null
                            ),
                            uploadStatus = Message.UploadStatus.UPLOADED,
                            downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
                        )
                    ),
                    data.expectsReadConfirmation
                ),
                encryptedProto = null,
                null,
                null,
                null
            )
        else -> null // TODO handle other cases
    }
}
