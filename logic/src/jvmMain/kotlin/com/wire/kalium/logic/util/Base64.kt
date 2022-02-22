package com.wire.kalium.logic.util

import java.util.Base64

actual object Base64 {
    actual fun encodeToBase64(originalString: ByteArray): ByteArray = Base64.getEncoder().encode(originalString)

    actual fun decodeFromBase64(encoded: ByteArray): ByteArray = Base64.getDecoder().decode(encoded)
}
