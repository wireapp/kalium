package com.wire.kalium.util.long

fun Long.toByteArray(): ByteArray {
    var longConvertedToByteArray = this

    val result = ByteArray(Long.SIZE_BYTES)

    for (i in Long.SIZE_BYTES - 1 downTo 0) {
        result[i] = (longConvertedToByteArray and 0xFFL).toByte()
        longConvertedToByteArray = longConvertedToByteArray shr Long.SIZE_BYTES
    }

    val lastEncounteredZeroIndex = result.indexOfLast { it == 0x00.toByte() }

    return result.copyOfRange(lastEncounteredZeroIndex + 1, result.size)
}
