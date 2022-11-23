package com.wire.kalium.logic.util

import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.long.toByteArray
import com.wire.kalium.util.string.toHexString
import com.wire.kalium.util.string.toUTF16BEByteArray

class MessageContentEncoder {
    fun encodeMessageContent(messageDate: String, messageContent: MessageContent): EncodedMessageContent? {
        return when (messageContent) {
            is MessageContent.Asset ->
                encodeMessageAsset(
                    messageTimeStampInMillis = messageDate.toTimeInMillis(),
                    assetId = messageContent.value.remoteData.assetId
                )

            is MessageContent.Text ->
                encodeMessageTextBody(
                    messageTimeStampInMillis = messageDate.toTimeInMillis(),
                    messageTextBody = messageContent.value
                )

            else -> {
                kaliumLogger.w("Unknown message type being replied to. Marking quote as invalid")
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

    private fun wrapIntoResult(messageTimeStampByteArray: ByteArray, messageTextBodyUTF16BE: ByteArray): EncodedMessageContent {
        return EncodedMessageContent(
            byteArray = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + messageTextBodyUTF16BE + messageTimeStampByteArray
        )
    }

    private companion object {
        const val MILLIS_IN_SEC = 1000
    }
}

class EncodedMessageContent(val byteArray: ByteArray) {
    val asHexString = byteArray.toHexString()
    val sha256Digest = calcSHA256(byteArray)
}
