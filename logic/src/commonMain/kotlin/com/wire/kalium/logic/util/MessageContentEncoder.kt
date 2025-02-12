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

package com.wire.kalium.logic.util

import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.long.toByteArray
import com.wire.kalium.util.string.toHexString
import com.wire.kalium.util.string.toUTF16BEByteArray
import kotlinx.datetime.Instant
import kotlin.math.roundToLong

class MessageContentEncoder {
    fun encodeMessageContent(messageInstant: Instant, messageContent: MessageContent): EncodedMessageContent? {
        return when (messageContent) {
            is MessageContent.Asset ->
                encodeMessageAsset(
                    messageTimeStampInMillis = messageInstant.toEpochMilliseconds(),
                    assetId = messageContent.value.remoteData.assetId
                )

            is MessageContent.Text ->
                encodeMessageTextBody(
                    messageTimeStampInMillis = messageInstant.toEpochMilliseconds(),
                    messageTextBody = messageContent.value
                )

            is MessageContent.Location -> with(messageContent) {
                encodeLocationCoordinates(
                    latitude = latitude,
                    longitude = longitude,
                    messageTimeStampInMillis = messageInstant.toEpochMilliseconds()
                )
            }

            else -> {
                kaliumLogger.w("Attempting to encode message with unsupported content type")
                null
            }
        }
    }

    private fun encodeMessageAsset(
        messageTimeStampInMillis: Long,
        assetId: String
    ): EncodedMessageContent = wrapIntoResult(
        messageTimeStampByteArray = encodeMessageTimeStampInMillis(messageTimeStampInMillis = messageTimeStampInMillis),
        messageTextBodyUTF16BE = assetId.toUTF16BEByteArray()
    )

    private fun encodeMessageTextBody(
        messageTimeStampInMillis: Long,
        messageTextBody: String
    ): EncodedMessageContent = wrapIntoResult(
        messageTimeStampByteArray = encodeMessageTimeStampInMillis(messageTimeStampInMillis = messageTimeStampInMillis),
        messageTextBodyUTF16BE = messageTextBody.toUTF16BEByteArray()
    )

    private fun encodeMessageTimeStampInMillis(messageTimeStampInMillis: Long): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC

        return messageTimeStampInSec.toByteArray()
    }

    private fun encodeLocationCoordinates(latitude: Float, longitude: Float, messageTimeStampInMillis: Long): EncodedMessageContent {
        val latitudeBEBytes = (latitude * COORDINATES_ROUNDING).roundToLong().toByteArray()
        val longitudeBEBytes = (longitude * COORDINATES_ROUNDING).roundToLong().toByteArray()

        return EncodedMessageContent(latitudeBEBytes + longitudeBEBytes + encodeMessageTimeStampInMillis(messageTimeStampInMillis))
    }

    private fun wrapIntoResult(messageTimeStampByteArray: ByteArray, messageTextBodyUTF16BE: ByteArray): EncodedMessageContent {
        return EncodedMessageContent(
            byteArray = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + messageTextBodyUTF16BE + messageTimeStampByteArray
        )
    }

    private companion object {
        const val MILLIS_IN_SEC = 1000
        const val COORDINATES_ROUNDING = 1000
    }
}

class EncodedMessageContent(val byteArray: ByteArray) {
    val asHexString = byteArray.toHexString()
    val sha256Digest = calcSHA256(byteArray)
}
