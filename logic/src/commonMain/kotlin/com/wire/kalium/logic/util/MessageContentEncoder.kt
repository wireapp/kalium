package com.wire.kalium.logic.util

import com.wire.kalium.util.long.toByteArray
import com.wire.kalium.util.string.toUTF16BEByteArray

class MessageContentEncoder {

    fun encryptMessageAsset(
        messageTimeStampInMillis: Long,
        assetId: String
    ): ByteArray = wrapIntoByteResult(
        messageTimeStampByteArray = encodeMessageTimeStampInMillis(messageTimeStampInMillis = messageTimeStampInMillis),
        messageTextBodyUTF16BE = assetId.toUTF16BEByteArray()
    )

    fun encryptMessageTextBody(
        messageTimeStampInMillis: Long,
        messageTextBody: String
    ): ByteArray = wrapIntoByteResult(
        messageTimeStampByteArray = encodeMessageTimeStampInMillis(messageTimeStampInMillis = messageTimeStampInMillis),
        messageTextBodyUTF16BE = messageTextBody.toUTF16BEByteArray()
    )

    private fun encodeMessageTimeStampInMillis(messageTimeStampInMillis: Long): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC

        return messageTimeStampInSec.toByteArray()
    }

    private fun wrapIntoByteResult(messageTimeStampByteArray: ByteArray, messageTextBodyUTF16BE: ByteArray): ByteArray {
        return byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + messageTextBodyUTF16BE + messageTimeStampByteArray
    }

    private companion object {
        const val MILLIS_IN_SEC = 1000
    }
}
