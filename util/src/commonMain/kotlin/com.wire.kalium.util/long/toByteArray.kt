package com.wire.kalium.util.long

@Suppress("MagicNumber")
fun Long.toByteArray(): ByteArray {
    val result = ByteArray(Long.SIZE_BYTES)

    var longConvertedToByteArray = this

    for (i in Long.SIZE_BYTES - 1 downTo 0) {
        result[i] = (longConvertedToByteArray and 0xFFL).toByte()
        longConvertedToByteArray = longConvertedToByteArray shr Long.SIZE_BYTES
    }

    return result
}
