package com.wire.kalium.util.long

fun Long.toByteArray(): ByteArray {
    val result = ByteArray(Long.SIZE_BYTES)

    var longConvertedToByteArray = this

    for (i in Long.SIZE_BYTES - 1 downTo 0) {
        result[i] = (longConvertedToByteArray and 0xFFL).toByte()
        longConvertedToByteArray = longConvertedToByteArray shr Long.SIZE_BYTES
    }

    return result.copyOfRange(result.indexOfLast { it == 0x00.toByte() } + 1, result.size)
}
