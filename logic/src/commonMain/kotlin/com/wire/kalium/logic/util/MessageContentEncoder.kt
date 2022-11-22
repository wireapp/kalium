package com.wire.kalium.logic.util

import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.util.long.toByteArray
import com.wire.kalium.util.string.toHexString
import com.wire.kalium.util.string.toUTF16BEByteArray

class MessageContentEncoder {

    fun encodeMessageAsset(
        messageTimeStampInMillis: Long,
        assetId: String
    ): EncodedMessageContent = wrapIntoResult(
        messageTimeStampByteArray = encodeMessageTimeStampInMillis(messageTimeStampInMillis = messageTimeStampInMillis),
        messageTextBodyUTF16BE = assetId.toUTF16BEByteArray()
    )

    fun encodeMessageTextBody(
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
        return EncodedMessageContent(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + messageTextBodyUTF16BE + messageTimeStampByteArray)
    }

    private companion object {
        const val MILLIS_IN_SEC = 1000
    }
}

class EncodedMessageContent(byteArray: ByteArray) {
    val asByteArray = byteArray
    val asHexString = byteArray.toHexString()
    val asSHA256 = calcSHA256(byteArray)
}
