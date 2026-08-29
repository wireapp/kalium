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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.attachment.toModel
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.string.toHexString
import kotlinx.datetime.Instant

@InternalKaliumApi
public fun interface IncomingQuotedMessageVerifier {
    public suspend operator fun invoke(
        conversationId: ConversationId,
        quotedReference: MessageContent.QuoteReference,
    ): MessageContent.QuoteReference
}

@InternalKaliumApi
public class IncomingQuotedMessageVerifierImpl public constructor(
    private val messageDAO: MessageDAO,
    private val messageContentEncoder: MessageContentEncoder,
) : IncomingQuotedMessageVerifier {

    private val logger by lazy { kaliumLogger.withFeatureId(ApplicationFlow.EVENT_RECEIVER) }
    private val storedMessageMapper = IncomingQuotedMessageHashInputMapper()

    override suspend fun invoke(
        conversationId: ConversationId,
        quotedReference: MessageContent.QuoteReference,
    ): MessageContent.QuoteReference {
        val quotedMessageSha256 = quotedReference.quotedMessageSha256 ?: run {
            logger.i("Quote message received with null hash. Marking as unverified.")
            return quotedReference.copy(isVerified = false)
        }

        val originalHash = wrapStorageRequest {
            messageDAO.getMessageById(quotedReference.quotedMessageId, conversationId.toDao())
        }.map(storedMessageMapper::fromEntity)
            .map { storedMessage ->
                messageContentEncoder.encodeMessageContent(storedMessage.date, storedMessage.content)
            }.getOrElse(null)

        return if (quotedMessageSha256.contentEquals(originalHash?.sha256Digest)) {
            quotedReference.copy(isVerified = true)
        } else {
            logger.d("Expected hash = ${originalHash?.sha256Digest?.toHexString()}")
            logger.d("Received hash = ${quotedMessageSha256.toHexString()}")
            logger.i("Quote message received but original doesn't match or wasn't found. Marking as unverified.")
            quotedReference.copy(isVerified = false)
        }
    }
}

internal data class IncomingQuotedMessageHashInput(
    val date: Instant,
    val content: MessageContent,
)

internal class IncomingQuotedMessageHashInputMapper {
    fun fromEntity(message: MessageEntity): IncomingQuotedMessageHashInput = IncomingQuotedMessageHashInput(
        date = message.date,
        content = when (message) {
            is MessageEntity.Regular -> message.content.toHashInput()
            is MessageEntity.System -> MessageContent.Unknown()
        },
    )

    private fun MessageEntityContent.Regular.toHashInput(): MessageContent = when (this) {
        is MessageEntityContent.Text -> MessageContent.Text(messageBody)
        is MessageEntityContent.Asset -> MessageContent.Asset(
            AssetContent(
                sizeInBytes = 0,
                mimeType = "",
                remoteData = AssetContent.RemoteData(
                    otrKey = byteArrayOf(),
                    sha256 = byteArrayOf(),
                    assetId = assetId,
                    assetToken = null,
                    assetDomain = null,
                    encryptionAlgorithm = null,
                ),
            )
        )

        is MessageEntityContent.Location -> MessageContent.Location(
            latitude = latitude,
            longitude = longitude,
            name = name,
            zoom = zoom,
        )

        is MessageEntityContent.Multipart -> MessageContent.Multipart(
            value = messageBody,
            attachments = attachments
                .sortedBy { it.assetIndex }
                .mapNotNull { it.toModel() },
        )

        else -> MessageContent.Unknown()
    }
}
